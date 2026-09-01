package com.example.dual.space

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Compatibility trampoline for shortcuts pinned by versions before 3.1.4.
 * New shortcuts target MainActivity directly, but old launcher entries must continue to work.
 */
class ShortcutLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent?.getStringExtra(EXTRA_PACKAGE)
        val userId = intent?.getIntExtra(EXTRA_USER_ID, 0) ?: 0
        if (!pkg.isNullOrBlank()) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = CloneLaunchActivity.ACTION_LAUNCH_CLONE
                    putExtra(CloneLaunchActivity.EXTRA_PACKAGE, pkg)
                    putExtra(CloneLaunchActivity.EXTRA_USER_ID, userId)
                    putExtra(CloneLaunchActivity.EXTRA_LABEL, pkg)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
            )
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_PACKAGE = "com.example.dual.space.extra.PACKAGE"
        const val EXTRA_USER_ID = "com.example.dual.space.extra.USER_ID"
    }
}
