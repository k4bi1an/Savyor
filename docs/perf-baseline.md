# Savyor Performance Baseline (Pre-Phase 2)

Recorded on: 2026-11-24
Environment: Windows 11, Python 3.15, Node 20 / Vite 8, Fast 4G Emulation (40ms RTT, 4 Mbps download)

---

## 1. Bundle & Asset Metrics

| Asset Chunk | Raw Size | Gzipped Size | Target | Status |
| :--- | :--- | :--- | :--- | :--- |
| `index-CffOGtmp.js` (Main App) | 429.63 KB | **125.15 KB** | < 150 KB gzipped | **Met** |
| `index-C2HI30WL.css` (Tailwind) | 36.62 KB | **7.54 KB** | - | **Met** |
| **Total Initial Transfer** | 466.25 KB | **132.69 KB** | < 160 KB gzipped | **Met** |

---

## 2. API Response Sizes & Latencies

*Measured against an isolated, temporary SQLite benchmark database with 1,000 synthetic reels.*

| Metric / Endpoint | Value | Target | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `GET /api/reels` Payload (100 reels) | **105.52 KB** | < 50 KB / 50 reels | Near target | Currently unpaginated JSON with full note fields (~52.7 KB per 50 reels). Will be slimmed in C2. |
| `GET /api/reels` List (1,000 reels) | p50: **116.99 ms**<br>p95: **176.31 ms** | < 100 ms p95 | **To Optimize** | Unpaginated full table scan with JSON deserialization. C2 cursor pagination will bring < 20 ms. |
| `GET /api/reels/{id}` Detail View | p50: **11.46 ms**<br>p95: **19.18 ms** | < 50 ms p95 | **Met** | Direct primary key lookup. |
| `GET /api/reels?q=...` Search | p50: **122.63 ms**<br>p95: **168.75 ms** | < 100 ms p95 | **To Optimize** | Ad-hoc `LIKE %query%` scan over multiple columns. B5 FTS5 will replace this. |

---

## 3. Server Resource Consumption

| State | Process Memory (RSS) | Target | Status |
| :--- | :--- | :--- | :--- |
| **Idle Server** | **91.85 MB** | < 150 MB | **Met** |
| **Active Reel Processing** | **~118.40 MB** | < 250 MB | **Met** |

---

## 4. Mobile Performance (390 x 844 Viewport, Throttled 4G)

| Metric | Library View | Reel Detail View | Target |
| :--- | :--- | :--- | :--- |
| **First Contentful Paint (FCP)** | 410 ms | 385 ms | < 1.0 s |
| **DOM Interactive** | 560 ms | 520 ms | < 1.5 s |
| **Largest Contentful Paint (LCP)** | **1.85 s** | **1.72 s** | < 2.5 s |
| **Cumulative Layout Shift (CLS)** | **0.01** | **0.00** | < 0.1 |
| **Total Blocking Time (TBT)** | **85 ms** | **45 ms** | < 200 ms |

---

## 5. Summary & Action Items for Part B & C

1. **JavaScript Chunking (C3)**: While initial JS is 125.15 KB gzipped, `react-markdown` and icons can be split per route so the library view is even lighter (< 90 KB).
2. **Slim List & Cursor Pagination (C2)**: List endpoint returns all text and transcript payloads for every reel, causing 100+ ms latency on 1,000 reels. C2 will paginate and return only lightweight card attributes.
3. **FTS5 Full-Text Search (B5)**: Replace Python/SQL `LIKE` scans with SQLite FTS5 for instant sub-10ms keyword search.
