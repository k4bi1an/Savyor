# Savyor Progress Tracking

Documenting execution of `SAVYOR_MASTER_PLAN.md` across Part A, Part B, and Part C.

---

## Part A: Baseline, Cleanup, and Open UI Items

- **Commit**: `pending` (to be committed immediately after progress logging)
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
