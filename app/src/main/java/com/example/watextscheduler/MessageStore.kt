package com.example.watextscheduler

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class Status { PENDING, WAITING_UNLOCK, SENDING, SENT, FAILED, MISSED }

data class ScheduledMessage(
    val id: Long,
    val phone: String,      // digits only, e.g. "60123456789"
    val text: String,
    val sendAt: Long,       // time in milliseconds
    val status: Status = Status.PENDING,
    val name: String? = null,   // contact name, if picked from contacts
) {
    /** "Ali (+60123456789)" or just "+60123456789" */
    val recipientLabel: String
        get() = if (name.isNullOrBlank()) "+$phone" else "$name (+$phone)"

    /** "Ali" or "+60123456789" – for notifications */
    val shortLabel: String
        get() = if (name.isNullOrBlank()) "+$phone" else name
}

/** Saves the list of scheduled messages as JSON inside the app's private storage. */
object MessageStore {
    private const val PREFS = "messages"
    private const val KEY = "list"

    @Synchronized
    fun all(context: Context): List<ScheduledMessage> {
        val arr = JSONArray(prefs(context).getString(KEY, "[]") ?: "[]")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ScheduledMessage(
                id = o.getLong("id"),
                phone = o.getString("phone"),
                text = o.getString("text"),
                sendAt = o.getLong("sendAt"),
                status = Status.valueOf(o.getString("status")),
                name = o.optString("name").ifBlank { null },
            )
        }.sortedBy { it.sendAt }
    }

    fun get(context: Context, id: Long): ScheduledMessage? = all(context).firstOrNull { it.id == id }

    @Synchronized
    fun add(context: Context, phone: String, text: String, sendAt: Long, name: String? = null): ScheduledMessage {
        val msg = ScheduledMessage(
            id = System.currentTimeMillis(), phone = phone, text = text, sendAt = sendAt, name = name
        )
        save(context, all(context) + msg)
        return msg
    }

    @Synchronized
    fun setStatus(context: Context, id: Long, status: Status) {
        save(context, all(context).map { if (it.id == id) it.copy(status = status) else it })
    }

    @Synchronized
    fun remove(context: Context, id: Long) {
        save(context, all(context).filterNot { it.id == id })
    }

    private fun save(context: Context, list: List<ScheduledMessage>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("phone", m.phone)
                    .put("text", m.text)
                    .put("sendAt", m.sendAt)
                    .put("status", m.status.name)
                    .put("name", m.name ?: "")
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}