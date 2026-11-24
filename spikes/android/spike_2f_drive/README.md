# Spike 2F: Google Drive Backup & Starred Reel Compression

## 1. Google Drive Authorization & Scopes

- **Recommended API**: Google Drive REST API v3 using Android CredentialManager or Google Play Services Auth (`GoogleAccountCredential.usingOAuth2`).
- **Narrowest Safe Scope**:
  `https://www.googleapis.com/auth/drive.appdata` (Drive App Data Folder).
  - Hidden from the user's regular Drive folder view.
  - Inaccessible to other apps or third parties.
  - Deleted automatically if user uninstalls the app and clears cloud data.

---

## 2. Google Cloud Console Setup (Numbered Steps for User)

1. Open [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project: `Savyor Personal Vault`.
3. In **APIs & Services > Library**, search for **Google Drive API** and click **Enable**.
4. Go to **APIs & Services > OAuth consent screen**:
   - User Type: **External**
   - App Name: `Savyor`
   - User Support Email: your personal Gmail
   - Publishing Status: **Testing**
   - Test Users: Add your personal Gmail address.
5. Go to **APIs & Services > Credentials > Create Credentials > OAuth client ID**:
   - Application Type: **Android**
   - Package Name: `com.savyor.app`
   - SHA-1 Certificate Fingerprint: (Obtain by running `./gradlew signingReport` or `keytool -list -v -keystore savyor.keystore`).
6. Save and exit. Zero client secrets needed (Android OAuth uses package name + SHA-1 certificate binding).

---

## 3. Video Compression for Starred Reels (Media3 Transformer)

- Uses Android's native hardware video encoder (`androidx.media3:media3-transformer`):
  - Transcodes 1080p/720p downloads to 480p H.264 baseline (~800 kbps video + 96 kbps AAC audio).
  - Typical file size reduction: **75% smaller** (from ~18 MB down to ~4.5 MB).
  - Background constraint: Scheduled via `WorkManager` with `setRequiresCharging(true)` and `setRequiredNetworkType(NetworkType.UNMETERED)` so it never burns battery or cellular mobile data.
