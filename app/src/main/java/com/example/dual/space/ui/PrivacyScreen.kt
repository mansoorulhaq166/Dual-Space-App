package com.example.dual.space.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.dual.space.engine.AppInfo
import com.example.dual.space.ui.theme.Aqua
import com.example.dual.space.ui.theme.ElectricViolet
import com.example.dual.space.ui.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    clones: List<AppInfo>,
    hiddenKeys: Set<String>,
    appLockEnabled: Boolean,
    lowMemoryMode: Boolean,
    onBack: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onUnhide: (AppInfo) -> Unit,
    onSetPin: (String, String) -> Unit,
    onDisablePin: () -> Unit,
    onLowMemoryMode: (Boolean) -> Unit,
    onOptimize: () -> Unit,
    onShareLogs: () -> Unit,
) {
    var showPinDialog by remember { mutableStateOf(false) }
    val hidden = remember(clones, hiddenKeys) { clones.filter { it.cloneKey in hiddenKeys } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                title = {
                    Column {
                        Text("Private Space", style = MaterialTheme.typography.titleLarge)
                        Text("Security and performance controls", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 10.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PrivacyHero(hiddenCount = hidden.size, lockEnabled = appLockEnabled)
            }
            item {
                SettingCard(
                    icon = Icons.Rounded.Lock,
                    title = "Privacy lock",
                    description = "Require a local PIN whenever you return to Dual Space.",
                    checked = appLockEnabled,
                    onChecked = { enabled -> if (enabled) showPinDialog = true else onDisablePin() },
                )
            }
            item {
                SettingCard(
                    icon = Icons.Rounded.Memory,
                    title = "Ultra low-memory mode",
                    description = "Keeps one user clone active at a time. Recommended for 2–3 GB phones.",
                    checked = lowMemoryMode,
                    onChecked = onLowMemoryMode,
                )
            }
            item {
                ActionCard(
                    icon = Icons.Rounded.CleaningServices,
                    title = "Quick optimize",
                    description = "Stops cloned apps to free memory. Your accounts and data stay intact.",
                    actionText = "Optimize",
                    onAction = onOptimize,
                )
            }
            item {
                Text("Hidden clones", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text(
                    if (hidden.isEmpty()) "No apps are hidden" else "Only visible after unlocking Dual Space",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (hidden.isEmpty()) {
                item { EmptyPrivateApps() }
            } else {
                items(hidden, key = { it.cloneKey }) { app ->
                    HiddenAppRow(app = app, onLaunch = { onLaunch(app) }, onUnhide = { onUnhide(app) })
                }
            }
            item {
                ActionCard(
                    icon = Icons.Rounded.BugReport,
                    title = "Diagnostics",
                    description = "Share startup, memory, microG and process logs when an app fails.",
                    actionText = "Share logs",
                    onAction = onShareLogs,
                )
            }
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = { showPinDialog = false },
            onSave = { pin, confirmation ->
                showPinDialog = false
                onSetPin(pin, confirmation)
            },
        )
    }
}

@Composable
private fun PrivacyHero(hiddenCount: Int, lockEnabled: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ElectricViolet.copy(alpha = .17f)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Security, null, tint = Aqua, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Your accounts. Your space.", style = MaterialTheme.typography.titleLarge)
                Text(
                    "$hiddenCount hidden • Lock ${if (lockEnabled) "on" else "off"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().clickable { onChecked(!checked) },
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (checked) Success else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Aqua, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = onAction) { Text(actionText) }
        }
    }
}

@Composable
private fun HiddenAppRow(app: AppInfo, onLaunch: () -> Unit, onUnhide: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(app.packageName, app.label, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.displayLabel, style = MaterialTheme.typography.titleMedium)
                Text("Protected clone", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onLaunch) { Icon(Icons.Rounded.PlayArrow, "Open") }
            IconButton(onClick = onUnhide) { Icon(Icons.Rounded.Visibility, "Restore") }
        }
    }
}

@Composable
private fun EmptyPrivateApps() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Visibility, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("Long-press a clone on Home and choose “Move to Private Space”.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = pin.length in 4..8 && pin == confirmation && pin.all(Char::isDigit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create privacy PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Use 4–8 digits. The PIN is stored only on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                    label = { Text("New PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmation = it },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(pin, confirmation) }) { Text("Enable") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
