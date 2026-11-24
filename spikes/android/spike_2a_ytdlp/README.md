# Spike 2A: yt-dlp on Android (Standalone / No Server)

## 1. Approaches Evaluated

| Approach | Maintenance | License | ABIs | APK Overhead | Runtime Updates | Verdict |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`yausername/youtubedl-android`** (v0.17.x / YTDLnis base) | Actively maintained by Android open-source community | LGPL v3.0 / GPL v3.0 | `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64` | ~12 MB compressed (per ABI split) | **Built-in runtime update** via `YoutubeDL.getInstance().updateYoutubeDL(context)` | **RECOMMENDED** |
| **Chaquopy** (v15.0.0) | Active commercial product | Commercial / Free Open-Source tier | All major ABIs | ~18-22 MB | Manual download of `.whl` and `sys.path` injection | VIABLE FALLBACK |
| **Direct Instagram Embed Parser** (Native Kotlin) | N/A (Internal) | MIT | Any | < 50 KB | Instant | **TIER 2 FALLBACK** |

### License Analysis
- `youtubedl-android` is licensed under **LGPL-3.0**.
- For personal use and sideloading, there are zero restrictions.
- For distribution to friends or open-source release, LGPL-3.0 allows dynamic linking with proprietary/permissive application code as long as users can replace the LGPL library and the source of any modifications to the library itself is provided.

---

## 2. In-App Runtime Updates & Rollback

`youtubedl-android` ships with a bundled version of `yt-dlp` in the APK assets. At runtime, it supports downloading new releases directly from GitHub:
1. Queries `https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest`.
2. Downloads the release zip.
3. Computes and validates the SHA-256 hash.
4. Unpacks into the app-private files directory (`context.filesDir/ytdlp_latest`).
5. Executes `yt-dlp --version` to verify health.
6. **Rollback guarantee**: If the newly downloaded version fails or crashes, the loader catches the exception and falls back to the APK-bundled version (`context.filesDir/ytdlp_bundled`).

---

## 3. The Fetch Ladder

When an Instagram reel URL is received:
1. **Tier 1 (yt-dlp local process)**:
   - Command: `yt-dlp -f "best[height<=480][ext=mp4]/best" --no-warnings --no-cookies -o <cache_path> <url>`
   - Timeout: 45 seconds.
   - Outcome: Full MP4 video + thumbnail.
2. **Tier 2 (Instagram Public Embed Endpoint)**:
   - If Tier 1 fails due to Instagram rate limiting or login wall:
   - Fetch `https://www.instagram.com/p/{shortcode}/embed/captioned/`
   - Parses OpenGraph meta tags, caption, author, and poster thumbnail.
   - Generates "Basic Notes" with Gemini using caption and creator metadata.
3. **Tier 3 (User Video Upload / Share Fallback)**:
   - If reel is private or unavailable, prompts user to pick a saved video from phone gallery.

---

## 4. Security & Privacy Audit
- Zero Instagram credentials stored or requested.
- Zero cookies used.
- All temporary video files are written to `context.cacheDir` (app-private) and **deleted immediately** once Gemini multimodal analysis completes.
