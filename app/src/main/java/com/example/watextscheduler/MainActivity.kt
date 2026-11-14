package com.example.watextscheduler

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.watextscheduler.ui.theme.WaTextSchedulerTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaTextSchedulerTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    TestScreen(
                        onOpen = { phone, text -> openNow(phone, text, autoSend = false) },
                        onAutoSend = { phone, text -> openNow(phone, text, autoSend = true) },
                        onSchedule = ::scheduleAt,
                        onCancel = {
                            SendScheduler.cancel(this)
                            "Cancelled"
                        },
                        onOpenSettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    )
                }
            }
        }
    }

    private fun openNow(phone: String, text: String, autoSend: Boolean) {
        if (autoSend && !WhatsAppLauncher.isSenderEnabled(this)) {
            toast("Turn on 'WaTextScheduler sender' in Accessibility first")
            return
        }
        if (!WhatsAppLauncher.open(this, phone, text, autoSend)) toast("Couldn't open WhatsApp")
    }

    /** Sets the alarm and returns a status line to show on screen. */
    private fun scheduleAt(phone: String, text: String, atMillis: Long): String {
        if (!WhatsAppLauncher.isSenderEnabled(this)) {
            return "⚠️ Turn on 'WaTextScheduler sender' in Accessibility first"
        }
        if (!SendScheduler.schedule(this, phone, text, atMillis)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
            }
            return "⚠️ Allow 'Alarms & reminders' for this app, then try again"
        }
        val time = SimpleDateFormat("EEE, HH:mm", Locale.getDefault()).format(Date(atMillis))
        return "⏰ Scheduled for $time"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

@Composable
fun TestScreen(
    onOpen: (String, String) -> Unit,
    onAutoSend: (String, String) -> Unit,
    onSchedule: (String, String, Long) -> String,
    onCancel: () -> String,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("60") }
    var text by remember { mutableStateOf("Hello from my scheduler 👋") }
    var status by remember { mutableStateOf("") }

    Column(
        Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("WaTextScheduler", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = phone, onValueChange = { phone = it },
            label = { Text("Phone (with country code, e.g. 60123456789)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth()
        )

        // --- Scheduling (new in step 3) ---
        Button(
            onClick = { status = onSchedule(phone, text, System.currentTimeMillis() + 60_000) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Send in 1 minute (test)") }

        Button(
            onClick = {
                val now = Calendar.getInstance()
                TimePickerDialog(context, { _, hour, minute ->
                    val at = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        // time already passed today? -> tomorrow
                        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
                    }
                    status = onSchedule(phone, text, at.timeInMillis)
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Pick a time…") }

        OutlinedButton(onClick = { status = onCancel() }, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel scheduled message")
        }

        if (status.isNotEmpty()) Text(status)

        HorizontalDivider()

        // --- Testing tools from steps 1–2 ---
        OutlinedButton(onClick = { onOpen(phone, text) }, modifier = Modifier.fillMaxWidth()) {
            Text("Open in WhatsApp now (don't send)")
        }
        OutlinedButton(onClick = { onAutoSend(phone, text) }, modifier = Modifier.fillMaxWidth()) {
            Text("Send now (test)")
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Accessibility settings")
        }
    }
}