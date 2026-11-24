# SAVYOR ANDROID: FEASIBILITY AND TEST-READINESS (for Antigravity)

Read this whole file first. **Do not build the production Android app.** Your job is to (1) report the state of the paused work, (2) verify every item below with small isolated spikes and real measurements, (3) prove that you can build, install, run, and test an APK yourself, and (4) write a report. Then STOP and wait for my decision.

## What we want to build (context)
Savyor becomes a **phone-only Android app** (sideloaded APK, no server, no Play Store). The user shares an Instagram reel link to Savyor. The phone fetches the reel with yt-dlp (no login, no cookies; the user already does this successfully with the YTDLnis app), uploads it to Gemini with the user's own API key, receives structured notes, a timestamped transcript, key moments, and on-screen links, extracts a few key frames as small WebP snapshots, saves everything in a local database, and then **deletes the video**. Starred reels can be compressed and backed up to the user's Google Drive. All text, the search index, and thumbnails stay on the phone; snapshots live in a capped cache and are also copied to Drive so evicted ones can be re-fetched.
Hard goals: **very light on phone resources** (battery, RAM, storage, mobile data) and **fast retrieval**. User budget: the whole app plus 100 saved reels should stay around **200 MB**.
No manual per-reel steps are acceptable (no "download then upload a file", no screen recordings).

## Ground rules
1. Do not modify the existing web/Python app. Work only on a new branch `android-feasibility`, with spikes under `spikes/android/`, each spike in its own folder with a README (how to run, what it proves).
2. Never print or commit secrets (API keys, passcodes, keystores). Use a test Gemini key from an environment variable or `local.properties`, git-ignored. Never put a developer key inside an APK.
3. For every claim about a library, API, or platform behavior, check the **current official documentation or repository** and record the URL, version, and date. Mark each finding as VERIFIED (you ran it or read the source) or ASSUMED. Do not guess.
4. Be honest about what you could not do. Anything that needs the user's physical phone, accounts, or manual action goes in a "Human actions required" list with exact steps.
5. Commit after each spike. Keep every spike small and deletable.

---

## SECTION 0: Status of the paused work
- 0.1 Report which step of `SAVYOR_MASTER_PLAN.md` you were on, `git status`, `git log --oneline -10`, whether a database migration had started, and the last pytest and Playwright results.
- 0.2 Confirm `reels.db` and `media/` are intact (run `python scripts/backup.py` and verify the reel count). If the working tree is dirty or tests are red, say so and propose a safe way back; do not "fix forward" without telling me.

## SECTION 1: Can you build and run Android here?
For each item, run it and attach the evidence (command output, screenshot).
- 1.1 Detect OS, CPU, RAM, free disk space, and whether hardware virtualization is enabled (needed for a fast emulator). Report anything that blocks the emulator.
- 1.2 JDK (17+ as required by the current Android Gradle Plugin), Android SDK (command-line tools or Android Studio), platform-tools (`adb`), build-tools, and an emulator with a system image. Install what is missing (or list exactly what I must install). Verify with `sdkmanager --list_installed`, `adb version`, `java -version`.
- 1.3 Create an emulator (AVD): a recent Pixel profile on a current stable API level, plus a second AVD on a low API level that matches the minimum we plan to support (propose the minimum SDK and justify it with current Android distribution data). Boot it headless-capable, and confirm with `adb devices`.
- 1.4 Build a "hello world" debug APK from the command line (`./gradlew assembleDebug`), install it with `adb install`, launch it, and capture a screenshot with `adb exec-out screencap`. Save to `docs/android/evidence/`.
- 1.5 Confirm you can: start and stop the emulator, install and uninstall the APK, clear app data, send an Android intent with `adb shell am start`, read logs (`adb logcat`), record the screen (`adb shell screenrecord`), read the app's files (`adb shell run-as`), and query battery, memory, and storage (`dumpsys batterystats`, `dumpsys meminfo`, `du`).
- 1.6 Physical phone: describe exactly how I connect my phone (developer options, USB debugging, or wireless debugging) and test that `adb install` works on it if I plug it in. Ask me for the phone model and Android version if you need them; list OEM battery-manager risks for that brand.
- 1.7 Produce a Gradle wrapper build that works offline-after-first-sync and document the exact commands for debug build, release build, clean, and running tests.

## SECTION 2: Core feasibility spikes
For each, give: approach, library/version/license, result, measurements, risks, and a PASS / FAIL / NEEDS-HUMAN verdict.

### 2A. Fetching with yt-dlp on Android
- 2A.1 Compare the realistic ways to run yt-dlp inside an Android app: an existing Android wrapper library (research which are currently maintained, e.g. the library YTDLnis is built on; read YTDLnis's repository to learn how it receives shared links and downloads), Python embedded via Chaquopy, or other approaches. For each: maintenance status, license (flag GPL implications for a personal sideloaded app and for sharing the APK later), supported ABIs, bundled Python/ffmpeg, APK-size increase, update mechanism.
- 2A.2 Spike: a minimal app that downloads a public reel URL (no cookies, no login) to app-private cache at up to 720p. Measure: APK size delta, install size, download time, peak RAM, and result for each URL in a list I provide (ask me for 10 public reel links; do not commit them).
- 2A.3 In-app yt-dlp update: demonstrate updating the yt-dlp version at runtime, verify integrity (hash or signature), and define what happens when an update breaks extraction (rollback to the previous version).
- 2A.4 Failure handling: simulate and handle login-wall, rate-limit, private/unavailable, network timeout, and parse errors, with the categories I use in the existing code. Design the fetch ladder: yt-dlp, then the public embed page (caption, creator, thumbnail), then caption-only "basic notes", with later upgrade to full notes.
- 2A.5 Confirm no cookies, no account credentials, and no Instagram login are used anywhere.
- NEEDS-HUMAN: results from the emulator come from my home internet; real results on my phone's mobile data and Wi-Fi must be run by me. Provide a one-tap in-app "Fetch test" screen that runs the 10 URLs and shows pass/fail and error categories, and exports a diagnostics file with no secrets.

### 2B. Gemini analysis from the phone
- 2B.1 Check current Gemini API docs for: model names available to a standard free API key, Files API limits (size, retention, upload method), supported video formats, structured-output (JSON schema) support, rate limits and free-tier quotas, and pricing notes. Record exact versions and dates.
- 2B.2 Spike: from Android code (no server), upload a small test video with the Files API (resumable upload), wait until the file is ACTIVE, request structured JSON (summary, note_type, transcript with timestamps, key moments, on-screen text and URLs), then delete the uploaded file. Measure latency, bytes uploaded, peak RAM, and battery use for the call.
- 2B.3 Key storage: store the user's Gemini key with Android Keystore-backed encrypted storage; show it masked; never log it; verify it is not present in logs, backups, or the APK. Disable Android auto-backup of the key store contents (or exclude it).
- 2B.4 Handle 429 and quota errors with backoff and a clear user message; handle malformed JSON with one retry and validation; handle safety blocks.
- 2B.5 Two-pass link recovery: extract full-resolution frames at key-moment timestamps, send only those frames to a vision-capable model with the question "transcribe the exact text, URLs, handles, and prices visible", and verify GitHub links against the GitHub API (no auth, handle rate limits). Test it on a reel where a repository address appears on screen (I will provide one) and report accuracy.
- 2B.6 Confirm the download resolution (720p) is enough to read small on-screen text; if not, say what is needed.

### 2C. Frames and images
- 2C.1 Extract frames at exact timestamps using Android's media APIs (compare `MediaMetadataRetriever` closest-frame modes for accuracy), encode to WebP (thumbnail about 360 px, snapshots about 640 px), cap to 4 snapshots per reel. Measure time, RAM, and resulting file sizes. Confirm no ffmpeg is needed for this step.
- 2C.2 Eviction: implement a size-capped snapshot cache (default 100 MB) with least-recently-used eviction and re-fetch from the Drive copy; test it with synthetic data.

### 2D. Local database and search
- 2D.1 Choose the database layer (Room/SQLite via Kotlin, or a Capacitor SQLite plugin if the UI stays web-based) and justify it.
- 2D.2 Verify on every API level we plan to support that **FTS5** (or an acceptable alternative) is available at runtime (check `sqlite_compileoption_used` and a real `CREATE VIRTUAL TABLE`), including prefix matching, ranking, and quoted phrases. If the platform SQLite lacks FTS5, test a bundled SQLite and report its size cost.
- 2D.3 Performance test with 1,000 synthetic reels with realistic text (including Tamil, Hindi, and emoji): list query time, filtered query time, search time, database size, cold-open time. Targets: list under 50 ms, search under 100 ms, detail open under 150 ms on the emulator and on my phone.
- 2D.4 Schema design for phone-only use: reels, notes (by template), transcript segments, key moments, resources with link status, snapshots, categories, tags, status, fetch attempts, jobs, settings. Include migrations with tests (upgrade from every previous schema version).
- 2D.5 Import path: define an export file format from the existing PC app (JSON + images) and an importer, tested with my current reels.

### 2E. Sharing and background processing
- 2E.1 Share target: register an intent filter for `ACTION_SEND` with `text/plain`. Test with `adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "<share text>"` for: cold start, app already open, multiple quick shares, duplicate links, text containing extra words and tracking parameters, non-Instagram text (must be rejected politely). Validate and normalize URLs; do not trust intent data blindly.
- NEEDS-HUMAN: test the real Instagram app's share sheet on my phone and report what text Instagram actually sends.
- 2E.2 Background work: use the currently recommended Android mechanism for user-initiated long work (WorkManager with expedited work and/or a foreground service). Check the **current** Android 14, 15, and later rules for foreground-service types, time limits, notification permission (POST_NOTIFICATIONS), and battery restrictions, and design to those rules. One reel at a time, constraints configurable (network type, battery not low), retry with exponential backoff, resumable queue stored in the database.
- 2E.3 Prove survival: processing continues when the screen turns off, when the app is swiped away from recents, and across Doze (use `adb shell dumpsys deviceidle` to force it), and the queue resumes after a reboot or process death. Record results on the emulator. NEEDS-HUMAN: repeat on my phone with its OEM battery manager and tell me which settings I must change (provide a first-run guide screen).
- 2E.4 Idle behavior: with an empty queue the app must do nothing (no timers, no polling, no wake locks, no always-on notification). Verify with `dumpsys batterystats` and `dumpsys alarm`.

### 2F. Google Drive backup
- 2F.1 Check the **current** recommended way to sign in and authorize Drive access on Android, and choose the narrowest scope (app-created files only, or the hidden app-data folder). List exactly what I must create in Google Cloud Console (project, consent screen, Android OAuth client with the signing key's SHA-1) as numbered steps.
- 2F.2 Check how long authorizations last for an app in testing mode and what happens when they expire; design a quiet re-authorization flow.
- 2F.3 Spike (after I complete the manual setup): upload and download a 10 MB file and a database backup, resume an interrupted upload, and handle quota-full and offline errors. Run only when charging and on Wi-Fi (configurable).
- 2F.4 Compression for starred reels: evaluate the platform's hardware video encoding (Media3 Transformer or MediaCodec) at 480p H.264. Measure output size, time, and battery on a sample reel, and confirm it only runs on charging and Wi-Fi.

### 2G. UI approach and performance
- 2G.1 Decide the UI layer with a short matrix: (a) reuse the existing React/Tailwind interface in a Capacitor WebView with native plugins for fetching and background work, versus (b) a native Kotlin/Compose UI. Criteria: reuse of existing work, scroll performance, memory, startup time, installed size, testability, effort. Recommend one, and prove the recommendation with a spike: a list of 500 items with thumbnails, measuring cold-start time, scroll jank, and RAM.
- 2G.2 If the WebView route is chosen: disable the service worker in the native build, bundle the web assets inside the APK, lock down the WebView (no remote content, no file access, JavaScript interface limited and validated), and set a strict network security config (HTTPS only, release build with no cleartext).
- 2G.3 Accessibility and touch: minimum 44 px targets, safe-area handling, back-button behavior, light and dark themes.

### 2H. Security, privacy, legal notes
- 2H.1 Manifest review: minimal permissions (INTERNET, notifications, foreground service type as needed), `allowBackup` and data-extraction rules set so keys and the database are not leaked into backups, no unnecessary exported components, release build with minification and no debuggable flag.
- 2H.2 Signing: document how to create a release keystore, where to store it (outside the repo, backed up), and that losing it means reinstalling the app. Sideload update path with the same key.
- 2H.3 Record the license of every dependency and note obligations. Note that downloading Instagram content automatically goes against Instagram's terms even from a personal device at low volume, and list the mitigations we use (no login, one reel at a time, polite delays, user-initiated only).

## SECTION 3: Efficiency budgets (propose, measure, report)
Measure and report actual numbers against these targets. If a target is unrealistic, say why and propose a better one.
- 3.1 Storage: installed app size; data per reel (target 0.5 MB or less); 100 reels total; temp folder cap (50 MB); snapshot cache cap (100 MB); video file lifetime (deleted right after analysis succeeds or fails).
- 3.2 Compute and battery: battery cost per reel (use `dumpsys batterystats` before and after a batch of 20 reels with the emulator battery simulated, and on my phone with its battery stats screen); zero background activity when idle; no wake locks left held.
- 3.3 Memory: peak RAM while processing one reel and while browsing a 1,000-reel library.
- 3.4 Network: data used per reel (download plus upload); a Wi-Fi-only setting that really holds jobs until Wi-Fi.
- 3.5 Speed: cold start (target under 2 s on my phone), list render, search, detail open, time from share to "saved" for a typical 30-second reel.
- 3.6 Overall: the user's target is about 200 MB total for the app plus 100 reels. Report the actual breakdown.

## SECTION 4: Test strategy (you must be able to test the APK yourself)
Set this up now, with at least one passing example of each layer before the report.
- 4.1 **Unit tests (JVM)**: URL extraction and normalization (many share formats), fetch-ladder logic, Gemini response parsing and validation (including malformed output, bad timestamps, invalid URLs), link verification (including SSRF protections), snapshot cache eviction, database queries, schema migrations.
- 4.2 **Instrumented tests (emulator)**: database and FTS on real SQLite, WorkManager behavior (use test drivers), share-intent handling, file cleanup after jobs, cache caps, notification behavior.
- 4.3 **Deterministic end-to-end tests**: a `test` build flavor with a **fake fetcher** (returns a bundled sample video) and a **fake Gemini client** (returns fixture JSON, including failures), so UI flows run without network or quota. Choose and justify a UI automation tool (for example Maestro, or Espresso/UI Automator; evaluate Appium and Playwright's Android support if relevant), and show one passing flow end to end on the emulator: share a link, see it queued, see it processed, open the note, search for it, delete it.
- 4.4 **Live smoke tests**: a separate, clearly labeled set (real yt-dlp and real Gemini) that I run on my phone, with an in-app "Diagnostics" screen that shows results and exports a file (no secrets, no full reel URLs unless I allow it).
- 4.5 **Failure injection**: kill the process mid-job, drop the network, fill the storage, deny notification permission, revoke Drive authorization, return 429 from Gemini, return a corrupt video.
- 4.6 **Responsive and visual checks**: screenshots from the emulator at small, medium, and large phone sizes, light and dark themes, large font setting, and a check for clipped or overlapping elements; save under `docs/android/screenshots/`.
- 4.7 **Performance and battery measurement scripts**: a reproducible script that runs a batch of N synthetic reels and records time, RAM, CPU, and battery stats into a CSV.
- 4.8 **Test commands**: one command each for unit tests, instrumented tests, end-to-end tests, and the full suite, documented in `docs/android/TESTING.md`. Everything must run on this machine; list anything that cannot.
- 4.9 CI is optional; if you propose GitHub Actions, check the free-tier limits and keep emulator tests local.

## SECTION 5: Report (`docs/android/FEASIBILITY.md`)
Write a single report with:
1. A summary table: item ID, title, status (PASS / FAIL / BLOCKED / NEEDS-HUMAN), evidence link, measured numbers, risk, and decision needed.
2. The recommended architecture with the alternatives you rejected and why, including what to do if a spike failed.
3. A **Human actions required** list with exact numbered steps (phone setup, reel URLs, Google Cloud setup, Gemini test key, battery settings, real-device tests) and what to send back.
4. A measured budget table (Section 3) versus targets.
5. A migration plan from the existing Python/React app (what is reused, what is rewritten), a phased build plan with test gates, and effort estimates.
6. A list of everything ASSUMED rather than VERIFIED.
Then **stop**. Do not start building the production app until I reply.
