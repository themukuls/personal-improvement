package com.chiefofstaff

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chiefofstaff.intervention.RitualForegroundService
import com.chiefofstaff.ui.CosApp
import kotlinx.coroutines.launch

/**
 * The single Activity. Renders [CosApp] (all four screens live inside it), requests the runtime
 * permissions the capture + ritual surfaces need, starts the foreground watchdog (SYS-08), and
 * routes the Quick Settings / share / assistant capture intents into the pipeline.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_QUICK_CAPTURE = "com.chiefofstaff.QUICK_CAPTURE"
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions are best-effort; the app degrades gracefully without them (P11). */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ChiefOfStaffApp
        val startInCapture = intent?.action == ACTION_QUICK_CAPTURE
        handleShare(intent)

        setContent { CosApp(app.container, startInCapture = startInCapture) }

        requestRuntimePermissions()
        runCatching { RitualForegroundService.start(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** CAP-18 — share-sheet capture from any app. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val app = application as ChiefOfStaffApp
            lifecycleScope.launch { app.container.captureManager.captureShared(text) }
        }
    }

    private fun requestRuntimePermissions() {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_CALENDAR)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isNotEmpty()) requestPermissions.launch(wanted.toTypedArray())
    }
}
