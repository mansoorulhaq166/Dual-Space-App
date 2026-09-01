package com.example.dual.space.ui

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders an app's launcher icon, loaded lazily by package name.
 *
 * Icons are decoded off the main thread and cached in-process, so lists (especially the
 * full installed-apps picker) render instantly and icons stream in per visible item instead
 * of blocking composition. Previously every icon was a Drawable loaded eagerly in the data
 * layer and converted to a bitmap on the main thread — the main source of UI hangs.
 */
@Composable
fun AppIconImage(packageName: String, label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf(IconCache.cached(packageName)) }

    LaunchedEffect(packageName) {
        if (bitmap == null) {
            bitmap = withContext(Dispatchers.IO) { IconCache.load(context, packageName) }
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = label,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        // Stable branded placeholder while the icon streams in from PackageManager.
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Memory-bounded in-memory cache of decoded launcher icons, keyed by package name. Capped by total
 * decoded bytes (LRU eviction) so browsing the full installed-apps picker can't grow it without limit.
 */
private object IconCache {
    private const val SIZE_PX = 144
    private const val MAX_BYTES = 6 * 1024 * 1024 // ~6 MB of decoded icons before LRU eviction kicks in

    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun cached(packageName: String): ImageBitmap? = cache.get(packageName)

    fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bmp = drawable.toBitmap(width = SIZE_PX, height = SIZE_PX).asImageBitmap()
            cache.put(packageName, bmp)
            bmp
        }.getOrNull()
    }
}
