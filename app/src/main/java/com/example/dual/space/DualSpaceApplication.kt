package com.example.dual.space

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.dual.space.diagnostics.DiagnosticsLog
import com.example.dual.space.engine.EngineBootState
import org.lsposed.hiddenapibypass.HiddenApiBypass
import com.dualcore.DualCore
import com.dualcore.app.configuration.ClientConfiguration

/**
 * Boots the DualCore virtualization engine. This Application runs in every process
 * DualCore spawns (main UI + the sandbox :p / service processes); the engine's
 * attachBaseContext handles per-process setup. Mirrors the reference integration.
 */
class DualSpaceApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Install the on-device crash logger first, so a crash during engine init below is still
        // captured to the diagnostics file (no adb/root needed to retrieve it).
        DiagnosticsLog.ensureHandler(base)
        // Unseal hidden/non-SDK APIs BEFORE the engine initializes. At targetSdk 34 the
        // engine's own FreeReflection unsealing is OS-denied, so do it here with LSPosed's
        // Unsafe-based bypass (works up to Android 15 regardless of targetSdk). Without this,
        // the sandbox can't grant guests hidden-API access and they crash on launch.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
                .onFailure { initError(base, "HiddenApiBypass", it) }
        }
        runCatching { DualCore.get().closeCodeInit() }
            .onFailure { initError(base, "closeCodeInit", it) }
        runCatching { DualCore.get().onBeforeMainApplicationAttach(this, base) }
            .onFailure { initError(base, "onBeforeMainApplicationAttach", it) }
        runCatching {
            DualCore.get().doAttachBaseContext(base, object : ClientConfiguration() {
                override fun getHostPackageName(): String = packageName

                // Launch the proxy activity directly. The engine's extra LauncherActivity splash kept
                // a second host task alive, added 100 ms plus a thread hop, and could resurface the Dual
                // Space home while a slow guest was still starting. The Compose UI already provides
                // launch feedback, so the wrapper only hurts reliability and low-end performance.
                override fun isEnableLauncherActivity(): Boolean = false

                // Dual Space Pro-style host GMS by default. Set BuildConfig.USE_MICROG_GOOGLE_LOGIN
                // true to restore in-clone microG login without deleting that stack.
                override fun isUseMicrogGoogleLogin(): Boolean = BuildConfig.USE_MICROG_GOOGLE_LOGIN
            })
        }.onFailure { initError(base, "doAttachBaseContext", it) }
        runCatching { DualCore.get().onAfterMainApplicationAttach(this, base) }
            .onFailure { initError(base, "onAfterMainApplicationAttach", it) }
        // Inject FLAG_IMMUTABLE into guests' PendingIntent calls (sandbox processes only),
        // so heavy apps like Facebook don't crash on the S+ PendingIntent requirement.
        com.example.dual.space.engine.PendingIntentFix.installIfSandbox()
    }

    override fun onCreate() {
        super.onCreate()
        // Full diagnostics install: session header + ANR watchdog (main process), and re-assert the
        // crash handler so it wraps any handler the engine installed during attachBaseContext.
        runCatching { DiagnosticsLog.install(this) }
        runCatching { DualCore.get().doCreate() }
            .onSuccess { EngineBootState.ready = true }
            .onFailure { initError(this, "doCreate", it) }
    }

    /** Log an engine-init failure to logcat AND the on-device diagnostics file. */
    private fun initError(context: Context, step: String, t: Throwable) {
        Log.e(TAG, step, t)
        runCatching { DiagnosticsLog.logThrowable(context, "init:$step", t) }
    }

    private companion object {
        const val TAG = "DualSpaceApp"
    }
}
