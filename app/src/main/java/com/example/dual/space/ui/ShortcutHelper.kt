package com.example.dual.space.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.dual.space.R
import com.example.dual.space.CloneLaunchActivity
import com.example.dual.space.MainActivity
import com.example.dual.space.engine.AppInfo

/** Pins a shortcut through MainActivity so the Dual Space task is always the Back destination. */
object ShortcutHelper {

    fun pin(context: Context, app: AppInfo): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            action = CloneLaunchActivity.ACTION_LAUNCH_CLONE
            putExtra(CloneLaunchActivity.EXTRA_PACKAGE, app.packageName)
            putExtra(CloneLaunchActivity.EXTRA_USER_ID, app.userId)
            putExtra(CloneLaunchActivity.EXTRA_LABEL, app.displayLabel)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        // Load the real app icon on demand (icons aren't held in AppInfo anymore); fall back to ours.
        val appIcon: Bitmap? = runCatching {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap(192, 192)
        }.getOrNull()
        val icon: IconCompat = if (appIcon == null) {
            IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        } else {
            IconCompat.createWithBitmap(appIcon)
        }

        // Unique per clone instance so a 2nd copy gets its own shortcut.
        val shortcut = ShortcutInfoCompat.Builder(context, "clone_${app.cloneKey}")
            .setShortLabel(app.displayLabel)
            .setIcon(icon)
            .setIntent(intent)
            .build()

        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
