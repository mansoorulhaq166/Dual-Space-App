package com.example.dual.space.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun DualSpaceApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    // Secondary tabs are in-app destinations, not separate activities. Consume system back
    // here so Add/Private Space always return to Home instead of finishing MainActivity.
    BackHandler(enabled = !state.locked && state.screen != Screen.HOME) {
        viewModel.openHome()
    }

    Box(Modifier.fillMaxSize()) {
        if (state.locked) {
            LockScreen(
                error = state.unlockError,
                onUnlock = viewModel::unlock,
            )
        } else {
            when (state.screen) {
                Screen.HOME -> HomeScreen(
                    clones = state.clones,
                    loading = state.clonesLoading,
                    hiddenKeys = state.hiddenKeys,
                    favoriteKeys = state.favoriteKeys,
                    recentKeys = state.recentKeys,
                    lowMemoryMode = state.lowMemoryMode,
                    onAddClick = viewModel::openAddScreen,
                    onPrivacyClick = viewModel::openPrivacy,
                    onLaunch = viewModel::launchClone,
                    onRestart = viewModel::restartClone,
                    onCreateShortcut = viewModel::createShortcut,
                    onCloneAgain = { viewModel.addClones(listOf(it.packageName)) },
                    onRename = viewModel::renameClone,
                    onFavorite = viewModel::setFavorite,
                    onHide = viewModel::setHidden,
                    onClearData = viewModel::clearCloneData,
                    onRemove = viewModel::removeClone,
                    onOptimize = viewModel::stopAllClones,
                    onShareLogs = viewModel::shareLogs,
                )

                Screen.ADD -> AddAppScreen(
                    apps = state.installed,
                    loading = state.installedLoading,
                    cloneCounts = state.clones.groupingBy { it.packageName }.eachCount(),
                    onClone = { selected ->
                        viewModel.openHome()
                        viewModel.addClones(selected.map { it.packageName })
                    },
                    onBack = viewModel::openHome,
                )

                Screen.PRIVACY -> PrivacyScreen(
                    clones = state.clones,
                    hiddenKeys = state.hiddenKeys,
                    appLockEnabled = state.appLockEnabled,
                    lowMemoryMode = state.lowMemoryMode,
                    onBack = viewModel::openHome,
                    onLaunch = viewModel::launchClone,
                    onUnhide = { viewModel.setHidden(it, false) },
                    onSetPin = viewModel::setPin,
                    onDisablePin = viewModel::disablePin,
                    onLowMemoryMode = viewModel::setLowMemoryMode,
                    onOptimize = viewModel::stopAllClones,
                    onShareLogs = viewModel::shareLogs,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp),
        )

        state.busyLabel?.let { label: String ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 68.dp, start = 20.dp, end = 20.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 10.dp,
                shadowElevation = 10.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
