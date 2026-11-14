package com.example.watextscheduler

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

/** Invisible screen that ONLY turns the display on, then closes so the real lock screen is reachable. */
class WakeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // Screen is on now. Close quickly so the service can swipe the real lock screen.
        window.decorView.postDelayed({ finish() }, 400)
    }

    companion object {
        const val EXTRA_ID = "id"
    }
}