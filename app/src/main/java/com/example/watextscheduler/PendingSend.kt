package com.example.watextscheduler

import android.os.SystemClock

/** Holds ONE message that is waiting for the service to press Send. */
object PendingSend {
    private data class Job(val text: String, val messageId: Long?, val expiresAt: Long)

    @Volatile private var job: Job? = null

    fun set(text: String, messageId: Long? = null, timeoutMs: Long = 60_000) {
        job = Job(text, messageId, SystemClock.elapsedRealtime() + timeoutMs)
    }

    private fun active(): Job? {
        val j = job ?: return null
        if (SystemClock.elapsedRealtime() > j.expiresAt) {
            job = null
            return null
        }
        return j
    }

    /** The waiting message text, or null. */
    fun current(): String? = active()?.text

    /** Which saved message this is (null for quick tests). */
    fun currentId(): Long? = active()?.messageId

    /** Is something already waiting to be sent? */
    fun isBusy(): Boolean = active() != null

    fun clear() {
        job = null
    }
}