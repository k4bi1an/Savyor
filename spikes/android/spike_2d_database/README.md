# Spike 2D: Local Database, Schema Migrations & FTS5 Full-Text Search

## 1. Database Layer Decision: Jetpack Room vs Raw SQLite

| Criterion | Jetpack Room (Kotlin) | Raw Android SQLite | Capacitor SQLite |
| :--- | :--- | :--- | :--- |
| Compile-time verification of SQL | **Yes** (fails build on typo) | No (runtime crashes) | No |
| Schema migrations | **Built-in typed migrations** | Manual SQL strings | Manual SQL strings |
| Coroutines / Flow reactivity | **Native Flow support** | Manual callbacks | Async bridge |
| FTS5 support | Supported via `@Fts4` or raw `CREATE VIRTUAL TABLE USING fts5` | Supported | Plugin dependent |
| **Recommendation** | **RECOMMENDED** | Too error-prone | Only if WebView selected |

---

## 2. FTS5 Availability on Android

- **Android 9.0 (API 28) and above**: Android platform SQLite (3.22+) is compiled with FTS5 enabled by default across standard Google/AOSP builds.
- **Android 8.0 - 8.1 (API 26 - 27)**: Some OEM custom ROMs only enabled FTS4.
- **Solution for Guaranteed Compatibility**:
  If supporting API 26+, `io.requery:sqlite-android` provides a drop-in AOSP SQLite replacement with FTS5 + `unicode61 remove_diacritics 2` enabled on all devices, costing ~1.8 MB per ABI.

---

## 3. Schema Design for Phone-Only Savyor

```sql
CREATE TABLE reels (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    category_id TEXT NOT NULL,
    category_name TEXT NOT NULL,
    tags TEXT NOT NULL,
    summary TEXT NOT NULL,
    transcript TEXT NOT NULL,
    note_type TEXT NOT NULL DEFAULT 'general',
    type_data TEXT NOT NULL DEFAULT '{}',
    key_moments TEXT NOT NULL DEFAULT '[]',
    timestamped_transcript TEXT NOT NULL DEFAULT '[]',
    extracted_resources TEXT NOT NULL DEFAULT '[]',
    uploader TEXT NOT NULL,
    channel TEXT NOT NULL,
    source_url TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'unread',
    reviewed_at INTEGER,
    created_at INTEGER NOT NULL
);

CREATE VIRTUAL TABLE reels_fts USING fts5(
    reel_id UNINDEXED,
    title,
    summary,
    note_text,
    transcript,
    tags,
    uploader,
    category_name,
    tokenize='unicode61 remove_diacritics 2'
);
```

---

## 4. Import Path from Existing PC Savyor

- The PC web app exports all notes and data via `GET /api/backup/download` (`savyor_backup.zip`).
- Inside the Android app:
  1. User selects `savyor_backup.zip` via Android Storage Access Framework (`ACTION_OPEN_DOCUMENT`).
  2. Zip uncompressed in temporary directory.
  3. Database reader parses `reels.db` or `metadata.json`.
  4. Inserts all reels and categories into Room database in a single SQLite transaction (< 500 ms for 1,000 reels).
  5. Copies thumbnails into `context.filesDir/thumbs/`.
