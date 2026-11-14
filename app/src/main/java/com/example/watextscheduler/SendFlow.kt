package com.example.watextscheduler

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

object SendFlow {
    private const val TAG = "WaSend"
    private const val CONFIRM_TIMEOUT_MS = 90_000L          // no "sent" after this -> FAILED
    private const val UNLOCK_NOTICE_MS = 40_000L            // still locked after this -> tell the user
    const val LATE_LIMIT_MS = 3 * 60 * 60_000L               // more than 3h late -> MISSED
    private val handler = Handler(Looper.getMainLooper())

    /** Called when a message's alarm goes off. */
    fun onAlarm(context: Context, msg: ScheduledMessage) {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val phoneInUse = power.isInteractive && !keyguard.isKeyguardLocked

        when {
            phoneInUse -> sendNow(context, msg)
            !keyguard.isKeyguardSecure || AutoUnlock.isReady(context) -> wakeAndUnlock(context, msg)
            else -> waitForUnlock(context, msg, "Auto-unlock is off", notify = true)
        }
    }

    /** Called when the phone gets unlocked (by you OR by auto-unlock) and when the service starts. */
    fun onUnlock(context: Context) {
        val now = System.currentTimeMillis()
        for (m in MessageStore.all(context)) {
            if (m.status != Status.WAITING_UNLOCK) continue
            if (now - m.sendAt > LATE_LIMIT_MS) {
                MessageStore.setStatus(context, m.id, Status.MISSED)
                Notifier.show(context, m.id, "⏭️ Message skipped", "It was too late to send your message to +${m.phone}.")
            } else {
                MessageStore.setStatus(context, m.id, Status.PENDING)
                SendScheduler.scheduleAt(context, m.id, now + 2_000)
            }
        }
    }

    fun relockIfWeUnlocked() {
        if (AutoUnlock.recentlyAutoUnlocked()) WaSendService.instance?.lockScreen()
    }

    /** Wake the screen; the service swipes up + types the PIN; onUnlock() then sends. */
    private fun wakeAndUnlock(context: Context, msg: ScheduledMessage) {
        if (PendingSend.isBusy() || AutoUnlock.inProgress()) {
            retryLater(context, msg)
            return
        }
        Log.i(TAG, "Phone locked – waking up for message ${msg.id}")
        waitForUnlock(context, msg, "Waiting for auto-unlock", notify = false)
        AutoUnlock.expectPin()
        context.startActivity(
            Intent(context, WakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        WaSendService.instance?.startUnlockLoop()

        // Still not unlocked after 40s? Let the user know it'll send when they unlock.
        val app = context.applicationContext
        handler.postDelayed({
            if (MessageStore.get(app, msg.id)?.status == Status.WAITING_UNLOCK) {
                Notifier.show(
                    app, msg.id, "🔒 Unlock to send",
                    "Couldn't unlock automatically. Your message to +${msg.phone} will send as soon as you unlock your phone."
                )
            }
        }, UNLOCK_NOTICE_MS)
    }

    private fun sendNow(context: Context, msg: ScheduledMessage) {
        if (PendingSend.isBusy()) {
            retryLater(context, msg)
            return
        }
        Log.i(TAG, "Sending message ${msg.id} to ${msg.phone}")
        MessageStore.setStatus(context, msg.id, Status.SENDING)
        val opened = WhatsAppLauncher.open(context, msg.phone, msg.text, autoSend = true, messageId = msg.id)
        if (!opened) {
            MessageStore.setStatus(context, msg.id, Status.FAILED)
            Notifier.show(context, msg.id, "❌ Not sent", "Couldn't open WhatsApp – is the sender service on?")
            relockIfWeUnlocked()
            return
        }
        startWatchdog(context, msg)
    }

    private fun waitForUnlock(context: Context, msg: ScheduledMessage, reason: String, notify: Boolean) {
        Log.i(TAG, "$reason – message ${msg.id} waits for unlock")
        MessageStore.setStatus(context, msg.id, Status.WAITING_UNLOCK)
        if (notify) {
            Notifier.show(
                context, msg.id, "🔒 Unlock to send",
                "$reason. Your message to +${msg.phone} will send as soon as you unlock your phone."
            )
        }
    }

    private fun retryLater(context: Context, msg: ScheduledMessage) {
        Log.i(TAG, "Busy – retrying message ${msg.id} in 30s")
        SendScheduler.scheduleAt(context, msg.id, System.currentTimeMillis() + 30_000)
    }

    /** Safety net: nothing confirmed "sent" in time -> mark failed and lock the phone again. */
    private fun startWatchdog(context: Context, msg: ScheduledMessage) {
        val app = context.applicationContext
        handler.postDelayed({
            if (MessageStore.get(app, msg.id)?.status == Status.SENDING) {
                Log.w(TAG, "Message ${msg.id} not confirmed in time")
                if (PendingSend.currentId() == msg.id) PendingSend.clear()
                MessageStore.setStatus(app, msg.id, Status.FAILED)
                Notifier.show(app, msg.id, "❌ Not sent", "Your message to +${msg.phone} didn't go through. Open the app to check.")
                relockIfWeUnlocked()
            }
        }, CONFIRM_TIMEOUT_MS)
    }
}