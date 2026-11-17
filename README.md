# WaTextScheduler

> Schedule WhatsApp messages on Android and have them sent automatically, even when your phone is locked.

An Android app that lets you write a WhatsApp message now and have it sent at a time you choose, from your own number, without touching your phone. It drives the **official WhatsApp app** on your device (no unofficial APIs, no servers), and can wake and unlock your phone with your PIN to send, then lock it again.

> ⚠️ **Unofficial project.** Not affiliated with, endorsed by, or sponsored by WhatsApp or Meta. It automates the WhatsApp app through Android's Accessibility service, which may go against WhatsApp's Terms of Service.

<!-- Add screenshots here, e.g.:
<p align="center">
  <img src="docs/home.png" width="250" />
  <img src="docs/notification.png" width="250" />
</p>
-->

---

## Features

- ⏰ **Exact-time scheduling** – pick a date and time; uses `AlarmManager.setAlarmClock()` so it fires on time, even in Doze
- 🔓 **Works while locked** – optional auto-unlock wakes the screen, enters your PIN, sends, then locks the phone again
- 📇 **Pick from contacts** – uses Android's contact picker (no contacts permission needed)
- 💬 **Pick from WhatsApp** – grab numbers you've chatted with but never saved
- 🔢 **Any number format** – `012-345 6789`, `+60 12-345 6789` and `0060…` are all normalised automatically
- 📋 **Message list** – see every scheduled message with its status (waiting, sent, failed, missed)
- 🔔 **Notifications** – "✅ Sent to Ali", "❌ Not sent", "🔒 Unlock to send"
- 🔁 **Survives restarts** – alarms are re-armed after a reboot or app update
- 🛡️ **Safety first** – only acts when a message is due, checks the text before sending, one PIN attempt only, always relocks


## Usage

1. Enter a number, or tap **📇 Contacts** / **💬 WhatsApp** to pick one.
2. Write your message.
3. Pick the date and time, then tap **Schedule**.
4. The message appears in the list as **⏰ Waiting** and changes to **✅ Sent** when done.

## Project structure

```
app/src/main/java/com/example/watextscheduler/
├── MainActivity.kt        # UI: form, message list, auto-unlock card (Jetpack Compose)
├── MessageStore.kt        # Scheduled messages (JSON in SharedPreferences)
├── SendScheduler.kt       # Exact alarms + alarm receiver + boot receiver
├── SendFlow.kt            # Decides: send now / wake & unlock / wait for unlock
├── WaSendService.kt       # Accessibility service: types PIN, taps Send, relocks
├── WakeActivity.kt        # Transparent activity that turns the screen on
├── AutoUnlock.kt          # PinVault (Keystore-encrypted PIN) + safety rules
├── WhatsAppLauncher.kt    # Opens wa.me links in WhatsApp
├── PendingSend.kt         # The one message currently waiting to be sent
├── ChatPicker.kt          # "Pick from WhatsApp" mode
├── Contacts.kt            # Contact picker + number normalisation
└── Notifier.kt            # Status notifications
```

**Tech:** Kotlin · Jetpack Compose · AccessibilityService · AlarmManager · Android Keystore

## Disclaimer

This is a personal, educational project.

- **Not affiliated with WhatsApp or Meta.** "WhatsApp" is a trademark of WhatsApp LLC / Meta Platforms, Inc., used here only to describe compatibility.
- It automates the official WhatsApp app via Android's Accessibility service. **This may violate WhatsApp's Terms of Service**, and WhatsApp may restrict or ban accounts that use automation. You are solely responsible for how you use it.
- **Do not use it for spam, bulk messaging or messaging people who haven't agreed to hear from you.**
- Storing your device PIN in any app carries risk. Auto-unlock is optional; only enable it if you understand the trade-off.
- Provided "as is", without warranty of any kind (see [LICENSE](LICENSE)).

## License

[MIT](LICENSE)
