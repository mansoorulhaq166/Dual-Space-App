package com.example.dual.space.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dual.space.BuildConfig
import com.example.dual.space.engine.AppInfo
import com.example.dual.space.ui.theme.Aqua
import com.example.dual.space.ui.theme.ElectricViolet
import com.example.dual.space.ui.theme.Gold
import com.example.dual.space.ui.theme.PanelRaised
import com.example.dual.space.ui.theme.Success

private enum class DestructiveAction { CLEAR, REMOVE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    clones: List<AppInfo>,
    loading: Boolean,
    hiddenKeys: Set<String>,
    favoriteKeys: Set<String>,
    recentKeys: List<String>,
    lowMemoryMode: Boolean,
    onAddClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onRestart: (AppInfo) -> Unit,
    onCreateShortcut: (AppInfo) -> Unit,
    onCloneAgain: (AppInfo) -> Unit,
    onRename: (AppInfo, String?) -> Unit,
    onFavorite: (AppInfo, Boolean) -> Unit,
    onHide: (AppInfo, Boolean) -> Unit,
    onClearData: (AppInfo) -> Unit,
    onRemove: (AppInfo) -> Unit,
    onOptimize: () -> Unit,
    onShareLogs: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }

    val visibleClones: List<AppInfo> = remember(
        clones,
        hiddenKeys,
        favoriteKeys,
        recentKeys,
        query,
    ) {
        val recentRank: Map<String, Int> = recentKeys.withIndex().associate { indexed ->
            indexed.value to indexed.index
        }
        clones.asSequence()
            .filterNot { app: AppInfo -> app.cloneKey in hiddenKeys }
            .filter { app: AppInfo ->
                query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<AppInfo> { app: AppInfo -> app.cloneKey in favoriteKeys }
                    .thenBy { app: AppInfo -> recentRank[app.cloneKey] ?: Int.MAX_VALUE }
                    .thenBy { app: AppInfo -> app.label.lowercase() },
            )
            .toList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text(
                            text = "Dual Space",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (lowMemoryMode) "Low-memory mode" else "Your private app spaces",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (lowMemoryMode) Aqua else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) query = ""
                        },
                    ) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (searchVisible) "Close search" else "Search",
                        )
                    }
                    IconButton(onClick = onShareLogs) {
                        Icon(Icons.Rounded.BugReport, contentDescription = "Diagnostics")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onAddClick,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    label = { Text("Add") },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onPrivacyClick,
                    icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    label = { Text("Private") },
                )
            }
        },
    ) { scaffoldPadding: PaddingValues ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Aqua)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 78.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    top = 8.dp,
                    end = 14.dp,
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CompactOverview(
                        cloneCount = visibleClones.size,
                        hiddenCount = hiddenKeys.size,
                        lowMemoryMode = lowMemoryMode,
                        onOptimize = onOptimize,
                    )
                }

                if (searchVisible) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { value: String -> query = value },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            placeholder = { Text("Search apps or spaces") },
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Your spaces",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${visibleClones.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (visibleClones.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyHome(
                            hasHidden = hiddenKeys.isNotEmpty(),
                            onAdd = onAddClick,
                            onPrivacy = onPrivacyClick,
                        )
                    }
                } else {
                    items(
                        items = visibleClones,
                        key = { app: AppInfo -> app.cloneKey },
                    ) { app: AppInfo ->
                        val packageCloneCount =
                            clones.count { it.packageName == app.packageName }
                        CloneTile(
                            app = app,
                            favorite = app.cloneKey in favoriteKeys,
                            canCloneAgain =
                                packageCloneCount < BuildConfig.MAX_CLONES_PER_PACKAGE,
                            onLaunch = { onLaunch(app) },
                            onRestart = { onRestart(app) },
                            onShortcut = { onCreateShortcut(app) },
                            onCloneAgain = { onCloneAgain(app) },
                            onRename = { alias: String? -> onRename(app, alias) },
                            onFavorite = { enabled: Boolean -> onFavorite(app, enabled) },
                            onHide = { onHide(app, true) },
                            onClearData = { onClearData(app) },
                            onRemove = { onRemove(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactOverview(
    cloneCount: Int,
    hiddenCount: Int,
    lowMemoryMode: Boolean,
    onOptimize: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PanelRaised),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ElectricViolet.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Aqua)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$cloneCount active space${if (cloneCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        lowMemoryMode -> "Optimized for this device"
                        hiddenCount > 0 -> "$hiddenCount hidden in Private Space"
                        else -> "Tap an icon to switch accounts"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onOptimize) {
                Icon(
                    Icons.Rounded.CleaningServices,
                    contentDescription = "Optimize memory",
                    tint = Success,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloneTile(
    app: AppInfo,
    favorite: Boolean,
    canCloneAgain: Boolean,
    onLaunch: () -> Unit,
    onRestart: () -> Unit,
    onShortcut: () -> Unit,
    onCloneAgain: () -> Unit,
    onRename: (String?) -> Unit,
    onFavorite: (Boolean) -> Unit,
    onHide: () -> Unit,
    onClearData: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<DestructiveAction?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameText by remember(app.cloneKey, app.label) { mutableStateOf(app.label) }

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = onLaunch,
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                AppIconImage(
                    packageName = app.packageName,
                    label = app.label,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                if (app.userId > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(ElectricViolet),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${app.userId + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                if (favorite) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Favorite",
                        tint = Gold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.background, CircleShape)
                            .padding(2.dp),
                    )
                }
            }
            Text(
                text = app.label,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            MenuItem("Open", Icons.Rounded.PlayArrow) { menuOpen = false; onLaunch() }
            MenuItem("Restart", Icons.Rounded.Refresh) { menuOpen = false; onRestart() }
            MenuItem("Create shortcut", Icons.Rounded.Shortcut) { menuOpen = false; onShortcut() }
            if (canCloneAgain) {
                MenuItem("Clone again", Icons.Rounded.Add) { menuOpen = false; onCloneAgain() }
            }
            MenuItem("Rename", Icons.Rounded.Edit) { menuOpen = false; renameOpen = true }
            MenuItem(
                if (favorite) "Remove favorite" else "Add favorite",
                if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            ) {
                menuOpen = false
                onFavorite(!favorite)
            }
            MenuItem("Move to Private Space", Icons.Rounded.VisibilityOff) {
                menuOpen = false
                onHide()
            }
            MenuItem("Clear clone data", Icons.Rounded.CleaningServices) {
                menuOpen = false
                pending = DestructiveAction.CLEAR
            }
            MenuItem("Remove clone", Icons.Rounded.DeleteOutline) {
                menuOpen = false
                pending = DestructiveAction.REMOVE
            }
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename space") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { value: String -> renameText = value.take(28) },
                    singleLine = true,
                    label = { Text("Display name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameOpen = false
                        onRename(renameText)
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            renameOpen = false
                            onRename(null)
                        },
                    ) { Text("Reset") }
                    TextButton(onClick = { renameOpen = false }) { Text("Cancel") }
                }
            },
        )
    }

    pending?.let { action: DestructiveAction ->
        val clear: Boolean = action == DestructiveAction.CLEAR
        AlertDialog(
            onDismissRequest = { pending = null },
            title = {
                Text(if (clear) "Reset ${app.displayLabel}?" else "Remove ${app.displayLabel}?")
            },
            text = {
                Text(
                    if (clear) {
                        "This deletes this clone's local data and signed-in state."
                    } else {
                        "This permanently removes this cloned instance and its isolated data."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        if (clear) onClearData() else onRemove()
                    },
                ) {
                    Text(
                        text = if (clear) "Reset" else "Remove",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = action,
    )
}

@Composable
private fun EmptyHome(hasHidden: Boolean, onAdd: () -> Unit, onPrivacy: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (hasHidden) Icons.Rounded.Lock else Icons.Rounded.Add,
                contentDescription = null,
                tint = Aqua,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = if (hasHidden) "Apps are in Private Space" else "Add your first app",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = if (hasHidden) {
                    "Open Private Space to access hidden clones."
                } else {
                    "Create a separate account space with isolated app data."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            Button(onClick = if (hasHidden) onPrivacy else onAdd) {
                Text(if (hasHidden) "Open Private Space" else "Add app")
            }
        }
    }
}
