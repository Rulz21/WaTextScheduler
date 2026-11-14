package com.example.watextscheduler

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import com.example.watextscheduler.ui.theme.WaTextSchedulerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaTextSchedulerTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    TestScreen(
                        onOpen = { phone, text -> openWhatsApp(phone, text, autoSend = false) },
                        onAutoSend = { phone, text -> openWhatsApp(phone, text, autoSend = true) },
                        onOpenSettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    )
                }
            }
        }
    }

    private fun openWhatsApp(phone: String, text: String, autoSend: Boolean) {
        if (autoSend) {
            if (!isSenderEnabled()) {
                Toast.makeText(this, "Turn on 'WaTextScheduler sender' in Accessibility first", Toast.LENGTH_LONG).show()
                return
            }
            PendingSend.set(text) // tell the service: "press Send for this message"
        }

        val number = phone.filter { it.isDigit() }          // "+60 12-345 6789" -> "60123456789"
        val uri = Uri.parse("https://wa.me/$number?text=${Uri.encode(text)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")                         // WhatsApp Business = "com.whatsapp.w4b"
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            PendingSend.clear()
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }

    /** Is our Accessibility service switched on in Settings? */
    private fun isSenderEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, WaSendService::class.java)
        return enabled.split(':').any {
            it.equals(me.flattenToString(), ignoreCase = true) ||
                    it.equals(me.flattenToShortString(), ignoreCase = true)
        }
    }
}

@Composable
fun TestScreen(
    onOpen: (String, String) -> Unit,
    onAutoSend: (String, String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var phone by remember { mutableStateOf("60") }
    var text by remember { mutableStateOf("Hello from my scheduler 👋") }

    Column(
        Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("WaTextScheduler – test", style = MaterialTheme.typography.titleLarge)
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
        Button(onClick = { onOpen(phone, text) }, modifier = Modifier.fillMaxWidth()) {
            Text("Open in WhatsApp (don't send)")
        }
        Button(onClick = { onAutoSend(phone, text) }, modifier = Modifier.fillMaxWidth()) {
            Text("Open & auto-send (test)")
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Accessibility settings")
        }
    }
}