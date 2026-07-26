package com.chiefofstaff

import android.app.backup.BackupManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.chiefofstaff.intervention.RitualForegroundService
import com.chiefofstaff.system.Permissions
import com.chiefofstaff.ui.CosApp
import com.chiefofstaff.ui.screens.OnboardingScreen
import kotlinx.coroutines.launch

/**
 * The single Activity. On first run it shows the [OnboardingScreen] — a consent gate that, once the
 * user says Yes, requests the runtime permissions and walks them into each special-access Settings
 * panel. After onboarding (or a skip) it renders [CosApp] (all four screens), starts the foreground
 * watchdog (SYS-08), and routes the Quick Settings / share / assistant capture intents.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_QUICK_CAPTURE = "com.chiefofstaff.QUICK_CAPTURE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app commits to a light scheme, so force dark system-bar icons (time, battery, gesture
        // pill) for legibility even when the phone itself is in dark mode. Transparent scrims keep
        // the bars edge-to-edge; the screens pad for the insets themselves.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        val app = application as ChiefOfStaffApp
        val startInCapture = intent?.action == ACTION_QUICK_CAPTURE
        handleShare(intent)

        setContent {
            // Onboarding owns the first permission request, so nothing is prompted until the user
            // opts in. Once complete (or skipped) we never gate again.
            var onboarded by remember { mutableStateOf(Permissions.isOnboarded(this)) }
            if (onboarded) {
                CosApp(app.container, startInCapture = startInCapture)
            } else {
                OnboardingScreen(onEnter = {
                    Permissions.markOnboarded(this)
                    onboarded = true
                })
            }
        }

        runCatching { RitualForegroundService.start(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /**
     * Whenever the user leaves the app, tell the OS its data changed so a fresh Auto Backup is
     * scheduled soon (rather than waiting for the once-a-day default). Fully automatic — the system
     * still runs it later under its own conditions (idle, charging, unmetered). Zero user action.
     */
    override fun onStop() {
        super.onStop()
        runCatching { BackupManager(this).dataChanged() }
    }

    /** CAP-18 — share-sheet capture from any app. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val app = application as ChiefOfStaffApp
            lifecycleScope.launch { app.container.captureManager.captureShared(text) }
        }
    }
}
