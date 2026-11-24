# 🔖 Savyor — Save the reel. Keep the idea.

A calm, minimal personal knowledge library for Instagram Reels. It downloads video streams locally, transcribes audio and on-screen text with Google Gemini, extracts actionable takeaways and resources, and presents everything in a clean reading and note-taking interface.

---

## ✨ Key Capabilities

- **Minimalist Aesthetic:** Built with deliberate design tokens (#FAFAF8 background, Newsreader serif headers, Inter body text, #2F5D50 accent, zero blur/glow/gradients).
- **Local Video & Thumbnail Storage:** Downloads 720p progressive MP4s and cover frames locally with `yt-dlp` and serves them with full HTTP 206 Partial Content Range streaming.
- **Accurate Audio Transcription:** Gemini 3.5 Flash watches the video frames, transcribes speech word-for-word, and explicitly identifies silent/music-only clips.
- **Custom Categories & Corrections:** Add, rename, recolor, and drag-reorder categories with automatic migration to "Other" upon category deletion.
- **Passcode & Signed Session Security:** Constant-time passcode verification, signed HttpOnly SameSite=Lax session cookies (30-day expiry), and rate-limiting (5 attempts/min/IP).
- **Status Lifecycle:** Track reels across `Unread`, `Reviewed`, `Tried`, and `Archived` statuses with auto-marking on review.
- **Android Share Target & PWA:** Native web app manifest, offline shell service worker, and share sheet integration receiving Instagram URLs directly.

---

## 🛠️ Environment Variables (.env)

Create a `.env` file in the project root (see `.env.example`):

```bash
# 1. Google Gemini API Key
GEMINI_API_KEY=your_gemini_api_key_here

# 2. Vault Passcode
# If unset, Savyor runs open on 127.0.0.1 (localhost only).
# When set, requires authentication via HttpOnly session cookies.
SAVYOR_PASSCODE=your_custom_passcode

# 3. Session Signing Secret (Must be separate from passcode)
SAVYOR_SECRET_KEY=generate_a_secure_random_secret_for_cookies

# 4. CORS Origins (Required for credentials/cookies)
CORS_ORIGINS=http://localhost:5173,http://localhost:8000,http://127.0.0.1:5173,http://127.0.0.1:8000
```

---

## 🚀 How to Run Locally

### 1. Run the Backend Server
```powershell
# Activate Python virtual environment
.\.venv\Scripts\Activate.ps1

# Run FastAPI backend with Uvicorn (binds to 0.0.0.0:8000 when passcode set)
python server.py
```
Open **[http://localhost:8000](http://localhost:8000)** in your browser.

### 2. Frontend Development & Production Build
```powershell
cd frontend

# Run live Vite dev server
npm.cmd run dev

# Or compile production bundle into frontend/dist (served directly by FastAPI)
npm.cmd run build
```

---

## 💾 Database & Media Backups

Savyor includes built-in scripts to create consistent, online backups of your SQLite database (using SQLite's native online backup API, preventing corruption during live writes) and media storage.

### Create a Backup
```powershell
# Python
python scripts/backup.py

# Or PowerShell wrapper
powershell -ExecutionPolicy Bypass -File scripts/backup.ps1
```
Backups are saved to `backups/YYYY-MM-DD_HHMMSS/` containing `reels.db` and the `media/` folder. Savyor automatically retains the newest 10 backups and removes older ones.

### Restore from Backup
```powershell
# Interactive selection (shows list of available backups)
python scripts/restore.py

# Or restore a specific backup folder
python scripts/restore.py backups/2026-11-24_140345
```
Before overwriting any files, `scripts/restore.py` prompts for explicit confirmation (`yes`) and creates an automatic pre-restore safety backup of current data first.

---

## 🧪 Running Automated Tests

Run the full pytest test suite:
```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_savyor.py -v
```

This verifies:
1. URL parsing and shortcode normalization (`/reel/`, `/p/`, `/share/reel/`, query params).
2. PWA & Android Share-Target link extraction from text.
3. Category CRUD, reordering, and fallback to "Other" when deleted.
4. Gemini category validation against active database categories.
5. Reel status lifecycle (`unread`, `reviewed`, `tried`, `archived`) and archived filtering.
6. Constant-time passcode verification, rate limiting, and HttpOnly session cookies.

---

## 📱 Android Share Target & Capacitor APK

### Option A: Progressive Web App (PWA)
1. Open Savyor in Chrome on your Android device (on the same Wi-Fi LAN address or via HTTPS tunnel).
2. Tap the browser menu `⋮` ➔ **Add to Home screen** (or **Install app**).
3. Savyor installs as a standalone app with the official Savyor ribbon icon.
4. Share any reel from Instagram ➔ select **Savyor** in the Android share sheet. The Add modal will open with the reel URL prefilled for confirmation.

### Option B: Android APK (Capacitor)
To package into a native Android `.apk`:
```powershell
cd frontend
npm.cmd install @capacitor/core @capacitor/cli @capacitor/android
npx.cmd cap init Savyor app.savyor.vault
npm.cmd run build
npx.cmd cap add android
```

In `android/app/src/main/AndroidManifest.xml`, add the `text/plain` intent filter inside `<activity android:name=".MainActivity">`:

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

In `frontend/src/App.jsx`, configure the backend API origin if pointing to a remote server (e.g. `https://vault.yourdomain.com`). HTTPS is required for non-localhost connections.

---

## 📋 Manual Verification Checklist

1. **Passcode Screen:**
   - Open `http://localhost:8000`. If locked, verify the minimalist Savyor unlock screen appears.
   - Enter your configured `SAVYOR_PASSCODE` ➔ click **Unlock vault**.
2. **Category Tabs & Persistence:**
   - Click different category tabs in the top bar. Note that categories with reels remain visible and filtering is instantaneous.
   - Click **Manage** ➔ add a new category (e.g. "Architecture") with a color swatch ➔ verify it appears in the list.
3. **Status Filter:**
   - Switch status between `All`, `Unread`, `Reviewed`, `Tried`, `Archived`.
   - Verify that archived reels are hidden when viewing `All`.
4. **Reel Detail View:**
   - Click any reel card. The URL updates to `/reel/:id` with a sticky 340px video on the left and reading column on the right.
   - Test the video controls: play/pause, scrub bar, time counter, 1x/1.25x/1.5x/2x speed toggle, and loop button.
   - Switch between **Notes**, **Transcript**, and **Links** tabs.
   - Click **Copy notes** icon ➔ confirm notes are copied to clipboard with markdown formatting.
   - In the "..." menu, test **Change category** and **Change status**.
5. **Brand Preview:**
   - Navigate to `http://localhost:8000/brand` to inspect the clean SVG vector lockup, mark, wordmark, and app icons at various resolutions.
