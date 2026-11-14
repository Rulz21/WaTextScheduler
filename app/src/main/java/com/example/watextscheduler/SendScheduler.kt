package com.example.watextscheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object SendScheduler {
    private const val REQUEST_CODE = 1001   // one alarm for now; step 4 gives each message its own
    const val EXTRA_PHONE = "phone"
    const val EXTRA_TEXT = "text"

    /** Sets the alarm. Returns false if Android hasn't allowed exact alarms. */
    fun schedule(context: Context, phone: String, text: String, atMillis: Long): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return false

        val fire = alarmIntent(context, phone, text)
        // Tapping the alarm icon in the status bar opens our app
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), fire)
        Log.i("WaSend", "Alarm set for $atMillis")
        return true
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, "", ""))
        Log.i("WaSend", "Alarm cancelled")
    }

    private fun alarmIntent(context: Context, phone: String, text: String): PendingIntent {
        val intent = Intent(context, SendAlarmReceiver::class.java)
            .putExtra(EXTRA_PHONE, phone)
            .putExtra(EXTRA_TEXT, text)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** Android calls this when the alarm goes off. */
class SendAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra(SendScheduler.EXTRA_PHONE) ?: return
        val text = intent.getStringExtra(SendScheduler.EXTRA_TEXT) ?: return
        Log.i("WaSend", "⏰ Alarm fired – sending to $phone")
        WhatsAppLauncher.open(context, phone, text, autoSend = true)
    }
}