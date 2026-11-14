package com.example.watextscheduler

import android.os.SystemClock

/** Holds ONE message that is waiting for the service to press Send. */
object PendingSend {
    private data class Job(val text: String, val expiresAt: Long)

    @Volatile private var job: Job? = null

    fun set(text: String, timeoutMs: Long = 30_000) {
        job = Job(text, SystemClock.elapsedRealtime() + timeoutMs)
    }

    /** The waiting message, or null if there is none (or it expired). */
    fun current(): String? {
        val j = job ?: return null
        if (SystemClock.elapsedRealtime() > j.expiresAt) {
            job = null
            return null
        }
        return j.text
    }

    fun clear() {
        job = null
    }
}