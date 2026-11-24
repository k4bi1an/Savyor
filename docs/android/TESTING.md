# Savyor Android Test Harness & Commands

This guide documents the automated testing layers for the standalone Savyor Android app and runnable spikes. All test commands run on this host machine with the installed Android SDK and OpenJDK 17.

---

## 1. Environment Setup

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:PATH"
```

---

## 2. Spike 1: Native `youtubedl-android` Engine (`spikes/android/spike_ytdlp/`)

### A. Build Split APKs
```powershell
cd "c:\Users\kabil\OneDrive\Documents\Insta Saver\spikes\android"
.\gradlew.bat :spike_ytdlp:assembleDebug
```
- **Generated APKs**:
  - `spikes/android/spike_ytdlp/build/outputs/apk/debug/spike_ytdlp-arm64-v8a-debug.apk` (26.37 MB / 27,655,688 bytes)
  - `spikes/android/spike_ytdlp/build/outputs/apk/debug/spike_ytdlp-x86_64-debug.apk` (26.41 MB / 27,689,399 bytes)

### B. Sideload onto Samsung Galaxy S24 FE (`arm64-v8a`)
```powershell
adb install -r "spike_ytdlp\build\outputs\apk\debug\spike_ytdlp-arm64-v8a-debug.apk"
```

### C. Install & Run on Emulator (`x86_64`)
```powershell
adb install -r "spike_ytdlp\build\outputs\apk\debug\spike_ytdlp-x86_64-debug.apk"
adb shell am start -n com.savyor.spike.ytdlp/.MainActivity --ez run_default_test true
```

### D. Measure Memory and Storage via ADB
```powershell
# Peak RAM
adb shell dumpsys meminfo com.savyor.spike.ytdlp

# Installed Size on Disk
adb shell pm path com.savyor.spike.ytdlp
adb shell "run-as com.savyor.spike.ytdlp du -sh ."
```

---

## 3. Spike 2: Gemini Files API & Hardware WebP Extraction (`spikes/android/spike_gemini_frames/`)

### A. Build Split APKs
```powershell
cd "c:\Users\kabil\OneDrive\Documents\Insta Saver\spikes\android"
.\gradlew.bat :spike_gemini_frames:assembleDebug
```
- **Generated APK**: `spike_gemini_frames-x86_64-debug.apk` (8.10 MB / 8,498,620 bytes)

### B. Stage Key & Sample Video, Then Execute
```powershell
# Stage sample video to device
adb push "..\..\media\default_owner\videos\Df-OIaqgGZy.mp4" /data/local/tmp/sample.mp4

# Stage git-ignored API key (never bundled in APK)
$key = (Get-Content "local.properties" | Select-String "GEMINI_API_KEY=").Line.Replace("GEMINI_API_KEY=", "").Trim()
adb shell "echo '$key' > /data/local/tmp/gemini_key.txt"

# Install and run
adb install -r "spike_gemini_frames\build\outputs\apk\debug\spike_gemini_frames-x86_64-debug.apk"
adb shell am start -n com.savyor.spike.gemini/.MainActivity --ez run_auto_test true
```

### C. Verify Extracted WebP Files and Local Cleanup
```powershell
# Inspect WebP frames in app cache
adb shell "run-as com.savyor.spike.gemini ls -lh cache/extracted_frames"

# Confirm original MP4 was deleted
adb shell "run-as com.savyor.spike.gemini ls -lh cache/sample_test_reel.mp4"
```

---

## 4. Spike 3: Share Intent + WorkManager + Instrumented E2E Test (`spikes/android/spike_share_work/`)

### A. Run Instrumented Tests on Emulator (100% Offline / Zero Network)
```powershell
cd "c:\Users\kabil\OneDrive\Documents\Insta Saver\spikes\android"
.\gradlew.bat :spike_share_work:connectedDebugAndroidTest
```
- **Test Target**: `Spike3InstrumentedE2ETest`
- **Output Report**: `spikes/android/spike_share_work/build/reports/androidTests/connected/debug/index.html`

### B. Manual Share Intent Simulation via ADB
```powershell
# Install Spike 3
adb install -r "spike_share_work\build\outputs\apk\debug\spike_share_work-x86_64-debug.apk"

# Test 1: Share messy Instagram URL (Normalizes and Enqueues)
adb shell am start -n com.savyor.spike.share/.ShareReceiverActivity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'Check out this reel! https://www.instagram.com/reel/Df-OIaqgGZy/?igsh=xyz123 nice'"

# Test 2: Share non-Instagram URL (Rejects with clear message)
adb shell am start -n com.savyor.spike.share/.ShareReceiverActivity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'https://google.com/search?q=test'"
```

### C. Verify Doze & App Kill Survival
```powershell
# Enqueue job
adb shell am start -n com.savyor.spike.share/.ShareReceiverActivity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'https://www.instagram.com/reel/DfBEEFBo52M/'"

# Force stop app process
adb shell am force-stop com.savyor.spike.share

# Force device into deep Doze idle
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle step deep

# Confirm job is persisted in JobScheduler with foreground exemption
adb shell dumpsys jobscheduler | Select-String "com.savyor.spike.share" -Context 0, 5

# Exit Doze
adb shell dumpsys deviceidle unforce
```
