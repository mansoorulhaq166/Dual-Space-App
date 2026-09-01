package com.example.dual.space.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import com.example.dual.space.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device crash + ANR logger.
 *
 * Writes crashes (uncaught exceptions) and main-thread stalls (ANRs) to a file under the app's
 * private INTERNAL files dir (never world-readable), retrievable via the in-app Share action.
 *
 * Wiring (see DualSpaceApplication): call [ensureHandler] at the very start of attachBaseContext
 * (to catch engine-init crashes), [install] in onCreate (header + ANR watchdog + re-assert the
 * handler so it wraps the engine's), and [logThrowable] from any catch site you want recorded.
 * The crash handler chains to whatever was previously installed, so the engine's own handling and
 * the system dialog still run.
 */
object DiagnosticsLog {
    private const val TAG = "DiagnosticsLog"
    private const val DIR = "diagnostics"
    private const val FILE = "diagnostic-log.txt"
    private const val MAX_BYTES = 512 * 1024L
    private const val ANR_TIMEOUT_MS = 5000L
    private const val ANR_STARTUP_GRACE_MS = 12000L // ignore cold-start jank on slow devices
    private const val MAX_NATIVE_BLOCK_LINES = 90 // enough for the signal header + full backtrace
    private const val PERF_TAG = "DualCorePerf" // engine PerfTrace logcat tag (DSPERF spans)
    private const val PERF_SLOW_MS = 100 // only capture spans at/over this — the ones that explain slowness
    private const val AUTH_TAG = "DualCoreGmsAuth" // engine Google Sign-in authenticator-bind trace
    private const val HEALTH_INTERVAL_MS = 8000L

    @Volatile private var headerWritten = false
    @Volatile private var watchdogStarted = false
    @Volatile private var nativeReaderStarted = false
    @Volatile private var perfReaderStarted = false
    @Volatile private var healthSamplerStarted = false
    @Volatile private var reassertStarted = false

    /**
     * App context if available, else the passed context. During Application.attachBaseContext,
     * getApplicationContext() is still null — using it there NPEs, so we fall back to `context`
     * (the Application's base context, which lives for the whole process). Safe for file I/O.
     */
    private fun ctx(context: Context): Context = context.applicationContext ?: context

    /** Install our uncaught-exception handler (wrapping the current one). Safe to call repeatedly. */
    fun ensureHandler(context: Context) {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is DiagHandler) return
        Thread.setDefaultUncaughtExceptionHandler(DiagHandler(ctx(context), current))
    }

    /** Full install: session header, handler, and (main process only) the ANR watchdog. */
    fun install(context: Context) {
        val app = ctx(context)
        if (!headerWritten) {
            headerWritten = true
            write(app, "SESSION", "device=${Build.MANUFACTURER} ${Build.MODEL}, " +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                    "abi=${Build.SUPPORTED_ABIS.joinToString()}, ram=${totalRamMb(app)}MB, " +
                    "cg=2, process=${processName(app)}") // cg=2 confirms the robust crash-guard build is live
        }
        ensureHandler(app)
        // A cloned guest starts as the host app (our DiagHandler installed), THEN the engine binds the
        // cloned app, which often installs its OWN default uncaught handler ON TOP of ours — so our
        // crash-guard's "return without chaining" can't stop the cloned app's handler from killing the
        // process. Re-assert ours as the outermost handler across the clone's init window so the guard is
        // effective. ensureHandler is a no-op once ours is already on top.
        if (!reassertStarted) {
            reassertStarted = true
            startHandlerReassert(app)
        }
        if (!processName(app).contains(":")) {
            if (!watchdogStarted) {
                watchdogStarted = true
                startAnrWatchdog(app)
            }
            if (!nativeReaderStarted) {
                nativeReaderStarted = true
                startNativeCrashReader(app)
            }
            if (!perfReaderStarted) {
                perfReaderStarted = true
                startPerfReader(app)
            }
            if (!healthSamplerStarted) {
                healthSamplerStarted = true
                startHealthSampler(app)
            }
        }
    }

    private fun totalRamMb(context: Context): Long = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.totalMem / 1048576
    }.getOrDefault(-1)

    /** Record a caught throwable (e.g. from an engine-init try/catch). */
    fun logThrowable(context: Context, tag: String, t: Throwable) {
        write(ctx(context), tag, Log.getStackTraceString(t))
    }

    /** Record a one-line diagnostic event (e.g. a slow clone launch timing) into the shared file. */
    fun event(context: Context, tag: String, message: String) {
        write(ctx(context), tag, message)
    }

    /** Capture launch-window memory immediately and again at 1s, 3s and 6s. A guest killed before
     *  the periodic health sampler's first tick would otherwise leave no memory evidence. */
    fun probeLaunchMemory(context: Context, packageName: String, userId: Int) {
        val app = ctx(context)
        val thread = Thread {
            val sampleAt = longArrayOf(0L, 1_000L, 3_000L, 6_000L)
            var previous = 0L
            for (at in sampleAt) {
                val sleep = at - previous
                if (sleep > 0L) {
                    try { Thread.sleep(sleep) } catch (_: InterruptedException) { return@Thread }
                }
                writeMemorySnapshot(app, "pkg=$packageName u$userId t=${at}ms")
                previous = at
            }
        }
        thread.name = "ds-launch-memory"
        thread.isDaemon = true
        thread.start()
    }

    /** Record OS-level exit reasons after a verified launch bounce. SIGKILL/LMK/native deaths do not
     *  pass through a Java uncaught-exception handler, but Android 11+ retains them here. */
    fun recordRecentExitReasons(
        context: Context,
        packageName: String,
        userId: Int,
        launchWallTimeMs: Long,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val app = ctx(context)
        runCatching {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exits = am.getHistoricalProcessExitReasons(BuildConfig.APPLICATION_ID, 0, 12)
                .filter { it.timestamp >= launchWallTimeMs - 2_000L }
            if (exits.isEmpty()) {
                write(app, "EXIT", "pkg=$packageName u$userId no recent OS exit record")
            } else {
                exits.forEach { exit ->
                    write(
                        app,
                        "EXIT",
                        "pkg=$packageName u$userId process=${exit.processName} reason=${exit.reason} " +
                            "status=${exit.status} importance=${exit.importance} " +
                            "pss=${exit.pss / 1024}MB rss=${exit.rss / 1024}MB " +
                            "description=${exit.description ?: ""}",
                    )
                }
            }
        }.onFailure { write(app, "EXIT", "query failed: ${it.message}") }
    }

    private fun writeMemorySnapshot(context: Context, prefix: String) {
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val ours = am.runningAppProcesses.orEmpty().filter {
                it.processName == BuildConfig.APPLICATION_ID ||
                    it.processName.startsWith("${BuildConfig.APPLICATION_ID}:")
            }
            val pids = ours.map { it.pid }.toIntArray()
            val details = if (pids.isEmpty()) "none" else {
                val memory = am.getProcessMemoryInfo(pids)
                ours.indices.joinToString(separator = ",") { index ->
                    val info = memory.getOrNull(index)
                    "${ours[index].processName}(pid=${ours[index].pid},pss=${info?.totalPss ?: -1}KB)"
                }
            }
            write(
                context,
                "LAUNCH-MEM",
                "$prefix avail=${mi.availMem / 1048576}MB total=${mi.totalMem / 1048576}MB " +
                    "threshold=${mi.threshold / 1048576}MB low=${mi.lowMemory} hostProcesses=$details",
            )
        }.onFailure { write(context, "LAUNCH-MEM", "$prefix failed=${it.message}") }
    }

    /** The diagnostics file (created on demand). */
    fun logFile(context: Context): File {
        val dir = diagnosticsDir(context).apply { mkdirs() }
        return File(dir, FILE)
    }

    /**
     * The diagnostics dir on INTERNAL storage — private on every API level, so it is never
     * world-readable (the old external path was readable by any app holding READ_EXTERNAL_STORAGE on
     * API 24-28, leaking cloned-app crash stacks). In a sandboxed GUEST process (`:pN`, running a
     * cloned app) the engine REDIRECTS file access into the guest's virtual storage — so a redirected
     * context.filesDir there would land in a file the host can't see. For guests we therefore build
     * the HOST app's REAL internal path explicitly via BuildConfig.APPLICATION_ID (never spoofed),
     * which the engine doesn't redirect — so every process appends to the SAME private file the Share
     * action reads.
     */
    private fun diagnosticsDir(context: Context): File {
        val proc = processName(context)
        val isSandboxGuest = proc.contains(":p")
        if (isSandboxGuest) {
            return File("/data/data/${BuildConfig.APPLICATION_ID}/files/$DIR")
        }
        return File(context.filesDir, DIR)
    }

    /** Fire a share-sheet so the user can send the log file. Returns false if there's nothing to share. */
    fun share(context: Context): Boolean {
        val file = logFile(context)
        if (!file.exists() || file.length() == 0L) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "DualSpace diagnostics")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share diagnostics").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(chooser); true }.getOrDefault(false)
    }

    private class DiagHandler(
        private val ctx: Context,
        private val next: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            val guarded = isSurvivableGuestThreadCrash(ctx, t, e)
            // Record either way (CRASH-GUARDED is clearly non-fatal in the log), then for the guarded case
            // RETURN without chaining to the killing handler: the offending thread dies but the clone's
            // process lives on.
            runCatching {
                write(ctx, if (guarded) "CRASH-GUARDED" else "CRASH", "thread=${t.name}\n" + Log.getStackTraceString(e))
            }
            if (guarded) return
            next?.uncaughtException(t, e)
        }
    }

    /**
     * True only for the narrow case it is safe to swallow: a sandboxed GUEST (`:pN`, a cloned app), a
     * NON-main background thread, dying from a class-load failure. This is exactly the pattern where a
     * cloned app's optional background init (e.g. Facebook's Achilles push provider, which throws
     * ClassNotFoundException for FirebaseInitCustomProvider$Impl) would otherwise kill the whole clone and
     * trigger an expensive re-fork loop — even though the app's main functionality is unaffected.
     *
     * Deliberately conservative so real crashes still surface: never the host or :black, never the main
     * thread (a main-thread crash genuinely breaks the app), and only when a ClassNotFoundException /
     * NoClassDefFoundError appears somewhere in the cause chain (the FB throwable is an
     * IllegalArgumentException wrapping the CNFE). Any failure to evaluate this defaults to NOT survivable.
     */
    private fun isSurvivableGuestThreadCrash(context: Context, t: Thread, e: Throwable): Boolean = runCatching {
        // Never swallow a main-thread crash (it genuinely breaks the app). Robust to a missing main looper:
        // fall back to the thread name, since the engine can rename threads/processes.
        val mainThread = runCatching { Looper.getMainLooper()?.thread }.getOrNull()
        if (t === mainThread || t.name == "main") return@runCatching false
        // Scan the WHOLE throwable graph (causes + suppressed, cycle-safe) for two things:
        //  (a) a class-load failure (the FB throwable is IllegalArgumentException -> ClassNotFoundException),
        //  (b) an engine frame (com.dualcore.*) proving this is a virtualized clone — which appears in the
        //      SUPPRESSED dex-load exceptions even when the top-level stack has none, and is reliable even
        //      if the guest process was renamed to the cloned app (so we don't depend on the ":p" name).
        var classLoad = false
        var inEngine = runCatching { processName(context).contains(":p") }.getOrDefault(false)
        val seen = java.util.IdentityHashMap<Throwable, Boolean>()
        val stack = ArrayList<Throwable>()
        stack.add(e)
        while (stack.isNotEmpty()) {
            val cur = stack.removeAt(stack.size - 1)
            if (seen.put(cur, true) != null) continue
            if (cur is ClassNotFoundException || cur is NoClassDefFoundError) classLoad = true
            if (!inEngine && cur.stackTrace.any { it.className.startsWith("com.dualcore.") }) inEngine = true
            if (classLoad && inEngine) return@runCatching true
            cur.cause?.let { stack.add(it) }
            for (s in cur.suppressed) stack.add(s)
        }
        false
    }.getOrDefault(false)

    private fun write(context: Context, tag: String, body: String) {
        runCatching {
            val f = logFile(context)
            if (f.exists() && f.length() > MAX_BYTES) f.writeText("") // simple rotation
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            f.appendText("\n===== $tag $ts =====\n$body\n")
        }.onFailure { Log.e(TAG, "write failed", it) }
    }

    private fun startAnrWatchdog(context: Context) {
        val mainHandler = Handler(Looper.getMainLooper())
        val watchdog = Thread {
            val main = Looper.getMainLooper().thread
            // Cold start on low-end devices legitimately keeps the main thread busy for several
            // seconds (class loading, first Compose frame). Skip that window so we don't log
            // startup jank as an ANR — we only want genuine mid-session hangs.
            try { Thread.sleep(ANR_STARTUP_GRACE_MS) } catch (e: InterruptedException) { return@Thread }
            while (!Thread.currentThread().isInterrupted) {
                val completed = AtomicBoolean(false)
                mainHandler.post { completed.set(true) }
                try {
                    Thread.sleep(ANR_TIMEOUT_MS)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                if (!completed.get()) {
                    val sb = StringBuilder("main thread unresponsive > ${ANR_TIMEOUT_MS}ms\n")
                    for (s in main.stackTrace) sb.append("\tat ").append(s).append('\n')
                    write(context, "ANR", sb.toString())
                    while (!completed.get() && !Thread.currentThread().isInterrupted) {
                        try { Thread.sleep(1000) } catch (e: InterruptedException) { return@Thread }
                    }
                }
            }
        }
        watchdog.name = "ds-anr-watchdog"
        watchdog.isDaemon = true
        watchdog.start()
    }

    /**
     * Capture NATIVE crashes (SIGSEGV/SIGABRT/...) that a Java UncaughtExceptionHandler can never see —
     * which is exactly why a native crash leaves the diagnostics file empty. The OS logs the signal +
     * debuggerd backtrace to logcat's dedicated `crash` buffer, tagged under the *crashing process's*
     * uid; an app may read its OWN uid's logs with no special permission. Every sandbox guest (`:pN`,
     * running a cloned app) shares the host uid, so one reader in the host process captures cloned-app
     * native crashes too — and the host outlives a crashing guest, so the capture survives. We tail the
     * crash buffer and append each crash block (signal + ABI + pid/tid/name + registers + backtrace) to
     * the same file the Share action exports. No adb, no root.
     */
    private fun startNativeCrashReader(context: Context) {
        val reader = Thread {
            try {
                // -b crash: the buffer debuggerd writes native crash reports to. -T 1: start near "now"
                // so we don't re-dump the whole historical buffer on every launch. Read-only (no -c/-f),
                // and only in the un-virtualized host process — so this can't hit the IO-hook issues that
                // made the engine's old in-guest logcat spawn crash on some OEM images.
                val proc = ProcessBuilder("logcat", "-b", "crash", "-v", "threadtime", "-T", "1")
                    .redirectErrorStream(true)
                    .start()
                proc.inputStream.bufferedReader().use { input ->
                    val block = StringBuilder()
                    var remaining = 0
                    while (true) {
                        val line = input.readLine() ?: break
                        if (remaining == 0 && (line.contains("Fatal signal") || line.contains("*** *** ***"))) {
                            remaining = MAX_NATIVE_BLOCK_LINES
                        }
                        if (remaining > 0) {
                            block.append(line).append('\n')
                            if (--remaining == 0) {
                                write(context, "NATIVE-CRASH", block.toString().trimEnd())
                                block.setLength(0)
                            }
                        }
                    }
                    if (block.isNotEmpty()) write(context, "NATIVE-CRASH", block.toString().trimEnd())
                }
            } catch (e: Throwable) {
                Log.w(TAG, "native crash reader stopped: ${e.message}")
            }
        }
        reader.name = "ds-native-crash-reader"
        reader.isDaemon = true
        reader.start()
    }

    /**
     * Surface the engine's PerfTrace timing in the shared file (no adb needed). The engine emits
     * "DSPERF op=… ms=… onMain=… proc=…" via Log.i(DualCorePerf) from :black and the guest processes,
     * which share the host uid — so the host's own logcat sees them. We keep only the spans that explain
     * slowness (≥ PERF_SLOW_MS) or that ran on a main thread (onMain=1, an ANR signal), batched so the
     * file stays small. This is what tells us WHERE a slow FB→WhatsApp launch spends its time.
     */
    private fun startPerfReader(context: Context) {
        val reader = Thread {
            try {
                val proc = ProcessBuilder("logcat", "-v", "brief", "-s", "$PERF_TAG:I", "$AUTH_TAG:I", "-T", "1")
                    .redirectErrorStream(true)
                    .start()
                proc.inputStream.bufferedReader().use { input ->
                    val batch = StringBuilder()
                    var lastFlush = System.currentTimeMillis()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.contains(AUTH_TAG)) {
                            // Google Sign-in authenticator-bind trace (low volume) — capture verbatim so the
                            // shared file shows exactly where the in-clone bind succeeds or fails.
                            write(context, "GMSAUTH", line.substringAfter("): ", line).trim())
                            continue
                        }
                        val i = line.indexOf("DSPERF")
                        if (i < 0) continue
                        val span = line.substring(i)
                        val ms = Regex("ms=(\\d+)").find(span)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        if (ms >= PERF_SLOW_MS || span.contains("onMain=1")) {
                            batch.append(span).append('\n')
                        }
                        val now = System.currentTimeMillis()
                        if (batch.isNotEmpty() && (batch.length > 4000 || now - lastFlush > 3000)) {
                            write(context, "PERF", batch.toString().trimEnd())
                            batch.setLength(0)
                            lastFlush = now
                        }
                    }
                    if (batch.isNotEmpty()) write(context, "PERF", batch.toString().trimEnd())
                }
            } catch (e: Throwable) {
                Log.w(TAG, "perf reader stopped: ${e.message}")
            }
        }
        reader.name = "ds-perf-reader"
        reader.isDaemon = true
        reader.start()
    }

    /**
     * Periodically record a memory snapshot — but only when the system is actually under pressure
     * (lowMemory, or free RAM within ~2× the kill threshold). On a 2–4 GB device, cloned Facebook katana
     * plus another clone can drive the low-memory killer, which kills clones (and can kill the host) with
     * NO Java stack trace — so a crash that leaves the file empty is explained by a HEALTH line here.
     */
    private fun startHealthSampler(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val t = Thread {
            try { Thread.sleep(HEALTH_INTERVAL_MS) } catch (e: InterruptedException) { return@Thread }
            while (!Thread.currentThread().isInterrupted) {
                runCatching {
                    val mi = ActivityManager.MemoryInfo()
                    am.getMemoryInfo(mi)
                    if (mi.lowMemory || mi.availMem < mi.threshold * 2) {
                        val procs = runCatching {
                            am.runningAppProcesses.orEmpty().count {
                                it.processName == BuildConfig.APPLICATION_ID ||
                                    it.processName.startsWith("${BuildConfig.APPLICATION_ID}:")
                            }
                        }.getOrDefault(-1)
                        write(context, "HEALTH", "availMem=${mi.availMem / 1048576}MB " +
                                "total=${mi.totalMem / 1048576}MB killThreshold=${mi.threshold / 1048576}MB " +
                                "lowMemory=${mi.lowMemory} hostProcesses=$procs")
                    }
                }
                try { Thread.sleep(HEALTH_INTERVAL_MS) } catch (e: InterruptedException) { return@Thread }
            }
        }
        t.name = "ds-health-sampler"
        t.isDaemon = true
        t.start()
    }

    /**
     * Keep our DiagHandler the OUTERMOST default uncaught-exception handler across a clone's init window.
     * The cloned app may install its own handler after ours; without this, that handler would kill the
     * process before our crash-guard's decision matters. Each ensureHandler() re-wraps the current default
     * if it isn't already ours, so after the clone sets its handler we end up on top again. A handful of
     * re-asserts over the first ~10s covers the init window cheaply.
     */
    private fun startHandlerReassert(context: Context) {
        val t = Thread {
            val delays = longArrayOf(700, 1500, 3000, 5000, 8000)
            for (d in delays) {
                try { Thread.sleep(d) } catch (e: InterruptedException) { return@Thread }
                runCatching { ensureHandler(context) }
            }
        }
        t.name = "ds-handler-reassert"
        t.isDaemon = true
        t.start()
    }

    private fun processName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.app.Application.getProcessName()?.let { return it }
        }
        return runCatching {
            File("/proc/self/cmdline").readBytes().toString(Charsets.UTF_8).trim(' ', '\u0000', '\n')
        }.getOrDefault(context.packageName)
    }
}
