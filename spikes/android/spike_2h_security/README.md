# Spike 2H: Security, Privacy, and Legal Analysis

## 1. Minimal Permissions Manifest

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Network access for yt-dlp, Gemini API, and Google Drive -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Foreground service and notifications for background reel processing (Android 13/14+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <!-- NO dangerous storage permissions needed (uses app-private filesDir and cacheDir) -->
    <!-- NO location, camera, or contact permissions -->
    <application
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:networkSecurityConfig="@xml/network_security_config"
        android:theme="@style/Theme.Savyor">
        ...
    </application>
</manifest>
```

---

## 2. Release Signing & Sideload Keystore

To create a permanent release signing key:
```bash
keytool -genkey -v -keystore savyor-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias savyor
```
- **Storage**: Must be stored safely on your PC and backed up outside the repository.
- **Sideload Rule**: Android requires all subsequent APK updates to be signed with the exact same keystore. If the keystore is lost, the existing app must be uninstalled (erasing local data unless exported first).

---

## 3. Legal & Terms of Service Mitigations

| Issue | Legal / Policy Context | Savyor Mitigation in Android App |
| :--- | :--- | :--- |
| **Instagram ToS** | Instagram prohibits automated downloading and scraping of content. | **Private sideloaded tool** (no Play Store distribution, personal archiving only). No Instagram login or cookies are ever used. Strict 1-at-a-time rate limiting with human delays. |
| **GPL / LGPL Licensing** | `yt-dlp` is unlicense/public domain; `youtubedl-android` is LGPL-3.0. | Sideloaded personal use is completely unencumbered. If open-sourced, dynamic linking via standard Android AAR complies with LGPL. |
| **User Privacy** | Instagram reels may contain personal or unlisted content. | Everything processed directly with user's own Gemini key. Zero third-party telemetry, trackers, or crash-reporting servers. |
