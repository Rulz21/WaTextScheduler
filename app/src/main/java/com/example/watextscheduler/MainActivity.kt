package com.example.watextscheduler

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.watextscheduler.ui.theme.WaTextSchedulerTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var refreshTick by mutableIntStateOf(0)
    private var pickedChat by mutableStateOf<Pair<String?, String>?>(null)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            WaTextSchedulerTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    HomeScreen(
                        tick = refreshTick,
                        pickedChat = pickedChat,
                        onPickedChatUsed = { pickedChat = null },
                        onPickFromWhatsApp = ::pickFromWhatsApp,
                        onAdd = ::addMessage,
                        onDelete = ::deleteMessage,
                        onChanged = { refreshTick++ },
                        onOpenSettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ChatPicker.take()?.let { pickedChat = it }  // came back from "Pick from WhatsApp"?
        refreshTick++
    }

    private fun pickFromWhatsApp() {
        if (!WhatsAppLauncher.isSenderEnabled(this)) {
            toast("Turn on 'WaTextScheduler sender' in Accessibility first")
            return
        }
        val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        if (intent == null) {
            toast("WhatsApp not installed")
            return
        }
        ChatPicker.start()
        toast("Open the chat you want – you'll come back here automatically")
        startActivity(intent)
    }

    /** Returns an error message, or null if it worked. */
    private fun addMessage(phone: String, text: String, atMillis: Long, name: String?): String? {
        val number = normalizeNumber(phone)
        if (number.length < 10) return "Enter a valid number, e.g. 012-345 6789"
        if (text.isBlank()) return "Message is empty"
        if (atMillis <= System.currentTimeMillis()) return "That time has already passed"
        if (!SendScheduler.canSchedule(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
            }
            return "Allow 'Alarms & reminders' for this app, then try again"
        }
        val msg = MessageStore.add(this, number, text.trim(), atMillis, name)
        SendScheduler.schedule(this, msg)
        refreshTick++
        return null
    }

    private fun deleteMessage(id: Long) {
        SendScheduler.cancel(this, id)
        MessageStore.remove(this, id)
        refreshTick++
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

// ---------- Screen ----------

@Composable
fun HomeScreen(
    tick: Int,
    pickedChat: Pair<String?, String>?,
    onPickedChatUsed: () -> Unit,
    onPickFromWhatsApp: () -> Unit,
    onAdd: (String, String, Long, String?) -> String?,
    onDelete: (Long) -> Unit,
    onChanged: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val messages = remember(tick) { MessageStore.all(context) }
    val senderOn = remember(tick) { WhatsAppLauncher.isSenderEnabled(context) }

    var phone by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var sendAt by remember { mutableLongStateOf(defaultSendTime()) }
    var error by remember { mutableStateOf<String?>(null) }

    // Phone contacts picker
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (uri != null) {
            readPickedContact(context, uri)?.let { (name, number) ->
                phone = number
                contactName = name.ifBlank { null }
                error = null
            }
        }
    }

    // Result of "Pick from WhatsApp"
    LaunchedEffect(pickedChat) {
        val picked = pickedChat ?: return@LaunchedEffect
        if (picked.second.isNotBlank()) {
            phone = picked.second
            contactName = null
            error = null
        } else {
            error = "\"${picked.first}\" is a saved contact – use 📇 Contacts for them."
        }
        onPickedChatUsed()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("WaTextScheduler", style = MaterialTheme.typography.headlineSmall) }

        if (!senderOn) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Sender is OFF – messages won't send.")
                        Text(
                            "Turn on 'WaTextScheduler sender' in Accessibility.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onOpenSettings) { Text("Open Accessibility settings") }
                    }
                }
            }
        }

        item { AutoUnlockCard(tick, onChanged) }

        // --- New message form ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        contactName = null
                    },
                    label = { Text("Phone (e.g. 012-345 6789)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText = {
                        if (phone.isNotBlank()) {
                            val who = contactName?.let { "👤 $it · " } ?: ""
                            Text("${who}Will send to +${normalizeNumber(phone)}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            pickContact.launch(
                                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("📇 Contacts") }
                    OutlinedButton(onClick = onPickFromWhatsApp, modifier = Modifier.weight(1f)) {
                        Text("💬 WhatsApp")
                    }
                }
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("Message") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val c = Calendar.getInstance().apply { timeInMillis = sendAt }
                            DatePickerDialog(context, { _, y, m, d ->
                                sendAt = Calendar.getInstance().apply {
                                    timeInMillis = sendAt
                                    set(y, m, d)
                                }.timeInMillis
                            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                                .apply { datePicker.minDate = System.currentTimeMillis() - 1000 }
                                .show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("📅 " + format(sendAt, "EEE, d MMM")) }

                    OutlinedButton(
                        onClick = {
                            val c = Calendar.getInstance().apply { timeInMillis = sendAt }
                            TimePickerDialog(context, { _, h, min ->
                                sendAt = Calendar.getInstance().apply {
                                    timeInMillis = sendAt
                                    set(Calendar.HOUR_OF_DAY, h)
                                    set(Calendar.MINUTE, min)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("🕐 " + format(sendAt, "HH:mm")) }
                }
                TextButton(onClick = { sendAt = System.currentTimeMillis() + 60_000 }) {
                    Text("Set to 1 minute from now (test)")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        error = onAdd(phone, text, sendAt, contactName)
                        if (error == null) {
                            text = ""
                            sendAt = defaultSendTime()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Schedule") }
            }
        }

        // --- The list ---
        item {
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Scheduled (${messages.size})", style = MaterialTheme.typography.titleMedium)
        }
        if (messages.isEmpty()) {
            item { Text("Nothing scheduled yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(messages, key = { it.id }) { m -> MessageRow(m, onDelete) }
    }
}

@Composable
fun AutoUnlockCard(tick: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val hasPin = remember(tick) { PinVault.hasPin(context) }
    val disabled = remember(tick) { AutoUnlock.isDisabled(context) }
    var pin by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Auto-unlock", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !hasPin -> "OFF – messages wait until you unlock your phone."
                    disabled -> "⚠️ Turned off after a failed unlock. Save your PIN again."
                    else -> "🔓 ON – unlocks your phone to send, then locks it again."
                },
                style = MaterialTheme.typography.bodySmall
            )

            if (!hasPin || disabled || editing) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(16) },
                    label = { Text("Your lock-screen PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Must match your lock screen PIN exactly. If it's wrong, the app tries ONCE, then turns auto-unlock off.",
                    style = MaterialTheme.typography.labelSmall
                )
                Button(
                    enabled = pin.length >= 4,
                    onClick = {
                        PinVault.save(context, pin)
                        AutoUnlock.setDisabled(context, false)
                        pin = ""
                        editing = false
                        onChanged()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save PIN") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f)) {
                        Text("Change PIN")
                    }
                    OutlinedButton(
                        onClick = {
                            PinVault.clear(context)
                            AutoUnlock.setDisabled(context, false)
                            onChanged()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Turn off") }
                }
            }
        }
    }
}

@Composable
fun MessageRow(m: ScheduledMessage, onDelete: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(format(m.sendAt, "EEE, d MMM · HH:mm"), style = MaterialTheme.typography.titleSmall)
                Text("To ${m.recipientLabel}", style = MaterialTheme.typography.bodySmall)
                Text(m.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(statusLabel(m), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { onDelete(m.id) }) {
                Text(if (m.status == Status.PENDING) "Cancel" else "Delete")
            }
        }
    }
}

// ---------- Helpers ----------

fun statusLabel(m: ScheduledMessage): String = when (m.status) {
    Status.PENDING -> "⏰ Waiting"
    Status.WAITING_UNLOCK -> "🔒 Will send when you unlock"
    Status.SENDING -> "📤 Sending…"
    Status.SENT -> "✅ Sent"
    Status.FAILED -> "❌ Failed"
    Status.MISSED -> "⏭️ Missed (too late)"
}

fun format(millis: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))

fun defaultSendTime(): Long = Calendar.getInstance().apply {
    add(Calendar.HOUR_OF_DAY, 1)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis