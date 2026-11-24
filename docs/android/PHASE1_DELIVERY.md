# Savyor Android — Phase 1 (Core MVP) Delivery Report

## 1. Executive Summary
Phase 1 of the production Savyor Android app is built in `android/` on branch `android-mvp`. The app is a 100% native Kotlin + Jetpack Compose application targeting modern Android devices, specifically optimized for the **Samsung Galaxy S24 FE** (`arm64-v8a`, Android 17 / One UI 9) with `minSdk = 26`.

The Python/web desktop application remains untouched on `main`.

---

## 2. Architecture & Design Principles

### A. Zero Permanent Video Storage (0 MB Footprint)
- Video files downloaded for processing are stored in app cache and **guaranteed to be deleted in a `finally` block** upon job completion or failure.
- Storage stats in Settings dynamically report:
  - Notes & FTS Database: ~0.05 MB
  - Visual WebP Snapshots: ~0.02 MB – 0.15 MB per reel
  - Permanent Video Storage: **0.00 MB (Always 0 MB — Auto-cleaned)**

### B. Multilingual Understanding & Multimodal Fusion
- Powered by `gemini-3.5-flash` with structured JSON output and low temperature (0.2).
- Multimodal prompt combines video frames, audio track, and Instagram creator caption.
- **Rule of Language**: Regardless of source language (Tamil, Hindi, Spanish, Tanglish, Japanese, etc.):
  - `title`, `summary`, `key_takeaways`, `structured_content`, and visual moment descriptions are always synthesized into clear, concise, fluent **English**.
  - `transcript` provides an English timestamped transcript (`[0:00] ...`).
  - `original_transcript` preserves the verbatim native phonetics/text when the spoken language is not English.
- **SQLite FTS5 Full-Text Search**:
  - The Room FTS virtual table indexes `title`, `summary`, `transcript`, `original_transcript`, `tags`, `creator`, and `structured_content`.
  - Enables finding notes using English translations or native keywords (e.g., searching for either "mutton" or "chukka" surfaces the Madurai Kari Dosa reel).
- **Few-Shot Category Correction Learning**:
  - Captures the user's last 5 manual category reassignments in a local Room table (`category_corrections`).
  - Injects past corrections dynamically into subsequent Gemini prompts to personalize category classification.

### C. Keystore Security & Privacy
- Gemini API key is encrypted using Android Keystore `MasterKey` and stored in `EncryptedSharedPreferences`.
- Excluded from Android Auto Backup via `data_extraction_rules.xml` and `backup_rules.xml`.
- Key is masked in Settings UI (`AIzaSy...****`) and never logged.

### D. Background Reliability & Samsung One UI Optimization
- Background analysis runs via `WorkManager` with `ExistingWorkPolicy.APPEND_OR_REPLACE` to process reels sequentially without saturating mobile RAM or network bandwidth.
- Transparent `ShareReceiverActivity` accepts Instagram URLs via standard Android Share Sheet (`ACTION_SEND`), provides immediate visual feedback (Toast), enqueues background processing, and finishes cleanly in <100ms.
- Settings includes a direct 1-tap deep link to Samsung One UI App Battery settings with explicit instructions to set battery usage to "Unrestricted" so WorkManager jobs complete when the screen is turned off.

---

## 3. Real Performance & Footprint Metrics

All measurements taken on Android emulator (`emulator-5554`, Android 14 x86_64 / arm64-v8a ABI):

| Metric | Measured Value | Target / Limit | Status |
| :--- | :--- | :--- | :--- |
| **arm64-v8a APK Size** | **36.0 MB** (37,796,061 bytes) | < 45 MB | **PASS** |
| **x86_64 APK Size** | **36.0 MB** (37,829,768 bytes) | < 45 MB | **PASS** |
| **Peak RAM Usage (PSS)** | **110.6 MB** (113,311 kB) | < 200 MB | **PASS** |
| **Permanent Video Storage**| **0.00 MB** | 0 MB | **PASS** |
| **JVM Unit Test Pass Rate**| **100% (3/3 passed)** | 100% | **PASS** |
| **Android Instrumented E2E**| **100% (2/2 passed)** | 100% | **PASS** |

---

## 4. Test Suite Summary

### JVM Unit Tests (`android/app/src/test/java/com/savyor/app/`)
1. `GeminiResponseParsingTest`:
   - Validates JSON deserialization of multilingual Tamil reel response with English notes, timestamped English transcript, and native Tamil `original_transcript`.
   - Validates code snippet extraction and structured fields.
2. `UrlNormalizationTest`:
   - Validates shortcode extraction from standard URLs (`/reel/XYZ/`), share sheet URLs with tracking parameters, and rejection of invalid domains.
3. `GeminiPromptBuilderTest`:
   - Validates strict English output instructions, anti-clickbait guidance, verbatim recipe/code formatting rules, and injection of past category corrections.

### Android Instrumented Tests (`android/app/src/androidTest/java/com/savyor/app/`)
1. `SavyorE2ETest`:
   - End-to-end integration test with mock pipeline dependencies.
   - Enqueues reel, executes `ProcessReelWorker`, validates Room database state, verifies FTS5 search (matches both English query "Madurai" and original Tamil phonetic "chuka"), verifies temporary `.mp4` deletion (`exists() == false`), updates category (verifying correction entry created), updates status, and verifies reel deletion + snapshot file cleanup.
2. `SeedDataTest`:
   - Seeds rich sample reels and generates visual WebP key-moment snapshots into the app's internal sandbox.

---

## 5. UI & Theme Screenshots

All screenshots captured from live Compose UI in both Light (`#FAFAF8`) and Dark (`#121413`) modes:

| Screen | Light Theme | Dark Theme |
| :--- | :--- | :--- |
| **Library Screen** | [library_light.png](file:///c:/Users/kabil/OneDrive/Documents/Insta%20Saver/docs/android/screenshots/library_light.png) | [library_dark.png](file:///c:/Users/kabil/OneDrive/Documents/Insta%20Saver/docs/android/screenshots/library_dark.png) |
| **Reel Detail Screen** | [detail_light.png](file:///c:/Users/kabil/OneDrive/Documents/Insta%20Saver/docs/android/screenshots/detail_light.png) | [detail_dark.png](file:///c:/Users/kabil/OneDrive/Documents/Insta%20Saver/docs/android/screenshots/detail_dark.png) |
| **Settings Screen** | [settings_light.png](file:///c:/Users/kabil/OneDrive/Documents/Insta%20Saver/docs/android/screenshots/settings_light.png) | [settings_dark.png](file:///c:/Users/kabil/OneDrive/Documents/Insta%20Saver/docs/android/screenshots/settings_dark.png) |

---

## 6. Directory Structure
```
android/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/savyor/app/
│       │   │   ├── SavyorApplication.kt
│       │   │   ├── data/
│       │   │   │   ├── local/ (Room entities, DAOs, SavyorDatabase, EncryptedSettingsManager)
│       │   │   │   └── remote/ (GeminiFilesClient, YtDlpEngine, FrameExtractor, PromptBuilder)
│       │   │   ├── domain/model/ (Domain entities and structured note models)
│       │   │   ├── ui/
│       │   │   │   ├── MainActivity.kt
│       │   │   │   ├── theme/ (Color, Theme, Type)
│       │   │   │   ├── screens/library/ (LibraryScreen, LibraryViewModel)
│       │   │   │   ├── screens/detail/ (ReelDetailScreen, ReelDetailViewModel)
│       │   │   │   └── screens/settings/ (SettingsScreen, SettingsViewModel)
│       │   │   └── work/ (ProcessReelWorker, WorkManagerScheduler, ShareReceiverActivity)
│       │   └── res/ (drawable, mipmap, values, xml backup & network security rules)
│       ├── test/ (JVM Unit Tests)
│       └── androidTest/ (Instrumented E2E & Seed Tests)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew / gradlew.bat
```
