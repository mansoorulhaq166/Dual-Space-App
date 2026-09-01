package com.example.dual.space.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.dual.space.CloneLaunchActivity
import androidx.lifecycle.viewModelScope
import com.example.dual.space.diagnostics.DiagnosticsLog
import com.example.dual.space.engine.AppInfo
import com.example.dual.space.engine.VirtualEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, ADD, PRIVACY }

data class UiState(
    val screen: Screen = Screen.HOME,
    val clones: List<AppInfo> = emptyList(),
    val clonesLoading: Boolean = true,
    val installed: List<AppInfo> = emptyList(),
    val installedLoading: Boolean = false,
    val busyLabel: String? = null,
    val hiddenKeys: Set<String> = emptySet(),
    val favoriteKeys: Set<String> = emptySet(),
    val recentKeys: List<String> = emptyList(),
    val lowMemoryMode: Boolean = false,
    val appLockEnabled: Boolean = false,
    val locked: Boolean = false,
    val unlockError: Boolean = false,
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
}

class MainViewModel(
    private val engine: VirtualEngine,
    private val appContext: Context,
) : ViewModel() {

    private val preferences = AppPreferences(appContext)
    private val _state = MutableStateFlow(
        UiState(
            hiddenKeys = preferences.hiddenKeys(),
            favoriteKeys = preferences.favoriteKeys(),
            recentKeys = preferences.recentKeys(),
            lowMemoryMode = preferences.lowMemoryMode,
            appLockEnabled = preferences.appLockEnabled,
            locked = preferences.appLockEnabled,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { reloadClones() }

    fun reloadClones() {
        viewModelScope.launch {
            _state.update { it.copy(clonesLoading = it.clones.isEmpty()) }
            val clones = withContext(Dispatchers.IO) {
                engine.clonedApps().map { app: AppInfo ->
                    val alias: String? = preferences.aliasFor(app.cloneKey)
                    if (alias == null) app else app.copy(label = alias)
                }
            }
            _state.update {
                it.copy(
                    clones = clones,
                    clonesLoading = false,
                    hiddenKeys = preferences.hiddenKeys(),
                    favoriteKeys = preferences.favoriteKeys(),
                    recentKeys = preferences.recentKeys(),
                )
            }
        }
    }

    fun openAddScreen() {
        _state.update { it.copy(screen = Screen.ADD) }
        loadInstalledApps()
    }

    fun openHome() = _state.update { it.copy(screen = Screen.HOME) }
    fun openPrivacy() = _state.update { it.copy(screen = Screen.PRIVACY) }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _state.update { it.copy(installedLoading = true) }
            val apps = withContext(Dispatchers.IO) { engine.installedApps() }
            _state.update { it.copy(installed = apps, installedLoading = false) }
        }
    }

    fun addClones(packageNames: List<String>) {
        if (packageNames.isEmpty() || _state.value.busyLabel != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyLabel = "Creating private space…") }
            val failed = withContext(Dispatchers.IO) { packageNames.count { !engine.addClone(it) } }
            _state.update { it.copy(busyLabel = null) }
            if (failed > 0) {
                message(if (packageNames.size == 1) "This app could not be cloned" else "$failed apps could not be cloned")
            } else {
                message(if (packageNames.size == 1) "Clone created" else "${packageNames.size} clones created")
            }
            reloadClones()
        }
    }

    fun launchClone(app: AppInfo) {
        if (_state.value.busyLabel != null) return
        _state.update { it.copy(screen = Screen.HOME) }
        preferences.markLaunched(app.cloneKey)
        _state.update { it.copy(recentKeys = preferences.recentKeys()) }
        // Show a dedicated launch surface immediately. Engine startup then happens from that activity,
        // so slow virtual-process creation never looks like a frozen/unresponsive Home screen.
        CloneLaunchActivity.routeThroughHost(
            context = appContext,
            packageName = app.packageName,
            userId = app.userId,
            label = app.displayLabel,
        )
    }

    fun restartClone(app: AppInfo) {
        if (_state.value.busyLabel != null) return
        _state.update { it.copy(screen = Screen.HOME) }
        preferences.markLaunched(app.cloneKey)
        _state.update { it.copy(recentKeys = preferences.recentKeys()) }
        CloneLaunchActivity.routeThroughHost(
            context = appContext,
            packageName = app.packageName,
            userId = app.userId,
            label = app.displayLabel,
            forceRestart = true,
        )
    }

    fun stopAllClones() {
        if (_state.value.busyLabel != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyLabel = "Optimizing memory…") }
            val stopped = withContext(Dispatchers.IO) {
                _state.value.clones.count { engine.stopClone(it.packageName, it.userId) }
            }
            _state.update { it.copy(busyLabel = null) }
            message(if (stopped == 0) "Nothing was running" else "$stopped clone${if (stopped == 1) "" else "s"} stopped")
        }
    }

    fun clearCloneData(app: AppInfo) {
        if (_state.value.busyLabel != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyLabel = "Clearing ${app.displayLabel}…") }
            val ok = withContext(Dispatchers.IO) { engine.clearCloneData(app.packageName, app.userId) }
            _state.update { it.copy(busyLabel = null) }
            message(if (ok) "${app.displayLabel} was reset" else "Could not clear ${app.displayLabel}")
        }
    }

    fun removeClone(app: AppInfo) {
        if (_state.value.busyLabel != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyLabel = "Removing ${app.displayLabel}…") }
            withContext(Dispatchers.IO) { engine.removeClone(app.packageName, app.userId) }
            preferences.removeCloneMetadata(app.cloneKey)
            _state.update { it.copy(busyLabel = null) }
            reloadClones()
            message("Clone removed")
        }
    }

    fun renameClone(app: AppInfo, alias: String?) {
        preferences.setAlias(app.cloneKey, alias)
        reloadClones()
        message(if (alias.isNullOrBlank()) "Original name restored" else "Clone renamed")
    }

    fun setFavorite(app: AppInfo, favorite: Boolean) {
        preferences.setFavorite(app.cloneKey, favorite)
        _state.update { it.copy(favoriteKeys = preferences.favoriteKeys()) }
    }

    fun setHidden(app: AppInfo, hidden: Boolean) {
        preferences.setHidden(app.cloneKey, hidden)
        _state.update { it.copy(hiddenKeys = preferences.hiddenKeys()) }
        message(if (hidden) "Moved to Private Space" else "Restored to Home")
    }

    fun setLowMemoryMode(enabled: Boolean) {
        preferences.lowMemoryMode = enabled
        _state.update { it.copy(lowMemoryMode = enabled) }
        message(if (enabled) "Ultra low-memory mode enabled" else "Balanced performance mode enabled")
    }

    fun setPin(pin: String, confirmation: String) {
        if (pin != confirmation || pin.length !in 4..8 || pin.any { !it.isDigit() }) {
            message("Use matching 4–8 digit PINs")
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val ok = preferences.setPin(pin)
            _state.update { it.copy(appLockEnabled = ok, locked = false, unlockError = false) }
            message(if (ok) "Privacy lock enabled" else "Could not save the PIN")
        }
    }

    fun disablePin() {
        preferences.disablePin()
        _state.update { it.copy(appLockEnabled = false, locked = false, unlockError = false) }
        message("Privacy lock disabled")
    }

    fun unlock(pin: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val valid = preferences.verifyPin(pin)
            _state.update { it.copy(locked = !valid, unlockError = !valid) }
        }
    }

    /** Called when Dual Space leaves the foreground; returning later requires the PIN when enabled. */
    fun onHostBackgrounded() {
        if (_state.value.appLockEnabled) _state.update { it.copy(locked = true, unlockError = false) }
    }

    fun createShortcut(app: AppInfo) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { ShortcutHelper.pin(appContext, app) }
            message(if (ok) "Shortcut requested" else "Home-screen shortcuts are not supported here")
        }
    }

    fun shareLogs() {
        if (!DiagnosticsLog.share(appContext)) message("No diagnostics yet. Reproduce the issue first.")
    }

    private fun message(text: String) {
        viewModelScope.launch { _events.send(UiEvent.Message(text)) }
    }
}
