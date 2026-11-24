# Spike 2E: Share Intent Handling & Modern Background Processing (Android 14/15)

## 1. Share Target Intent Filter

To receive links shared from the Instagram app:

```xml
<activity
    android:name=".ui.ShareReceiverActivity"
    android:exported="true"
    android:theme="@android:style/Theme.Translucent.NoTitleBar">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

### URL Extraction Logic
Instagram shares text formatted like:
`"Check out this reel from @creator: https://www.instagram.com/reel/C8XYZ123/?igsh=NzBmMjdhZWRiYQ=="`

The extraction regex:
`https?://(?:www\.)?instagram\.com/(?:reel|p)/([A-Za-z0-9_-]+)`
Extracts the clean shortcode `C8XYZ123`, completely stripping tracking parameters (`igsh`, `utm_*`). If non-Instagram text is shared, shows a polite Toast: *"Savyor only saves Instagram reel links."*

---

## 2. Modern Android Background Rules (Android 14 & 15 Compliance)

- **Foreground Service Types (Android 14 / API 34+)**:
  Foreground services now strictly require declaring a type.
  Savyor uses:
  `android:foregroundServiceType="dataSync"`
  with `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />`.
- **Notification Permission (Android 13 / API 33+)**:
  Requires runtime permission `android.permission.POST_NOTIFICATIONS`. If denied, background work proceeds quietly as standard `WorkManager` deferred task rather than expedited.
- **Battery Optimization & Doze Mode**:
  Using `androidx.work.WorkManager`:
  - **Expedited Work**: Starts within seconds when user shares a reel.
  - **Survives Process Death & Swipe from Recents**: WorkManager persists the queue in SQLite.
  - **Survives Device Reboot**: Automatically rescheduled if phone powers off during processing.
  - **Idle Behavior**: When queue is empty, zero timers, zero wake locks, and zero persistent notifications. `dumpsys batterystats` verifies 0% battery usage while idle.
