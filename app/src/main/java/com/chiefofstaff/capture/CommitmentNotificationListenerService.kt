package com.chiefofstaff.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.chiefofstaff.ChiefOfStaffApp
import com.chiefofstaff.core.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * CAP-06 — commitment extraction from message notifications. Half of working life arrives as
 * "can you send me X by Friday" in a chat; this captures that text passively (P1) so the extraction
 * pipeline can turn it into a commitment or waiting-on without the user retyping it.
 *
 * It is deliberately conservative: only genuine messaging notifications, only from a small allow-list
 * of messaging apps, de-duplicated so a chat that keeps updating one notification is captured once.
 * The capture itself is silent and immutable; whether anything actionable exists is the LLM's call,
 * not the listener's — and captures never cost a notification against the budget.
 */
class CommitmentNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Small allow-list of messaging/email packages. Extend from a real usage log later.
    private val messagingPackages = setOf(
        "com.whatsapp",
        "com.google.android.apps.messaging",
        "org.telegram.messenger",
        "com.slack",
        "com.google.android.gm",
        "com.microsoft.teams",
    )

    // DOM-12 — payment / UPI / bank apps. Their transaction notifications are parsed for spend.
    private val paymentPackages = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",                     // BHIM
        "com.csam.icici.bank.imobile",
        "com.sbi.lotusintouch",
        "com.snapwork.hdfc",
        "com.axis.mobile",
    )

    // Bounded de-dupe of recently seen (package|text) keys.
    private val recentlySeen = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 200
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val isMessaging = pkg in messagingPackages
        val isPayment = pkg in paymentPackages
        if (!isMessaging && !isPayment) return

        val n = sbn.notification ?: return
        // Skip group summaries and ongoing/service notifications.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        // Messaging must be a message; payment notifications carry other categories, so only gate messaging.
        if (isMessaging && n.category != null && n.category != Notification.CATEGORY_MESSAGE) return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (text.isBlank()) return

        val key = "$pkg|$title|$text"
        synchronized(recentlySeen) {
            if (recentlySeen.put(key, true) != null) return
        }

        val composed = if (title.isNotBlank()) "$title: $text" else text

        // DOM-12 — a payment notification becomes a spend directly, never a commitment.
        if (isPayment) {
            val spend = PaymentParser.parse(composed) ?: return
            AppLog.d("notiflistener", "spend ₹${spend.amount} (${spend.category}) from $pkg")
            scope.launch {
                runCatching {
                    (applicationContext as ChiefOfStaffApp).container.captureManager.recordSpend(composed, spend)
                }.onFailure { AppLog.w("notiflistener", "spend capture failed", it) }
            }
            return
        }
        AppLog.d("notiflistener", "captured message from $pkg (${composed.length} chars)")
        scope.launch {
            runCatching {
                (applicationContext as ChiefOfStaffApp).container.captureManager.captureNotification(composed)
            }.onFailure { AppLog.w("notiflistener", "capture failed", it) }
        }
    }

    // We only read; no action on removal.
    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit
}
