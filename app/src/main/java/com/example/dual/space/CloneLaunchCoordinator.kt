package com.example.dual.space

import android.content.Context
import com.example.dual.space.diagnostics.DiagnosticsLog
import com.example.dual.space.engine.DualCoreEngine
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

/** Ensures repeated taps and shortcuts share one launch per cloned package/user. */
internal object CloneLaunchCoordinator {
    // Clone starts can block on process attachment. A bounded pool prevents repeated app taps from
    // creating an unbounded number of threads on low-end devices.
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    private val inFlight = ConcurrentHashMap<String, FutureTask<Boolean>>()

    fun obtain(
        context: Context,
        packageName: String,
        userId: Int,
        forceRestart: Boolean,
    ): FutureTask<Boolean> {
        val appContext: Context = context.applicationContext
        val key: String = "$userId/$packageName"
        val candidate: FutureTask<Boolean> = FutureTask(
            Callable<Boolean> {
                val engine = DualCoreEngine(appContext)
                if (forceRestart) {
                    engine.stopClone(packageName, userId)
                }
                engine.launchClone(packageName, userId)
            },
        )
        val existing: FutureTask<Boolean>? = inFlight.putIfAbsent(key, candidate)
        if (existing != null) {
            DiagnosticsLog.event(
                appContext,
                "LAUNCH-SINGLE-FLIGHT",
                "joined existing launch pkg=$packageName u$userId",
            )
            return existing
        }

        executor.execute(
            Runnable {
                try {
                    candidate.run()
                } finally {
                    inFlight.remove(key, candidate)
                }
            },
        )
        return candidate
    }
}
