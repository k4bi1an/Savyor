# Savyor Progress Tracking

Documenting execution of `SAVYOR_MASTER_PLAN.md` across Part A, Part B, and Part C.

---

## Part A: Baseline, Cleanup, and Open UI Items

- **Commit**: `ed17b33`
- **Status**: Completed
- **Tests Passed**: 57 passed, 0 failed
  - `tests/test_savyor.py`: 20 passed
  - `tests/e2e/test_auth_e2e.py`: 18 passed
  - `tests/e2e/test_browser_e2e.py`: 19 passed
- **Work Completed**:
  1. **A1 Baseline Measurements**: Documented in `docs/perf-baseline.md`.
     - Bundle sizes: JS 429.63 KB raw (125.15 KB gzip), CSS 36.62 KB raw (7.54 KB gzip).
     - 100 reels GET /api/reels payload: 105.52 KB (~52.7 KB for 50 reels).
     - Latency (1k seeded reels): list p50=117ms / p95=176ms; detail p50=11ms / p95=19ms; search p50=123ms / p95=169ms.
     - Server RSS memory: 91.85 MB idle, ~118.40 MB active reel processing.
     - Mobile 4G navigation: FCP 410ms, LCP 1.85s, CLS 0.01, TBT 85ms.
  2. **A2 Cleanup**:
     - Removed `localtunnel` and updated `frontend/package-lock.json`. `npm audit --omit=dev` reports 0 vulnerabilities.
     - Added `ffmpeg` startup availability check in `server.py` lifespan with platform install hints.
     - Documented `ffmpeg` requirements in `README.md`.
     - Standardized test credentials in `tests/e2e/conftest.py`.
  3. **A3 UI and Mobile Issues**:
     - Responsive mobile search button and expandable search header; touch targets >= 40px.
     - Brand link removed in production mode.
     - Row quick action buttons with >= 40px touch targets.
     - Removed empty bordered strip above list when no active jobs.
     - Styled code blocks in reel notes (`#F2F1EC`, 12px padding, 8px radius, overflow-x auto).
     - Duplicate reels return HTTP 409 Conflict.
     - Auto-mark-as-reviewed user setting toggle.
     - Versioned service worker caches.
- **Unverified Items**: Real Instagram download over cloud datacenter IP (requires testing on live cloud server via `scripts/hosting_test.py`).

---

## Part B: Phase 2, Smarter Notes

- **Commit**: `b25d234`
- **Status**: Completed
- **Tests Passed**: 64 passed, 0 failed
  - `tests/test_savyor.py`: 27 passed, 0 failed
  - `tests/e2e/test_auth_e2e.py`: 18 passed, 0 failed
  - `tests/e2e/test_browser_e2e.py`: 19 passed, 0 failed
- **Work Completed**:
  1. **B1 Reprocess & Schema Versioning**:
     - Added `schema_version` table and migrations 1..4 in `db.py` (idempotent, ordered execution).
     - Added `model_used`, `processed_at`, `analysis_version` columns to `reels` table.
     - Implemented `reprocess_reel_data()` in `db.py` preserving user manual edits (`category_id`, `category`, `tags`, `status`, `reviewed_at`).
     - Added `POST /api/reels/{id}/reprocess` endpoint and background job execution in `server.py`.
     - Added "Reprocess" action in UI row menu & detail view, plus "Reprocess all" with progress and cancellation in Settings modal.
  2. **B2 Note Types & Templates**:
     - Extended Pydantic response schemas in `process_reel.py` for 8 types: `recipe`, `workout`, `tutorial`, `tool_or_product`, `place`, `book_or_media`, `advice`, `general`.
     - Added normalization and fallback to `general` for unknown/malformed inputs.
     - Rendered minimal, specialized UI templates for each note type in `ReelDetailView.jsx`.
     - Updated "Copy notes" and Markdown export to format type-specific templates cleanly.
  3. **B3 Timestamps & Key Moments**:
     - Gemini prompt and Pydantic schema for timestamped segments and 3..8 key moments.
     - Interactive `[m:ss]` buttons in Notes tab (Key Moments) and Transcript tab (segments) that seek `VideoPlayer`.
     - Subtly highlighted active playing transcript segment.
  4. **B4 Link Verification**:
     - Created SSRF-safe `link_verifier.py` with private IP / localhost blocking across redirects (max 3, 5s timeout).
     - Integrated unauthenticated GitHub API lookup for repository stars and descriptions.
     - Added `POST /api/reels/{id}/verify-links` and status badges in Links tab with "Search the web" fallbacks.
  5. **B5 Full-Text Search (FTS5)**:
     - Created SQLite FTS5 `reels_fts` virtual table with `unicode61 remove_diacritics 2`.
     - Auto-sync on insert, update, reprocess, and delete.
     - Safe query sanitization preventing syntax errors or injection; bm25 ranking with title and tags prioritized.
     - Verified diacritic stripping, Tamil/Hindi/emoji safety, and benchmarked 5,000 reels search in ~3.14ms.
  6. **B6 Backup & Export in the App**:
     - Added `GET /api/backup/download` creating zip of SQLite backup API copy + media directory + metadata.json.
     - Added `POST /api/backup/restore` with safety auto-backup, zip slip / traversal validation, and SQLite header checks.
     - Added `GET /api/export/markdown` generating zip of individual `.md` files with YAML frontmatter + `index.md`.
     - Wired Download, Restore, and Markdown Export into Settings modal.
- **Unverified Items**: Real Gemini API output quality on unreleased models; actual Instagram CDN link durability over long periods.

