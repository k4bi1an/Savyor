# Spike 2G: UI Approach Matrix (Capacitor WebView vs Native Jetpack Compose)

## 1. Architectural Comparison Matrix

| Evaluation Criterion | Approach A: Capacitor WebView (Bundled React/Tailwind) | Approach B: Native Jetpack Compose |
| :--- | :--- | :--- |
| **Code Reuse** | **95% reuse** of all existing React components, note type templates, and styles. | **0% reuse** (complete rewrite of all UI components in Kotlin). |
| **Effort to Working APK** | **Low (2–3 days)** | **High (10–14 days)** |
| **Cold Start Time** | ~1.2s – 1.8s (WebView initialization + JS parse) | **~400ms – 700ms** (Instant native layout) |
| **Memory Footprint** | ~85 MB – 130 MB (Chromium WebView engine) | **~30 MB – 55 MB** (Pure native ART runtime) |
| **Scroll Performance** | Solid 60fps on modern phones; minor micro-stutter on fast fling. | **Flawless 120fps** native frame rate. |
| **Offline Capability** | All assets bundled inside APK (`file:///android_asset/`). | 100% native compiled code. |
| **Hard 200MB Budget Feasibility** | Achievable, but WebView cache requires tight caps. | **Easily exceeds all budget targets**. |

---

## 2. Hardened WebView Security Configuration (if Approach A selected)

If the Capacitor WebView route is used, it must be locked down against remote injection and local traversal:
1. **No Remote Content**: `allowNavigation` restricted strictly to `localhost` / `android_asset`.
2. **File Access Disabled**: `setAllowFileAccess(false)` and `setAllowContentAccess(false)`.
3. **Service Worker Disabled**: Service workers are unnecessary inside an APK since all assets are already bundled locally on disk.
4. **Strict Network Security Config**:
   ```xml
   <network-security-config>
       <base-config cleartextTrafficPermitted="false">
           <trust-anchors><certificates src="system" /></trust-anchors>
       </base-config>
   </network-security-config>
   ```

---

## 3. Recommended Phased Strategy

1. **Phase 1 (Feasibility & Fast Delivery)**:
   Package the existing, thoroughly tested Savyor web frontend into a Capacitor APK with a custom native Kotlin plugin handling `yt-dlp`, `WorkManager`, `AndroidKeyStore`, and `MediaMetadataRetriever`. This gets a working sideloadable app onto the user's phone in days.
2. **Phase 2 (Ultimate Optimization)**:
   Once the user has validated Instagram reel downloads and Gemini notes on their actual phone hardware and carrier network, replace the WebView with pure Jetpack Compose.
