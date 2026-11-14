package com.example.watextscheduler

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WaSendService : AccessibilityService() {

    override fun onServiceConnected() {
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 1. Nothing waiting? Do nothing at all.
        val pending = PendingSend.current() ?: return

        // 2. Only care about WhatsApp.
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in WA_PACKAGES) return

        val root = rootInActiveWindow ?: return

        // 3. Safety: the text box must contain exactly our message.
        val box = root.findAccessibilityNodeInfosByViewId("$pkg:id/entry").firstOrNull()
        val boxText = box?.text?.toString()?.trim()
        if (boxText != pending.trim()) {
            Log.d(TAG, "Text box not ready / doesn't match: '$boxText'")
            return
        }

        // 4. Find the Send button and tap it.
        val send = findSendButton(root, pkg)
        if (send == null) {
            Log.d(TAG, "Send button not found yet")
            return
        }
        if (clickNodeOrParent(send)) {
            PendingSend.currentId()?.let { MessageStore.setStatus(this, it, Status.SENT) }
            PendingSend.clear()
            Log.i(TAG, "Message sent ✅")
        }
    }

    private fun findSendButton(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        // Try WhatsApp's internal button ID first
        root.findAccessibilityNodeInfosByViewId("$pkg:id/send")
            .firstOrNull { it.isVisibleToUser }
            ?.let { return it }

        // Fallback: find by its label (English / Malay)
        for (label in SEND_LABELS) {
            root.findAccessibilityNodeInfosByText(label)
                .firstOrNull { it.contentDescription?.toString().equals(label, ignoreCase = true) }
                ?.let { return it }
        }
        return null
    }

    /** Some buttons aren't clickable themselves, but their parent is. */
    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "WaSend"
        val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        val SEND_LABELS = listOf("Send", "Hantar")
    }
}