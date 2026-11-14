package com.example.watextscheduler

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

object WhatsAppLauncher {
    private const val TAG = "WaSend"

    /** Opens WhatsApp on the chat with `phone`, `text` typed in. Returns false if it couldn't. */
    fun open(context: Context, phone: String, text: String, autoSend: Boolean): Boolean {
        if (autoSend) {
            if (!isSenderEnabled(context)) {
                Log.w(TAG, "Sender service is OFF – can't auto-send")
                return false
            }
            PendingSend.set(text) // tell the service: "press Send for this message"
        }

        val number = phone.filter { it.isDigit() }          // "+60 12-345 6789" -> "60123456789"
        val uri = Uri.parse("https://wa.me/$number?text=${Uri.encode(text)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")                         // WhatsApp Business = "com.whatsapp.w4b"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)            // needed when opening from an alarm
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't open WhatsApp", e)
            PendingSend.clear()
            false
        }
    }

    /** Is our Accessibility service switched on in Settings? */
    fun isSenderEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(context, WaSendService::class.java)
        return enabled.split(':').any {
            it.equals(me.flattenToString(), ignoreCase = true) ||
                    it.equals(me.flattenToShortString(), ignoreCase = true)
        }
    }
}