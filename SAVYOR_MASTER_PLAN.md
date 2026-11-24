# SAVYOR MASTER PLAN (for Antigravity)

Read this whole file first, then execute it from top to bottom without stopping between steps. Savyor is a personal web app (installable PWA, no native app) that saves Instagram reels and turns them into structured notes. It runs on FastAPI + SQLite + React/Tailwind v4 and uses Gemini for analysis.

## Ground rules (apply to every step)

1. **Safety first.** Before any migration run `python scripts/backup.py` and confirm the backup opened and the reel count matches. Make every migration idempotent and non-destructive. Never delete user data to "fix" a problem.
2. **Git.** Commit after each numbered step with a clear message so any step can be undone on its own. Never commit `.env`, `reels.db`, `media/`, `backups/`, keys, or passcodes. Never print secrets (passcode, SECRET_KEY, any API key) in chat, logs, test output, or reports.
3. **Tests.** After each numbered step run BOTH suites: `tests/test_savyor.py` (pytest) and the Playwright e2e suite in `tests/e2e`. Add tests for every new behavior. Never weaken, skip, or delete an existing assertion to make a test pass; fix the code. Tests that need a passcode must read it from environment variables set by the test harness, never from a default.
4. **Scope.** Do exactly what is listed. Do NOT add: registration, invites, Google login, an admin page, quotas, a native/Capacitor app, action items, reminders, a weekly digest, semantic search, or an "ask your vault" chat. Keep the visual design: calm, minimal, off-white background #FAFAF8, ink #1A1A1A, accent #2F5D50, Newsreader (serif titles) and Inter (UI), no gradients, shadows, glow, blur, or emoji.
5. **If a step fails** after reasonable attempts: commit what works, record the problem in `docs/PROGRESS.md` (what failed, what you tried, what is needed), and continue with the next independent step. Never leave the app broken or the tests red at the end of a step.
6. **Progress file.** Keep `docs/PROGRESS.md` updated after each step: step name, commit hash, tests passed/failed counts, anything not verified.
7. **Things you cannot verify here** (real Instagram downloads from a cloud server, real Gemini quality, real phone install and share sheet, real Cloudflare/Tailscale/VM behavior, real-device performance) must be listed as "not verified" in the final report, never claimed as working.

---

# PART A: Baseline, cleanup, and open UI items

## A1. Baseline measurements (before changing anything)
Record into `docs/perf-baseline.md`: Lighthouse mobile (performance, LCP, CLS, TBT, total transfer size) for the library page and a reel detail page with 4G throttling; production bundle sizes (raw and gzipped) per chunk; size of `GET /api/reels` with 100 seeded reels; p50/p95 response times of the list, detail, and search endpoints with 1,000 seeded reels (use a separate test database, never the real one); server memory (RSS) idle and while processing one reel.
Targets to check later (report honestly if missed): initial JavaScript under 150 KB gzipped, LCP under 2.5 s on throttled 4G, list response under 50 KB for 50 reels, list p95 under 100 ms for 1,000 reels, idle memory under 150 MB.

## A2. Cleanup
- Remove `localtunnel` and any other tunnel packages the app does not import from `frontend/package.json`; update the lockfile; rebuild; rerun `npm audit --omit=dev`.
- Add a startup check and README note for `ffmpeg`: if not on PATH, print a warning with install instructions (Windows: `winget install ffmpeg`; Ubuntu: `apt install ffmpeg`). Thumbnails and the video frame fallback depend on it.
- Run the full Playwright e2e suite and pytest. Fix any test that still depends on the old default passcode. Report pass/fail counts.

## A3. Remaining UI and mobile issues (fix all that are still open)
- Mobile search is missing below 640 px: add a search icon in the header that opens a full-width search bar (autofocus, Esc closes, clear button). At about 768 px make the search field flexible so the placeholder is not clipped, and hide the "/" hint below 1024 px.
- Remove the "Brand" link from the production header (development only).
- Mobile List/Compact rows: no space reserved for hover actions on touch screens; use a "..." menu button (at least 40 px) at the row's right edge; titles use the full width (max 2 lines); meta line separators never wrap alone.
- Do not render the empty bordered strip above the first list row when there is no active job.
- Touch targets at least 40 px tall on touch screens (view toggles, sort, Lock, status pills, menus).
- Code blocks in notes: subtle background (#F2F1EC), 12 px padding, 8 px radius, `overflow-x: auto`; inline code gets a light background.
- The "Links (n)" tab uses the same colors as the other tabs; the video shows a poster and no stuck spinner once metadata loads.
- Duplicate reels return HTTP 409 (not 400); update UI and tests.
- Auto-mark-as-reviewed must be a setting (default on, 3 seconds) in a small Settings menu, with a test for on and off.
- Service worker: after a frontend rebuild a normal reload must show the new version (versioned caches, delete old caches on activate, "Update available" prompt); never cache `/api` or media responses. Add a test.

Commit and update `docs/PROGRESS.md`.

---

# PART B: Phase 2, smarter notes

## B1. Reprocess and schema versioning
- Add `schema_version` handling in `db.py` with ordered, idempotent migrations.
- Add a "Reprocess" action (in the reel "..." menu) and `POST /api/reels/{id}/reprocess`. It re-runs Gemini analysis on the stored local video (or re-downloads only if the file is missing) plus the caption, as a background job with the usual statuses and progress UI. User-edited fields (user-chosen category, edited tags, personal notes, status) must NOT be overwritten; only AI-generated fields change. Keep the old notes visible until new ones are saved; if reprocessing fails keep the old notes and show an error with Retry.
- Store `model_used`, `processed_at`, and `analysis_version` per reel.
- In Settings add "Reprocess all" (sequential, with progress, cancellable).

## B2. Note types and templates
- Extend the Gemini Pydantic response schema with `note_type`: one of recipe, workout, tutorial, tool_or_product, place, book_or_media, advice, general (unknown values fall back to general).
- Type-specific structured fields: recipe (servings, time, ingredients with name and quantity, steps); workout (goal, equipment, exercises with name/sets/reps or duration/notes, cautions); tutorial (prerequisites, numbered steps, code snippets with language, result); tool_or_product (name, what it does, key features, price if stated, alternatives mentioned, link); place (name, location as stated, what to do or eat, tips, best time); book_or_media (title, creator, why recommended, where to find it); advice (main claim, supporting points, caveats); general (summary, key points).
- Prompt rule: use only information present in the video or caption; if a field is not stated leave it empty or null; never guess quantities, prices, links, or locations; if the audio is unclear say so.
- The Notes tab renders a template per type in the existing minimal style (recipe: ingredients list and numbered steps; workout: a table of exercises; tutorial: steps with code blocks). The summary and key takeaways always appear first. Cards in Grid/List stay unchanged; show the note type as a small label in the detail meta row.
- "Copy notes" and Markdown export must produce good Markdown for every template.

## B3. Timestamps and key moments
- Ask Gemini for a timestamped transcript (segments with start time in seconds and text, grouped into short readable paragraphs) and 3 to 8 key moments (time + short label). Validate that times are within the video duration and sorted; clamp or drop bad ones.
- Transcript tab: paragraphs with clickable [m:ss] timestamps that seek the player (scroll the video into view on mobile); highlight the currently playing paragraph subtly.
- Add a "Key moments" list at the top of the Notes tab whose items seek the video. When no video file is stored, show timestamps as plain text.
- The plain transcript text stays searchable and copyable.

## B4. Link verification
- For each extracted resource with a URL: normalize; allow only http/https; block private/internal IP ranges and localhost (SSRF protection, also after redirects); at most 3 redirects; 5 s timeout; HEAD with GET fallback. For github.com repos also call the unauthenticated GitHub API (handle rate limits gracefully) and store existence, description, stars.
- Store `link_status`: verified, unreachable, unverified, plus the final URL. Links tab shows verified links normally, unreachable ones with a muted "couldn't verify" note, and resources without a URL as name + description with a "Search the web" link. Add "Re-check links". Never block saving on link checks. Only show resource types github_repo, tool, product, website, book/media, place.

## B5. Full-text search (FTS5)
- Replace ad-hoc search with SQLite FTS5 over title, summary, all flattened note text, transcript, caption, tags, creator, and category name. Keep it in sync on insert, update, reprocess, and delete; rebuild for existing reels in a migration.
- Prefix matching, unicode61 tokenization with diacritics removed, bm25 ranking with title and tags weighted higher, quoted phrases supported, user input escaped so it can never cause an FTS syntax error or injection. Return highlighted snippets labeled by matched field (Title, Notes, Transcript, Caption).
- Must stay instant with 5,000 generated reels (add a test) and combine with category and status filters. Must not crash on Tamil, Hindi, or emoji text.

## B6. Backup and export in the app
- Settings > Data: "Download backup" (zip of a consistent SQLite backup made with the backup API plus the media folder), "Restore from backup" (validate the zip, show what it contains, require confirmation, keep an automatic copy of the current data first, reject path-traversal entries), and "Export all notes as Markdown" (zip with one .md per reel plus index.md, YAML frontmatter: title, creator, category, tags, status, source URL, saved date). All require login.

## B7. Tests for Part B
- Mock Gemini with fixtures for each note type and for malformed output (missing fields, wrong note_type, out-of-range timestamps, invalid URLs); it must degrade gracefully to the general template.
- Playwright: render each template; click a timestamp to seek; reprocess a reel and confirm user edits survive; link status labels; search ranking and snippets; backup download; Markdown export. Screenshots of each template at 390 and 1440 px widths; fail on horizontal overflow or console errors.
- Update the README.

Commit and update `docs/PROGRESS.md`.

---

# PART C: Fast, light, hosting-ready web app

Keep the same-origin cookie login. Do not switch to token auth.

## C1. user_id foundation (data model only)
- Add a `users` table with ONE default owner user, and `user_id` (NOT NULL, indexed, foreign key) on every per-user table (reels, categories, jobs, tags, settings, link checks, search index). Reel identity is (user_id, shortcode). Every data-access function requires `user_id`, including search, counts, export, and backup. Media paths become `{DATA_DIR}/media/{user_id}/...`; migrate existing files.
- Add `user_settings` (user_id, gemini_key_encrypted nullable using Fernet with `KEY_ENCRYPTION_SECRET` from env, keep_video boolean, timestamps). Gemini calls use the user's key if set, else `GEMINI_API_KEY`. Settings has a masked key field (last 4 characters only) and a "Test key" button; never return or log the key.
- Tests: a second user created at the data layer cannot read, list, search, update, delete, stream, or export the owner's data through any function or endpoint, even with a guessed ID.

## C2. Backend efficiency
- Single uvicorn worker. SQLite: WAL, busy_timeout, synchronous=NORMAL, foreign keys on, per-request connection, indexes on (user_id, created_at), (user_id, category_id), (user_id, status); check `EXPLAIN QUERY PLAN` for list, filter, and search queries.
- Slim list endpoint: `GET /api/reels` returns only card fields (id, title, creator, category_id, status, small thumbnail URL, saved date, summary up to 160 characters, optional search snippet), with cursor pagination (default 30) and a total count. Full notes, transcript, and links come only from `GET /api/reels/{id}`. ETag/If-None-Match with `Cache-Control: private, max-age=0, must-revalidate` on JSON reads. Add a test that fails if the list endpoint exceeds a fixed number of SQL queries.
- gzip (and brotli if available) for JSON, HTML, JS, CSS over 1 KB. Hashed static assets with `Cache-Control: public, max-age=31536000, immutable`; `index.html` no-cache.
- Thumbnails: at save time generate a small WebP (about 360 px wide, quality about 75) and a 720 px poster; delete the large original; serve the small one in lists with width/height, `loading="lazy"`, `decoding="async"`, and a solid-color placeholder. Backfill existing reels with a one-off command.
- Video: download at most 480 p (progressive mp4 preferred, graceful fallback). `keep_video` default false in hosted mode (`KEEP_VIDEO_DEFAULT` env): delete the video after analysis succeeds, keep thumbnails, notes, transcript, and the source link; the detail view then shows the poster with a clear "Open on Instagram" button. When a video is kept, serve it with Range support, `preload="none"`, and a poster; enforce `MAX_STORAGE_GB`.
- Jobs: in-process, SQLite-backed queue with configurable concurrency (default 1, max 2); jobs survive restarts (resume or mark failed with Retry); timeouts on download and Gemini upload; temp files cleaned in `finally`. No Redis or Celery.
- Job status polling with ETag and client exponential backoff (1 s to 8 s), and no polling while the tab is hidden.

## C3. Frontend efficiency
- Route-level code splitting (library, detail, settings, manage categories, add dialog); load react-markdown and code rendering only on the detail route; import lucide icons by name; remove unused dependencies; report the biggest bundle contributors (rollup-plugin-visualizer).
- Fonts: self-host Inter and Newsreader as subsetted woff2 with `font-display: swap`, preload only the one or two critical files, and remove any Google Fonts import or external font request.
- Rendering: memoize list items; do not re-render the whole library on keystrokes or job polling; debounce search (200 to 250 ms) and cancel in-flight requests with AbortController; virtualize lists above 100 items; `content-visibility: auto` for off-screen cards; respect `prefers-reduced-motion`.
- Data: cache the library and opened reels in IndexedDB with stale-while-revalidate; prefetch detail data on hover or touch-start; clear all caches on logout.
- PWA: service worker precaches the app shell and fonts with versioned caches; never caches `/api` or media; offline screen shows cached library and notes read-only. Keep the manifest `share_target` (url and text params): it works on Android Chrome once served over HTTPS. Extract the first Instagram URL from shared text, preserve it through login, and open the Add dialog prefilled without auto-submitting. Unit-test URL extraction (many share formats, tracking params, multiple links, non-Instagram text). Note in the README that iOS Safari does not support web share targets.
- Pause or unload off-screen video; release object URLs and listeners on unmount.

## C4. Deployment (provider-agnostic)
- `Dockerfile` (multi-stage, non-root, linux/arm64 and amd64, ffmpeg and a pinned yt-dlp with an update command) and `docker-compose.yml` with one persistent volume for `DATA_DIR`; `/health` endpoint (no auth, no secrets); graceful shutdown; structured JSON logs with job ids; log the category of any yt-dlp failure (login wall, rate limit, private, network). All config from env (`DATA_DIR`, `BASE_URL`, `PORT`, `CORS_ORIGINS`, `TRUST_PROXY`, `SAVYOR_PASSCODE` with no default, `SAVYOR_SECRET_KEY`, `GEMINI_API_KEY`, `KEY_ENCRYPTION_SECRET`, `KEEP_VIDEO_DEFAULT=false`, `MAX_CONCURRENT_JOBS=1`) with a complete `.env.example`. If `SAVYOR_PASSCODE` is unset, bind to 127.0.0.1 only. Rate-limit login using `CF-Connecting-IP`/`X-Forwarded-For` only when `TRUST_PROXY=true`.
- `docs/DEPLOY.md` with three options, step by step: (1) a free or student cloud VM such as Azure for Students or Oracle Always Free (choose the region nearest India; firewall; Docker; compose up; mention that new accounts may need a card, and that free quotas change so the current terms must be checked); (2) running at home on the Windows PC or an always-on mini machine and reaching it from the phone through Tailscale (install on both, use Tailscale Serve to get HTTPS on a stable `*.ts.net` address so the PWA installs and the share target works); (3) Cloudflare free proxy or Caddy in front of a VM for HTTPS, compression, and caching of hashed static assets with rules so `/api` is never cached. Include the backup cron/timer example (with optional upload to an S3-compatible bucket), restore steps, how to update yt-dlp, and a security checklist. State clearly that Instagram often blocks datacenter IP addresses, so downloads from a cloud VM must be tested with `scripts/hosting_test.py` before relying on it, and that a home connection usually works.

## C5. Verify
- Re-run the A1 measurements into `docs/perf-after.md` with a before/after table for every metric and target, noting any missed target and why.
- Playwright at 390, 768, and 1440 px for library, detail, settings, Manage categories, Add dialog, an error card, an empty library, and a no-results search; also with throttled network. Fail on horizontal overflow, clipped elements, or console errors.

---

# FINAL REPORT (`docs/REPORT.md`)
Write: what changed in each part, the commit list, before/after performance table, test counts for both suites, commands to run locally and to deploy, a manual checklist for my phone (install the PWA, log in, share a reel from Instagram, watch it process, open a note offline), and a clear list of everything not verified. Remind me to keep `.env` and `backups/` copies outside the project folder.
