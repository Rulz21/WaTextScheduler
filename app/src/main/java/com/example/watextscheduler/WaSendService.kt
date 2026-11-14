package com.example.watextscheduler

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat

class WaSendService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var typingPin = false
    private var loopRunning = false
    private var swipes = 0
    private var lastSwipeAt = 0L

    /** Listens for "phone unlocked" and "screen turned on". */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    Log.i(TAG, "Phone unlocked")
                    SendFlow.onUnlock(applicationContext)
                }
                Intent.ACTION_SCREEN_ON -> watchForUnlock()
            }
        }
    }

    // Some phones (e.g. Honor) don't send USER_PRESENT, so after the screen turns on
    // we check every second for up to 60s whether the phone got unlocked.
    private var watchingUntil = 0L

    private fun watchForUnlock() {
        val alreadyWatching = SystemClock.elapsedRealtime() < watchingUntil
        watchingUntil = SystemClock.elapsedRealtime() + 60_000
        if (!alreadyWatching) handler.post { unlockWatchTick() }
    }

    private fun unlockWatchTick() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            watchingUntil = 0L
            Log.i(TAG, "Phone unlocked (detected)")
            SendFlow.onUnlock(applicationContext)
            return
        }
        if (SystemClock.elapsedRealtime() < watchingUntil) {
            handler.postDelayed({ unlockWatchTick() }, 1_000)
        }
    }

    override fun onServiceConnected() {
        instance = this
        Log.i(TAG, "Service connected")
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, unlockReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        SendFlow.onUnlock(applicationContext)
    }

    override fun onDestroy() {
        instance = null
        try { unregisterReceiver(unlockReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg in WA_PACKAGES) maybePressSend(pkg)
    }

    /** Lock the phone (Android 9+). */
    fun lockScreen() {
        AutoUnlock.clearAutoUnlocked()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            Log.i(TAG, "Locked again 🔒")
        }
    }

    // ================= WhatsApp: press Send =================

    private fun maybePressSend(pkg: String) {
        val pending = PendingSend.current() ?: return   // nothing waiting -> do nothing
        val root = rootInActiveWindow ?: return

        val box = root.findAccessibilityNodeInfosByViewId("$pkg:id/entry").firstOrNull()
        val boxText = box?.text?.toString()?.trim()
        if (boxText != pending.trim()) {
            Log.d(TAG, "Text box not ready / doesn't match: '$boxText'")
            return
        }

        val send = findSendButton(root, pkg)
        if (send == null) {
            Log.d(TAG, "Send button not found yet")
            return
        }
        if (clickNodeOrParent(send)) {
            val id = PendingSend.currentId()
            PendingSend.clear()
            Log.i(TAG, "Message sent ✅")

            if (id != null) {
                MessageStore.setStatus(this, id, Status.SENT)
                MessageStore.get(this, id)?.let {
                    Notifier.show(this, it.id, "✅ Sent", "To +${it.phone}: ${it.text}")
                }
                if (AutoUnlock.recentlyAutoUnlocked()) {
                    handler.postDelayed({ lockScreen() }, 1_500)
                } else {
                    handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 800)
                }
            }
        }
    }

    private fun findSendButton(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        root.findAccessibilityNodeInfosByViewId("$pkg:id/send")
            .firstOrNull { it.isVisibleToUser }
            ?.let { return it }
        for (label in SEND_LABELS) {
            root.findAccessibilityNodeInfosByText(label)
                .firstOrNull { it.contentDescription?.toString().equals(label, ignoreCase = true) }
                ?.let { return it }
        }
        return null
    }

    // ================= Lock screen: swipe up + type the PIN =================

    /** Called by SendFlow right after the screen is woken up. */
    fun startUnlockLoop() {
        if (loopRunning) return
        loopRunning = true
        swipes = 0
        lastSwipeAt = 0L
        Log.i(TAG, "Unlock loop started")
        handler.postDelayed({ unlockTick() }, 700) // give the screen a moment to turn on
    }

    private fun unlockTick() {
        if (typingPin) { loopRunning = false; return }
        if (!AutoUnlock.inProgress()) {
            Log.w(TAG, "PIN pad never appeared – giving up (no PIN was typed)")
            loopRunning = false
            return
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            Log.i(TAG, "Already unlocked")
            loopRunning = false
            return
        }

        // PIN pad visible? -> type the PIN
        if (findKey('5') != null) {
            loopRunning = false
            startTypingPin()
            return
        }

        // Otherwise swipe up to reveal it (max 3 times, 1.5s apart)
        val now = SystemClock.elapsedRealtime()
        if (swipes < 3 && now - lastSwipeAt > 1_500) {
            swipes++
            lastSwipeAt = now
            Log.i(TAG, "Swiping up to show the PIN pad (#$swipes)")
            swipeUp()
        }
        handler.postDelayed({ unlockTick() }, 400)
    }

    private fun startTypingPin() {
        if (!AutoUnlock.takePinRequest()) return          // ONE attempt only
        val pin = PinVault.load(this)
        if (pin.isNullOrEmpty()) {
            Log.w(TAG, "No PIN saved")
            return
        }
        typingPin = true
        Log.i(TAG, "Typing PIN (${pin.length} digits)")  // the PIN itself is never logged
        handler.postDelayed({ typeDigit(pin, 0) }, 300)
    }

    private fun typeDigit(pin: String, index: Int) {
        if (index == pin.length) {
            handler.postDelayed({
                pressEnterIfPresent()
                afterTyping()
            }, 300)
            return
        }
        val key = findKey(pin[index])
        if (key == null || !tap(key)) {
            Log.w(TAG, "Couldn't find/press PIN key #${index + 1} – dumping lock screen:")
            dumpLockScreen()
            typingPin = false
            AutoUnlock.onFailed(this)
            return
        }
        handler.postDelayed({ typeDigit(pin, index + 1) }, 200)
    }

    private fun pressEnterIfPresent() {
        for (root in lockScreenRoots()) {
            for (id in ENTER_IDS) {
                root.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isVisibleToUser }
                    ?.let { tap(it); return }
            }
            for (label in ENTER_LABELS) {
                root.findAccessibilityNodeInfosByText(label).firstOrNull {
                    it.isVisibleToUser && (
                            it.text?.toString().equals(label, ignoreCase = true) ||
                                    it.contentDescription?.toString().equals(label, ignoreCase = true))
                }?.let { tap(it); return }
            }
        }
    }

    private fun afterTyping() {
        typingPin = false
        AutoUnlock.markTyped()

        handler.postDelayed({
            val keyguard = getSystemService(KeyguardManager::class.java)
            if (keyguard.isKeyguardLocked) {
                Log.w(TAG, "Auto-unlock failed – still locked")
                AutoUnlock.clearAutoUnlocked()
                AutoUnlock.onFailed(this)
            } else {
                Log.i(TAG, "Auto-unlock OK 🔓")
                SendFlow.onUnlock(applicationContext) // don't wait for Android's unlock event
            }
        }, 3_000)

        // Hard safety limit: never leave the phone unlocked by us for more than 2 minutes.
        handler.postDelayed({ if (AutoUnlock.recentlyAutoUnlocked()) lockScreen() }, 120_000)
    }

    private fun swipeUp() {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val path = Path().apply {
            moveTo(x, dm.heightPixels * 0.80f)
            lineTo(x, dm.heightPixels * 0.30f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun lockScreenRoots(): List<AccessibilityNodeInfo> =
        windows.mapNotNull { it.root }
            .filter { it.packageName?.toString() in LOCK_SCREEN_PACKAGES }

    /** Finds the PIN pad key for one digit. */
    private fun findKey(digit: Char): AccessibilityNodeInfo? {
        val descPattern = Regex("^$digit([,\\s].*)?$")   // matches "2" or "2, ABC", not "15:39"
        for (root in lockScreenRoots()) {
            for (p in LOCK_SCREEN_PACKAGES) {
                root.findAccessibilityNodeInfosByViewId("$p:id/key$digit")
                    .firstOrNull { it.isVisibleToUser }
                    ?.let { return it }
            }
            root.findAccessibilityNodeInfosByText(digit.toString()).firstOrNull {
                it.isVisibleToUser && (
                        it.text?.toString()?.trim() == digit.toString() ||
                                it.contentDescription?.toString()?.trim()?.matches(descPattern) == true)
            }?.let { return it }
        }
        return null
    }

    /** Click a node; if that doesn't work, tap its position on screen. */
    private fun tap(node: AccessibilityNodeInfo): Boolean {
        if (clickNodeOrParent(node)) return true
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (r.isEmpty) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    // ================= Debug =================

    private fun dumpLockScreen() {
        lockScreenRoots().forEach { dumpTree(it) }
    }

    private fun dumpTree(node: AccessibilityNodeInfo?, depth: Int = 0) {
        if (node == null) return
        Log.d(TAG, "  ".repeat(depth) +
                "id=${node.viewIdResourceName} text='${node.text}' desc='${node.contentDescription}' click=${node.isClickable}")
        for (i in 0 until node.childCount) dumpTree(node.getChild(i), depth + 1)
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "WaSend"
        @Volatile var instance: WaSendService? = null

        val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        val LOCK_SCREEN_PACKAGES = setOf("com.android.systemui", "com.android.keyguard")
        val SEND_LABELS = listOf("Send", "Hantar")
        val ENTER_IDS = listOf("com.android.systemui:id/key_enter", "com.android.keyguard:id/key_enter")
        val ENTER_LABELS = listOf("OK", "Enter", "Done", "Masuk")
    }
}