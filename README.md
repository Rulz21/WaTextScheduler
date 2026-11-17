# WaTextScheduler

> A little side project of mine: write a WhatsApp message now, and my phone sends it later, all by itself.

<!-- Add screenshots here, e.g.:
<p align="center">
  <img src="docs/home.png" width="250" />
  <img src="docs/notification.png" width="250" />
</p>
-->

## Why I built this

This keeps happening to me: I need to message someone, but **not right now**.

Sometimes they're busy, or it's too late at night to text them. Sometimes I'm the busy one, and I know exactly what I want to say but it's not the right moment to send it. So I tell myself *"I'll message them later"*…

…and then I forget. 

The usual fix is a reminder. But a reminder only tells me to do the thing. I still have to stop what I'm doing, open WhatsApp, find the chat and type the message. And if I'm busy when the reminder goes off, I snooze it and forget anyway.

So I thought: **why remind myself to send a message when my phone could just send it?**

That's what this app does. I write the message when I think of it, pick when it should go out, and forget about it. At that time, my phone sends it from my own WhatsApp, even if it's locked in my pocket or I'm asleep.

> ⚠️ This is an **unofficial** personal project. It's not affiliated with WhatsApp or Meta, and it automates the WhatsApp app, which may go against WhatsApp's Terms of Service.

---

## What it does

- ⏰ **Sends at the exact time I pick.** It uses Android's alarm clock alarms, so it fires on time even when the phone is idle.
- 🔓 **Works while my phone is locked.** It can wake the screen, enter my PIN, send, then lock the phone again. This is optional.
- 📇 **Picks from my contacts**, without the app needing access to my whole contact list.
- 💬 **Picks from WhatsApp**, for people I've chatted with but never saved.
- 🔢 **Accepts any number format**: `012-345 6789`, `+60 12-345 6789`, whatever I copy.
- 📋 **Keeps a list** of everything scheduled: waiting, sent, failed or missed.
- 🔔 **Tells me what happened**, e.g. "✅ Sent to Ali".
- 🔁 **Survives restarts**: scheduled messages are re-armed after a reboot or update.

## Built with

Kotlin · Jetpack Compose · AccessibilityService · AlarmManager · Android Keystore

```
app/src/main/java/com/example/watextscheduler/
├── MainActivity.kt        # The screen: form, message list, auto-unlock settings
├── MessageStore.kt        # Saves scheduled messages
├── SendScheduler.kt       # Alarms + reboot handling
├── SendFlow.kt            # Decides: send now / unlock first / wait for me
├── WaSendService.kt       # Accessibility service: enters PIN, taps Send, relocks
├── WakeActivity.kt        # Turns the screen on
├── AutoUnlock.kt          # Encrypted PIN storage + safety rules
├── WhatsAppLauncher.kt    # Opens the chat via wa.me
├── PendingSend.kt         # The message currently being sent
├── ChatPicker.kt          # "Pick from WhatsApp"
├── Contacts.kt            # Contact picker + number formatting
└── Notifier.kt            # Notifications
```

## Disclaimer

This is a personal side project I made for myself and to learn.

- **Not affiliated with, endorsed by, or sponsored by WhatsApp or Meta.** "WhatsApp" is their trademark and is only used here to describe what the app works with.
- It automates the official WhatsApp app through Android's Accessibility service. **This may break WhatsApp's Terms of Service**, and WhatsApp can restrict accounts that use automation. If you use it, that's your call and your responsibility.
- **Please don't use it for spam or bulk messages.** It's meant for the "I'll message them later" moments, not for messaging people who don't want to hear from you.
- Saving your phone's PIN in any app has risks. Auto-unlock is optional, so only turn it on if you're comfortable with that.
- Provided as is, with no warranty (see [LICENSE](LICENSE)).

## License

[MIT](LICENSE)
