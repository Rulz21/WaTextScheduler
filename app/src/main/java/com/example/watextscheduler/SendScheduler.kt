package com.example.watextscheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

private const val TAG = "WaSend"

object SendScheduler {
    const val EXTRA_ID = "id"
    private const val LATE_LIMIT_MS = 60 * 60_000L  // more than 1 hour late after a restart -> MISSED

    /** Has Android allowed exact alarms? */
    fun canSchedule(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, msg: ScheduledMessage): Boolean = scheduleAt(context, msg.id, msg.sendAt)

    fun scheduleAt(context: Context, id: Long, atMillis: Long): Boolean {
        if (!canSchedule(context)) return false
        val am = context.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), alarmIntent(context, id))
        Log.i(TAG, "Alarm set for message $id at $atMillis")
        return true
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, id))
        Log.i(TAG, "Alarm cancelled for message $id")
    }

    /** After a restart or reinstall: set the alarms again for every waiting message. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        for (m in MessageStore.all(context)) {
            if (m.status != Status.PENDING) continue
            when {
                m.sendAt > now -> schedule(context, m)                          // still in the future
                now - m.sendAt < LATE_LIMIT_MS -> scheduleAt(context, m.id, now + 30_000) // a bit late: send soon
                else -> MessageStore.setStatus(context, m.id, Status.MISSED)    // way too late: skip it
            }
        }
    }

    private fun alarmIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, SendAlarmReceiver::class.java)
            .setData(Uri.parse("watext://message/$id"))  // makes each message's alarm unique
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Android calls this when a message's alarm goes off. */
class SendAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(SendScheduler.EXTRA_ID, -1)
        val msg = MessageStore.get(context, id) ?: return
        if (msg.status != Status.PENDING) return

        // Another message is being sent right now? Try this one again in 30 seconds.
        if (PendingSend.isBusy()) {
            Log.i(TAG, "Busy – retrying message $id in 30s")
            SendScheduler.scheduleAt(context, id, System.currentTimeMillis() + 30_000)
            return
        }

        Log.i(TAG, "⏰ Alarm fired – sending message $id to ${msg.phone}")
        MessageStore.setStatus(context, id, Status.SENDING)
        val opened = WhatsAppLauncher.open(context, msg.phone, msg.text, autoSend = true, messageId = id)
        if (!opened) MessageStore.setStatus(context, id, Status.FAILED)
    }
}

/** Runs after the phone restarts, or after the app is reinstalled (every ▶). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.i(TAG, "Re-arming alarms after ${intent.action}")
            SendScheduler.rescheduleAll(context)
        }
    }
}