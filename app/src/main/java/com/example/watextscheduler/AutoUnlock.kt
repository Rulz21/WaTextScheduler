package com.example.watextscheduler

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores your lock-screen PIN encrypted with a key that never leaves the phone's Keystore. */
object PinVault {
    private const val KEY_ALIAS = "wa_pin_key"
    private const val PREFS = "vault"

    fun save(context: Context, pin: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(pin.toByteArray())
        prefs(context).edit()
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
    }

    fun load(context: Context): String? {
        return try {
            val p = prefs(context)
            val data = Base64.decode(p.getString("data", null) ?: return null, Base64.NO_WRAP)
            val iv = Base64.decode(p.getString("iv", null) ?: return null, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data))
        } catch (e: Exception) {
            null
        }
    }

    fun hasPin(context: Context) = prefs(context).contains("data")

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Safety rules for auto-unlocking. */
object AutoUnlock {
    private const val PREFS = "autounlock"
    private const val PIN_WINDOW_MS = 20_000L      // the PIN pad must appear within 20s
    private const val RECENT_MS = 3 * 60_000L

    @Volatile private var expectPinUntil = 0L
    @Volatile private var unlockedAt = 0L

    fun isDisabled(context: Context) = prefs(context).getBoolean("disabled", false)

    fun setDisabled(context: Context, disabled: Boolean) {
        prefs(context).edit().putBoolean("disabled", disabled).commit()
    }

    /** PIN saved, not turned off after a failure, and the sender service is running. */
    fun isReady(context: Context) =
        PinVault.hasPin(context) && !isDisabled(context) && WaSendService.instance != null

    fun expectPin() {
        expectPinUntil = SystemClock.elapsedRealtime() + PIN_WINDOW_MS
    }

    fun inProgress() = SystemClock.elapsedRealtime() < expectPinUntil

    /** True only ONCE per wake-up: the PIN is never typed twice. */
    fun takePinRequest(): Boolean {
        if (!inProgress()) return false
        expectPinUntil = 0L
        return true
    }

    fun markTyped() {
        unlockedAt = SystemClock.elapsedRealtime()
    }

    /** Did WE unlock the phone in the last few minutes? (then we must lock it again) */
    fun recentlyAutoUnlocked() =
        unlockedAt != 0L && SystemClock.elapsedRealtime() - unlockedAt < RECENT_MS

    fun clearAutoUnlocked() {
        unlockedAt = 0L
    }

    /** Wrong PIN or couldn't type it → turn auto-unlock off so we never try again blindly. */
    fun onFailed(context: Context) {
        setDisabled(context, true)
        expectPinUntil = 0L
        Notifier.show(
            context, 0, "🔐 Auto-unlock turned off",
            "The app couldn't unlock your phone (did you change your PIN?). Open the app and save your PIN again."
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}