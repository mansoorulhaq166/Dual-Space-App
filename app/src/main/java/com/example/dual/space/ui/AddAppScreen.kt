package com.example.dual.space.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dual.space.engine.AppInfo
import com.example.dual.space.ui.theme.Aqua
import com.example.dual.space.ui.theme.ElectricViolet
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppScreen(
    apps: List<AppInfo>,
    loading: Boolean,
    cloneCounts: Map<String, Int>,
    onClone: (List<AppInfo>) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(query) {
        delay(120)
        debouncedQuery = query.trim()
    }

    val sorted = remember(apps) {
        apps.sortedWith(
            compareBy<AppInfo> { POPULAR_PACKAGES.indexOf(it.packageName).let { index -> if (index < 0) Int.MAX_VALUE else index } }
                .thenBy { it.label.lowercase() },
        )
    }
    val visible = remember(sorted, debouncedQuery) {
        if (debouncedQuery.isBlank()) sorted
        else sorted.filter { it.label.contains(debouncedQuery, true) || it.packageName.contains(debouncedQuery, true) }
    }
    val selectedApps = remember(apps, selected) { apps.filter { it.packageName in selected } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Clone apps", style = MaterialTheme.typography.titleLarge)
                        Text("Select one or more installed apps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
        floatingActionButton = {
            if (selectedApps.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onClone(selectedApps) },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add ${selectedApps.size}") },
                    containerColor = ElectricViolet,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search installed apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Aqua)
                }
                visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matching apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            if (debouncedQuery.isBlank()) "Recommended first" else "Results",
                            style = MaterialTheme.typography.labelLarge,
                            color = Aqua,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    items(visible, key = { it.packageName }) { app ->
                        AppPickerRow(
                            app = app,
                            cloneCount = cloneCounts[app.packageName] ?: 0,
                            selected = app.packageName in selected,
                            onToggle = {
                                selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(app: AppInfo, cloneCount: Int, selected: Boolean, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) ElectricViolet.copy(alpha = .18f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, ElectricViolet) else null,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconImage(app.packageName, app.label, Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (cloneCount) {
                        0 -> "Ready to clone"
                        1 -> "1 existing instance"
                        else -> "$cloneCount existing instances"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (selected) ElectricViolet else MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private val POPULAR_PACKAGES = listOf(
    "com.whatsapp",
    "com.whatsapp.w4b",
    "com.google.android.youtube",
    "com.reddit.frontpage",
    "com.instagram.android",
    "com.facebook.katana",
    "com.facebook.orca",
    "org.telegram.messenger",
    "com.snapchat.android",
    "com.zhiliaoapp.musically",
    "com.twitter.android",
    "com.x.android",
    "com.discord",
    "com.spotify.music",
)
