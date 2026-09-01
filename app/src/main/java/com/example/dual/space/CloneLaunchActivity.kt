package com.example.dual.space

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.example.dual.space.diagnostics.DiagnosticsLog
import com.example.dual.space.engine.DualCoreEngine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Immediate, lightweight launch surface shared by Home and pinned shortcuts.
 *
 * Virtual process creation can legitimately take several seconds on 32-bit/low-end devices. Keeping
 * this activity in the host task provides visible progress during that work and, crucially, leaves
 * MainActivity directly underneath the cloned task so Back returns to Dual Space instead of Home.
 */
class CloneLaunchActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var statusView: TextView
    private lateinit var detailView: TextView
    private lateinit var progressView: ProgressBar
    private lateinit var actionsView: LinearLayout

    @Volatile
    private var launchSubmitted = false

    @Volatile
    private var launchRunning = false

    @Volatile
    private var foregroundNudgeSent = false

    private var packageNameToLaunch: String = ""
    private var userIdToLaunch: Int = 0
    private var labelToLaunch: String = "App"
    private var forceRestart: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow(window)
        readRequest(intent)
        setContentView(buildContent())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        if (packageNameToLaunch.isBlank()) {
            showFailure("This shortcut is no longer valid")
        } else {
            beginLaunch()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (launchRunning) return
        readRequest(intent)
        beginLaunch()
    }

    override fun onStop() {
        super.onStop()
        // The successful cloned activity has covered this launch surface. Remove only this activity;
        // MainActivity remains in the host task and becomes the natural Back destination.
        if (launchSubmitted && !isChangingConfigurations) {
            finish()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdown()
        super.onDestroy()
    }

    private fun readRequest(source: Intent?) {
        val requestedPackage: String? = source?.getStringExtra(EXTRA_PACKAGE)
        val requestedLabel: String? = source?.getStringExtra(EXTRA_LABEL)
        packageNameToLaunch = requestedPackage.orEmpty()
        userIdToLaunch = source?.getIntExtra(EXTRA_USER_ID, 0) ?: 0
        labelToLaunch = if (requestedLabel.isNullOrBlank()) "App" else requestedLabel
        forceRestart = source?.getBooleanExtra(EXTRA_FORCE_RESTART, false) == true
    }

    private fun beginLaunch() {
        if (launchRunning || packageNameToLaunch.isBlank()) return
        launchRunning = true
        launchSubmitted = false
        foregroundNudgeSent = false
        progressView.visibility = View.VISIBLE
        actionsView.visibility = View.GONE
        statusView.text = if (forceRestart) "Restarting $labelToLaunch" else "Opening $labelToLaunch"
        val youtubeLaunch: Boolean = packageNameToLaunch == YOUTUBE_PACKAGE
        detailView.text = if (youtubeLaunch) {
            "Preparing Google services for this space…"
        } else {
            "Preparing your private space…"
        }

        mainHandler.postDelayed({
            if (launchRunning && !isFinishing) {
                detailView.text = if (youtubeLaunch) {
                    "Starting the private Google services runtime…"
                } else {
                    "Starting the secure app process…"
                }
            }
        }, 900L)
        mainHandler.postDelayed({
            if (launchRunning && !isFinishing) {
                detailView.text = if (youtubeLaunch) {
                    "Google services are readying YouTube…"
                } else {
                    "Almost ready — slower devices may need a moment"
                }
            }
        }, 4_000L)
        mainHandler.postDelayed({
            if (launchRunning && !isFinishing) {
                detailView.text = "Still working — this device needs extra time for a cold start"
            }
        }, 10_000L)

        val pkg = packageNameToLaunch
        val uid = userIdToLaunch
        val restart = forceRestart
        executor.execute {
            val submitted = try {
                CloneLaunchCoordinator.obtain(applicationContext, pkg, uid, restart).get()
            } catch (error: Throwable) {
                DiagnosticsLog.logThrowable(applicationContext, "launchSurface:$pkg u$uid", error)
                false
            }

            mainHandler.post {
                launchRunning = false
                if (isFinishing || isDestroyed) return@post
                if (submitted) {
                    launchSubmitted = true
                    statusView.text = "Opening $labelToLaunch"
                    detailView.text = "Secure space is ready…"
                    scheduleForegroundWatch(pkg, uid)
                } else {
                    showFailure("The app did not start. Retry keeps your clone data unchanged.")
                }
            }
        }
    }

    private fun scheduleForegroundWatch(packageName: String, userId: Int) {
        val facebook: Boolean = packageName == FACEBOOK_PACKAGE || packageName == MESSENGER_PACKAGE
        val slow32Bit: Boolean = Build.SUPPORTED_64_BIT_ABIS.isEmpty()
        val finalTimeoutMs: Long = when {
            facebook && slow32Bit -> 38_000L
            facebook -> 24_000L
            slow32Bit -> 14_000L
            else -> 8_000L
        }

        if (facebook) {
            mainHandler.postDelayed({
                if (!isFinishing && !isDestroyed && hasWindowFocus()) {
                    statusView.text = "Still opening $labelToLaunch"
                    detailView.text = "Facebook is finishing its first-time setup…"
                    progressView.visibility = View.VISIBLE
                }
            }, if (slow32Bit) 8_000L else 6_000L)

            // Some Facebook builds create their process but lose the first proxy-activity delivery.
            // Re-submit the same launcher intent once; do not stop the process or clear clone data.
            mainHandler.postDelayed({
                if (!foregroundNudgeSent && !isFinishing && !isDestroyed && hasWindowFocus()) {
                    foregroundNudgeSent = true
                    executor.execute {
                        try {
                            DualCoreEngine(applicationContext).launchClone(packageName, userId)
                            DiagnosticsLog.event(
                                applicationContext,
                                "FACEBOOK-LAUNCH-NUDGE",
                                "pkg=$packageName u$userId mode=non-destructive",
                            )
                        } catch (error: Throwable) {
                            DiagnosticsLog.logThrowable(
                                applicationContext,
                                "facebookLaunchNudge:$packageName u$userId",
                                error,
                            )
                        }
                    }
                }
            }, if (slow32Bit) 14_000L else 10_000L)
        }

        mainHandler.postDelayed({
            if (!isFinishing && !isDestroyed && hasWindowFocus()) {
                val processAlive: Boolean = if (facebook) {
                    DualCoreEngine(applicationContext).isCloneProcessAlive(packageName, userId)
                } else {
                    false
                }
                if (processAlive) {
                    progressView.visibility = View.VISIBLE
                    statusView.text = "$labelToLaunch is still starting"
                    detailView.text = "The clone process is active. Waiting for its first screen…"
                    // A live Facebook cold start is not a failed launch. Offer Retry only after an
                    // additional grace window, and never clear or restart its data automatically.
                    mainHandler.postDelayed({
                        if (!isFinishing && !isDestroyed && hasWindowFocus()) {
                            progressView.visibility = View.GONE
                            statusView.text = "$labelToLaunch is taking longer than expected"
                            detailView.text = "Retry sends the launcher again without resetting the clone."
                            actionsView.visibility = View.VISIBLE
                        }
                    }, 25_000L)
                } else {
                    progressView.visibility = View.GONE
                    statusView.text = "$labelToLaunch is taking longer than expected"
                    detailView.text = if (facebook) {
                        "Retry sends the launcher again without resetting the clone."
                    } else {
                        "You can retry without leaving Dual Space."
                    }
                    actionsView.visibility = View.VISIBLE
                }
            }
        }, finalTimeoutMs)
    }

    private fun showFailure(message: String) {
        launchRunning = false
        launchSubmitted = false
        progressView.visibility = View.GONE
        statusView.text = "Couldn’t open $labelToLaunch"
        detailView.text = message
        actionsView.visibility = View.VISIBLE
    }

    private fun buildContent(): View {
        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(8, 12, 35), Color.rgb(28, 17, 71), Color.rgb(7, 19, 42)),
            )
            setPadding(dp(28), dp(36), dp(28), dp(36))
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(34), dp(28), dp(30))
            background = roundedBackground(Color.argb(225, 19, 25, 55), 28f, Color.argb(90, 132, 95, 255))
            elevation = dp(16).toFloat()
        }

        val iconShell = FrameLayout(this).apply {
            background = roundedBackground(Color.argb(255, 50, 32, 112), 24f, Color.argb(150, 53, 209, 255))
            elevation = dp(10).toFloat()
        }
        val icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setImageDrawable(loadTargetIcon())
        }
        iconShell.addView(icon, FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER))
        panel.addView(iconShell, LinearLayout.LayoutParams(dp(104), dp(104)))

        panel.addView(space(dp(24)))

        statusView = TextView(this).apply {
            text = "Opening $labelToLaunch"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        panel.addView(statusView, matchWrap())

        panel.addView(space(dp(10)))

        detailView = TextView(this).apply {
            text = "Preparing your private space…"
            setTextColor(Color.rgb(190, 199, 230))
            textSize = 14.5f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.18f)
        }
        panel.addView(detailView, matchWrap())

        panel.addView(space(dp(26)))

        progressView = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(Color.rgb(82, 217, 255))
        }
        panel.addView(progressView, LinearLayout.LayoutParams(dp(42), dp(42)))

        panel.addView(space(dp(20)))

        val privacy = TextView(this).apply {
            text = "Isolated account • Protected app data"
            setTextColor(Color.rgb(130, 223, 255))
            textSize = 12.5f
            gravity = Gravity.CENTER
        }
        panel.addView(privacy, matchWrap())

        actionsView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val retry: Button = actionButton("Retry", primary = true)
        retry.setOnClickListener { beginLaunch() }
        val back: Button = actionButton("Back", primary = false)
        back.setOnClickListener { finish() }
        actionsView.addView(retry, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        actionsView.addView(back, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        panel.addView(space(dp(22)))
        panel.addView(actionsView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        return root
    }

    private fun actionButton(text: String, primary: Boolean): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        background = if (primary) {
            roundedBackground(Color.rgb(117, 70, 255), 16f, Color.rgb(65, 217, 255))
        } else {
            roundedBackground(Color.argb(180, 38, 44, 76), 16f, Color.argb(110, 148, 160, 205))
        }
    }

    private fun loadTargetIcon(): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageNameToLaunch)
        } catch (_: Throwable) {
            ContextCompat.getDrawable(this, R.drawable.dual_space_logo)
        }
    }

    private fun roundedBackground(fill: Int, radiusDp: Float, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun space(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun configureWindow(target: Window) {
        target.statusBarColor = Color.rgb(8, 12, 35)
        target.navigationBarColor = Color.rgb(7, 10, 28)
        target.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    companion object {
        const val ACTION_LAUNCH_CLONE = "com.example.dual.space.action.LAUNCH_CLONE"
        const val EXTRA_PACKAGE = "com.example.dual.space.extra.PACKAGE"
        const val EXTRA_USER_ID = "com.example.dual.space.extra.USER_ID"
        const val EXTRA_LABEL = "com.example.dual.space.extra.LABEL"
        const val EXTRA_FORCE_RESTART = "com.example.dual.space.extra.FORCE_RESTART"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val FACEBOOK_PACKAGE = "com.facebook.katana"
        private const val MESSENGER_PACKAGE = "com.facebook.orca"

        fun routeThroughHost(
            context: Context,
            packageName: String,
            userId: Int,
            label: String,
            forceRestart: Boolean = false,
        ) {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    action = ACTION_LAUNCH_CLONE
                    putExtra(EXTRA_PACKAGE, packageName)
                    putExtra(EXTRA_USER_ID, userId)
                    putExtra(EXTRA_LABEL, label)
                    putExtra(EXTRA_FORCE_RESTART, forceRestart)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                },
            )
        }

        fun start(
            context: Context,
            packageName: String,
            userId: Int,
            label: String,
            forceRestart: Boolean = false,
        ) {
            val launchIntent = Intent(context, CloneLaunchActivity::class.java).apply {
                action = ACTION_LAUNCH_CLONE
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_FORCE_RESTART, forceRestart)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
        }
    }
}
