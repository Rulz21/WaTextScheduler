package com.example.watextscheduler

import android.os.SystemClock

/** "Pick a chat in WhatsApp" mode. Only active for 2 minutes after you tap the button. */
object ChatPicker {
    @Volatile private var activeUntil = 0L
    @Volatile private var result: Pair<String?, String>? = null   // (saved name, number)

    fun start() {
        result = null
        activeUntil = SystemClock.elapsedRealtime() + 120_000
    }

    fun isActive() = SystemClock.elapsedRealtime() < activeUntil

    fun finish(name: String?, number: String) {
        result = name to number
        activeUntil = 0L
    }

    /** Returns the picked chat once, then forgets it. */
    fun take(): Pair<String?, String>? = result.also { result = null }
}