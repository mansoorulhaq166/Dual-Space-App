package com.example.dual.space

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.dual.space.engine.DualCoreEngine
import com.example.dual.space.ui.DualSpaceApp
import com.example.dual.space.ui.MainViewModel
import com.example.dual.space.ui.theme.DualSpaceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(DualCoreEngine(applicationContext), applicationContext) as T
        }
    }

    /** SMS + call-log so cloned Telegram (etc.) can auto-verify; Phone alone is not enough. */
    private val requestVerifyPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* user can re-open Dual Space settings if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DualSpaceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DualSpaceApp(viewModel)
                }
            }
        }
        if (savedInstanceState == null) handleLaunchIntent(intent)
        maybeRequestVerifyPermissions()
    }

    private fun maybeRequestVerifyPermissions() {
        val missing = VERIFY_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestVerifyPermissions.launch(missing.toTypedArray())
        }
    }

    companion object {
        private val VERIFY_PERMISSIONS = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.CALL_PHONE,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(source: Intent?) {
        if (source?.action != CloneLaunchActivity.ACTION_LAUNCH_CLONE) return
        val packageName = source.getStringExtra(CloneLaunchActivity.EXTRA_PACKAGE) ?: return
        val userId = source.getIntExtra(CloneLaunchActivity.EXTRA_USER_ID, 0)
        val label = source.getStringExtra(CloneLaunchActivity.EXTRA_LABEL) ?: packageName
        val forceRestart = source.getBooleanExtra(CloneLaunchActivity.EXTRA_FORCE_RESTART, false)
        // Post until MainActivity has entered RESUMED. This avoids background-launch denial on OEM
        // launchers and guarantees the host task is directly below the virtual app task.
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                CloneLaunchActivity.start(
                    context = this,
                    packageName = packageName,
                    userId = userId,
                    label = label,
                    forceRestart = forceRestart,
                )
            }
        }
        source.action = null
    }

    override fun onStop() {
        if (!isChangingConfigurations) viewModel.onHostBackgrounded()
        super.onStop()
    }
}
