package com.chiefofstaff.intervention

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.chiefofstaff.ChiefOfStaffApp
import com.chiefofstaff.core.AppLog
import com.chiefofstaff.data.model.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ACC-04 — resolve a commitment straight from the notification shade, one tap. Confirming costs a
 * single gesture (P6): the action buttons on the evening-close notification broadcast here, the
 * verdict is applied through the state machine, and the notification is dismissed.
 */
class VerdictActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_VERDICT = "com.chiefofstaff.VERDICT"
        const val EXTRA_ID = "commitment_id"
        const val EXTRA_VERDICT = "verdict"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_VERDICT) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val verdict = intent.getStringExtra(EXTRA_VERDICT)
            ?.let { runCatching { Verdict.valueOf(it) }.getOrNull() } ?: return
        if (id <= 0) return

        val app = context.applicationContext as ChiefOfStaffApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.stateMachine.applyVerdict(id, verdict)
                AppLog.i("verdict", "applied $verdict to #$id from shade")
                NotificationManagerCompat.from(context).cancel((id % Int.MAX_VALUE).toInt())
            } finally {
                pending.finish()
            }
        }
    }
}
