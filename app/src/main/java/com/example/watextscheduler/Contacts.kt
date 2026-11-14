package com.example.watextscheduler

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

const val DEFAULT_COUNTRY_CODE = "60" // Malaysia

/**
 * Turns any phone format into WhatsApp format (country code + number, digits only):
 * "012-345 6789" -> "60123456789", "+60 12-345 6789" -> "60123456789", "0060123…" -> "60123…"
 */
fun normalizeNumber(raw: String): String {
    val trimmed = raw.trim()
    val digits = trimmed.filter { it.isDigit() }
    return when {
        trimmed.startsWith("+") -> digits
        digits.startsWith("00") -> digits.drop(2)
        digits.startsWith("0") -> DEFAULT_COUNTRY_CODE + digits.drop(1)
        else -> digits
    }
}

/** Reads the name + number of the contact the user picked. Returns null if it couldn't. */
fun readPickedContact(context: Context, uri: Uri): Pair<String, String>? {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
        if (c.moveToFirst()) return (c.getString(0) ?: "") to (c.getString(1) ?: "")
    }
    return null
}