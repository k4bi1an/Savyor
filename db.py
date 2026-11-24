import sqlite3
import json
import os
import re
from typing import Optional, Dict, Any, List

def get_data_dir() -> Optional[str]:
    d = os.getenv("SAVYOR_DATA_DIR", "").strip()
    return d if d else None

def get_db_path() -> str:
    if os.getenv("SAVYOR_DB_PATH"):
        return os.getenv("SAVYOR_DB_PATH")
    data_dir = get_data_dir()
    if data_dir:
        os.makedirs(data_dir, exist_ok=True)
        return os.path.join(data_dir, "reels.db")
    return "reels.db"

def get_videos_dir() -> str:
    data_dir = get_data_dir()
    if data_dir:
        return os.path.join(data_dir, "media", "videos")
    return os.path.join("media", "videos")

def get_thumbs_dir() -> str:
    data_dir = get_data_dir()
    if data_dir:
        return os.path.join(data_dir, "media", "thumbs")
    return os.path.join("media", "thumbs")

DB_PATH = get_db_path()

DEFAULT_CATEGORIES = [
    {"id": "tech-code", "name": "Tech & Code", "color": "#4A6B82", "sort_order": 0},
    {"id": "health-fitness", "name": "Health & Fitness", "color": "#477A64", "sort_order": 1},
    {"id": "productivity", "name": "Productivity", "color": "#8A6D3B", "sort_order": 2},
    {"id": "business-finance", "name": "Business & Finance", "color": "#5C5885", "sort_order": 3},
    {"id": "food", "name": "Food", "color": "#944E45", "sort_order": 4},
    {"id": "creative", "name": "Creative", "color": "#7B5C82", "sort_order": 5},
    {"id": "life-mindset", "name": "Life & Mindset", "color": "#8A526E", "sort_order": 6},
    {"id": "other", "name": "Other", "color": "#6B6B6B", "sort_order": 7},
]

def get_db() -> sqlite3.Connection:
    conn = sqlite3.connect(get_db_path())
    conn.row_factory = sqlite3.Row
    return conn

def slugify(text: str) -> str:
    slug = re.sub(r'[^a-zA-Z0-9]+', '-', text.lower()).strip('-')
    return slug or "cat"

def sanitize_fts5_query(query: str) -> str:
    """
    Safely formats a user query for SQLite FTS5.
    Preserves quoted phrases, adds prefix matching to words, and strips operator keywords.
    """
    if not query or not query.strip():
        return ""
    phrases = re.findall(r'"([^"]+)"', query)
    remainder = re.sub(r'"[^"]*"', ' ', query)
    tokens = []
    for phrase in phrases:
        clean_p = phrase.strip().replace('"', '""')
        if clean_p:
            tokens.append(f'"{clean_p}"')
    words = re.findall(r'[\w]+', remainder)
    FTS_KEYWORDS = {"AND", "OR", "NOT", "NEAR"}
    for w in words:
        w_clean = w.strip()
        if not w_clean or w_clean.upper() in FTS_KEYWORDS:
            continue
        tokens.append(f'"{w_clean}"*')
    return " ".join(tokens)

def flatten_note_text(card_data: Dict[str, Any]) -> str:
    """Flattens structured note content, key takeaways, and type data into searchable plain text."""
    parts = []
    # Key takeaways
    takeaways = card_data.get("key_takeaways", [])
    if isinstance(takeaways, list):
        parts.extend(str(t) for t in takeaways)
    # Structured content
    sc = card_data.get("structured_content", {})
    if isinstance(sc, dict):
        parts.append(str(sc.get("heading", "")))
        parts.append(str(sc.get("items_or_markdown", "")))
    elif isinstance(sc, str):
        parts.append(sc)
    # Type data
    td = card_data.get("type_data", {})
    if isinstance(td, dict):
        for k, v in td.items():
            if isinstance(v, list):
                for item in v:
                    if isinstance(item, dict):
                        parts.extend(str(val) for val in item.values() if val)
                    else:
                        parts.append(str(item))
            elif isinstance(v, dict):
                parts.extend(str(val) for val in v.values() if val)
            elif v:
                parts.append(str(v))
    # Extracted resources
    res = card_data.get("extracted_resources", [])
    if isinstance(res, list):
        for r in res:
            if isinstance(r, dict):
                parts.append(str(r.get("name", "")))
                parts.append(str(r.get("description", "")))
    return " ".join(p for p in parts if p)

def sync_reel_fts(conn: sqlite3.Connection, reel_id: str):
    """Synchronizes a single reel row into the reels_fts virtual table."""
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM reels WHERE id = ?", (reel_id,))
    r = cursor.fetchone()
    if not r:
        cursor.execute("DELETE FROM reels_fts WHERE reel_id = ?", (reel_id,))
        return

    # Parse tags
    try:
        tags_list = json.loads(r["tags"] or "[]")
        tags_str = " ".join(str(t) for t in tags_list)
    except Exception:
        tags_str = str(r["tags"] or "")

    # Parse note text
    try:
        takeaways = json.loads(r["key_takeaways"] or "[]")
    except Exception:
        takeaways = []
    try:
        sc = json.loads(r["structured_content"] or "{}")
    except Exception:
        sc = {}
    try:
        td = json.loads(r["type_data"] or "{}")
    except Exception:
        td = {}
    try:
        res = json.loads(r["extracted_resources"] or "[]")
    except Exception:
        res = []

    note_text = flatten_note_text({
        "key_takeaways": takeaways,
        "structured_content": sc,
        "type_data": td,
        "extracted_resources": res
    })

    creator = f"{r['uploader'] or ''} {r['channel'] or ''}".strip()
    category_name = r["category"] or ""

    cursor.execute("DELETE FROM reels_fts WHERE reel_id = ?", (reel_id,))
    cursor.execute("""
    INSERT INTO reels_fts (reel_id, title, summary, note_text, transcript, caption, tags, creator, category_name)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        reel_id,
        r["title"] or "",
        r["summary"] or "",
        note_text,
        r["transcript"] or "",
        "",  # caption stored in media/webpage or description
        tags_str,
        creator,
        category_name
    ))

def rebuild_all_fts(conn: sqlite3.Connection):
    """Rebuilds the entire reels_fts virtual table from reels."""
    cursor = conn.cursor()
    cursor.execute("DELETE FROM reels_fts")
    cursor.execute("SELECT id FROM reels")
    reel_ids = [row["id"] for row in cursor.fetchall()]
    for rid in reel_ids:
        sync_reel_fts(conn, rid)

def init_db():
    conn = get_db()
    cursor = conn.cursor()

    # 1. Schema versioning table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS schema_version (
        version INTEGER PRIMARY KEY,
        applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        description TEXT
    )
    """)

    # 2. Categories Table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS categories (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL COLLATE NOCASE UNIQUE,
        color TEXT,
        sort_order INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)

    # Seed Default Categories if empty
    cursor.execute("SELECT COUNT(*) as count FROM categories")
    if cursor.fetchone()["count"] == 0:
        for cat in DEFAULT_CATEGORIES:
            cursor.execute("""
            INSERT OR IGNORE INTO categories (id, name, color, sort_order)
            VALUES (?, ?, ?, ?)
            """, (cat["id"], cat["name"], cat["color"], cat["sort_order"]))

    # 3. Category Corrections Table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS category_corrections (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        summary TEXT NOT NULL,
        chosen_category TEXT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)

    # 4. Reels Base Table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS reels (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        category TEXT NOT NULL,
        category_id TEXT,
        tags TEXT NOT NULL,
        summary TEXT NOT NULL,
        transcript TEXT NOT NULL DEFAULT '',
        key_takeaways TEXT NOT NULL,
        extracted_resources TEXT NOT NULL,
        structured_content TEXT NOT NULL,
        uploader TEXT NOT NULL,
        channel TEXT NOT NULL,
        thumbnail TEXT,
        local_video_path TEXT,
        local_thumb_path TEXT,
        direct_video_url TEXT,
        webpage_url TEXT,
        status TEXT NOT NULL DEFAULT 'unread',
        reviewed_at TIMESTAMP,
        suggested_new_category TEXT,
        source TEXT NOT NULL DEFAULT 'instagram',
        model_used TEXT DEFAULT 'gemini-2.5-flash',
        processed_at TIMESTAMP,
        analysis_version INTEGER DEFAULT 1,
        note_type TEXT DEFAULT 'general',
        type_data TEXT DEFAULT '{}',
        key_moments TEXT DEFAULT '[]',
        timestamped_transcript TEXT DEFAULT '[]',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)

    # 5. Jobs Table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS jobs (
        id TEXT PRIMARY KEY,
        url TEXT NOT NULL,
        reel_id TEXT,
        status TEXT NOT NULL,
        progress TEXT NOT NULL,
        error TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)

    # 6. Ordered Idempotent Migrations
    cursor.execute("SELECT COALESCE(MAX(version), 0) as v FROM schema_version")
    current_version = cursor.fetchone()["v"]

    # Migration 1: Base columns check
    if current_version < 1:
        cursor.execute("PRAGMA table_info(reels)")
        cols = [row["name"] for row in cursor.fetchall()]
        if "transcript" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN transcript TEXT NOT NULL DEFAULT ''")
        if "local_video_path" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN local_video_path TEXT")
        if "local_thumb_path" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN local_thumb_path TEXT")
        if "category_id" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN category_id TEXT")
        if "status" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN status TEXT NOT NULL DEFAULT 'unread'")
        if "reviewed_at" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN reviewed_at TIMESTAMP")
        if "suggested_new_category" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN suggested_new_category TEXT")
        if "source" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN source TEXT NOT NULL DEFAULT 'instagram'")
        cursor.execute("INSERT OR REPLACE INTO schema_version (version, description) VALUES (1, 'Baseline columns and categories')")

    # Migration 2: Reprocess and analysis version metadata
    if current_version < 2:
        cursor.execute("PRAGMA table_info(reels)")
        cols = [row["name"] for row in cursor.fetchall()]
        if "model_used" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN model_used TEXT DEFAULT 'gemini-2.5-flash'")
        if "processed_at" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN processed_at TIMESTAMP")
        if "analysis_version" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN analysis_version INTEGER DEFAULT 1")
        cursor.execute("INSERT OR REPLACE INTO schema_version (version, description) VALUES (2, 'Model, processed_at, and analysis_version')")

    # Migration 3: Note types and timestamps
    if current_version < 3:
        cursor.execute("PRAGMA table_info(reels)")
        cols = [row["name"] for row in cursor.fetchall()]
        if "note_type" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN note_type TEXT DEFAULT 'general'")
        if "type_data" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN type_data TEXT DEFAULT '{}'")
        if "key_moments" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN key_moments TEXT DEFAULT '[]'")
        if "timestamped_transcript" not in cols:
            cursor.execute("ALTER TABLE reels ADD COLUMN timestamped_transcript TEXT DEFAULT '[]'")
        cursor.execute("INSERT OR REPLACE INTO schema_version (version, description) VALUES (3, 'Note types, type_data, key_moments, and timestamped_transcript')")

    # Migration 4: FTS5 Virtual Table
    if current_version < 4:
        cursor.execute("""
        CREATE VIRTUAL TABLE IF NOT EXISTS reels_fts USING fts5(
            reel_id UNINDEXED,
            title,
            summary,
            note_text,
            transcript,
            caption,
            tags,
            creator,
            category_name,
            tokenize='unicode61 remove_diacritics 2'
        )
        """)
        rebuild_all_fts(conn)
        cursor.execute("INSERT OR REPLACE INTO schema_version (version, description) VALUES (4, 'FTS5 full text search table with bm25 ranking')")

    # Migrate category_id for unmapped reels
    cursor.execute("SELECT id, category FROM reels WHERE category_id IS NULL OR category_id = ''")
    unmapped = cursor.fetchall()
    for row in unmapped:
        r_id = row["id"]
        cat_name = row["category"]
        cursor.execute("SELECT id FROM categories WHERE name = ? COLLATE NOCASE", (cat_name,))
        match = cursor.fetchone()
        matched_id = match["id"] if match else "other"
        cursor.execute("UPDATE reels SET category_id = ? WHERE id = ?", (matched_id, r_id))

    conn.commit()
    conn.close()

# Category CRUD
def get_categories() -> List[Dict[str, Any]]:
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("""
    SELECT c.id, c.name, c.color, c.sort_order, c.created_at,
           COUNT(r.id) as reel_count
    FROM categories c
    LEFT JOIN reels r ON r.category_id = c.id
    GROUP BY c.id
    ORDER BY c.sort_order ASC, c.created_at ASC
    """)
    rows = cursor.fetchall()
    conn.close()
    return [
        {
            "id": r["id"],
            "name": r["name"],
            "color": r["color"],
            "sort_order": r["sort_order"],
            "reel_count": r["reel_count"],
            "is_default": r["id"] == "other"
        }
        for r in rows
    ]

def get_category_by_id(cat_id: str) -> Optional[Dict[str, Any]]:
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM categories WHERE id = ?", (cat_id,))
    row = cursor.fetchone()
    conn.close()
    if not row:
        return None
    return dict(row)

def create_category(name: str, color: Optional[str] = None) -> Dict[str, Any]:
    name = name.strip()
    if not name:
        raise ValueError("Category name cannot be empty.")
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT id FROM categories WHERE name = ? COLLATE NOCASE", (name,))
    if cursor.fetchone():
        conn.close()
        raise ValueError(f"Category '{name}' already exists.")
    base_id = slugify(name)
    cat_id = base_id
    counter = 1
    while True:
        cursor.execute("SELECT id FROM categories WHERE id = ?", (cat_id,))
        if not cursor.fetchone():
            break
        cat_id = f"{base_id}-{counter}"
        counter += 1

    cursor.execute("SELECT COALESCE(MAX(sort_order), 0) + 1 as next_order FROM categories")
    next_order = cursor.fetchone()["next_order"]

    cursor.execute("""
    INSERT INTO categories (id, name, color, sort_order)
    VALUES (?, ?, ?, ?)
    """, (cat_id, name, color or "#4A6B82", next_order))
    conn.commit()
    conn.close()
    return {"id": cat_id, "name": name, "color": color or "#4A6B82", "sort_order": next_order, "reel_count": 0}

def update_category(cat_id: str, name: Optional[str] = None, color: Optional[str] = None, sort_order: Optional[int] = None) -> Dict[str, Any]:
    if cat_id == "other" and name and name.strip().lower() != "other":
        raise ValueError("The 'Other' category cannot be renamed.")

    conn = get_db()
    cursor = conn.cursor()
    updates = []
    params = []
    if name is not None:
        clean_name = name.strip()
        if not clean_name:
            raise ValueError("Category name cannot be empty.")
        cursor.execute("SELECT id FROM categories WHERE name = ? COLLATE NOCASE AND id != ?", (clean_name, cat_id))
        if cursor.fetchone():
            conn.close()
            raise ValueError(f"Category '{clean_name}' already exists.")
        updates.append("name = ?")
        params.append(clean_name)
        cursor.execute("UPDATE reels SET category = ? WHERE category_id = ?", (clean_name, cat_id))

    if color is not None:
        updates.append("color = ?")
        params.append(color)

    if sort_order is not None:
        updates.append("sort_order = ?")
        params.append(sort_order)

    if updates:
        params.append(cat_id)
        cursor.execute(f"UPDATE categories SET {', '.join(updates)} WHERE id = ?", params)
        conn.commit()

    conn.close()
    return get_category_by_id(cat_id)

def delete_category(cat_id: str):
    if cat_id == "other":
        raise ValueError("The 'Other' category cannot be deleted.")
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("UPDATE reels SET category_id = 'other', category = 'Other' WHERE category_id = ?", (cat_id,))
    cursor.execute("DELETE FROM categories WHERE id = ?", (cat_id,))
    conn.commit()
    conn.close()

def reorder_categories(category_ids: List[str]):
    conn = get_db()
    cursor = conn.cursor()
    for idx, c_id in enumerate(category_ids):
        cursor.execute("UPDATE categories SET sort_order = ? WHERE id = ?", (idx, c_id))
    conn.commit()
    conn.close()

# Category Corrections
def record_category_correction(title: str, summary: str, chosen_category: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("""
    INSERT INTO category_corrections (title, summary, chosen_category)
    VALUES (?, ?, ?)
    """, (title, summary, chosen_category))
    conn.commit()
    conn.close()

def get_recent_category_corrections(limit: int = 5) -> List[Dict[str, str]]:
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("""
    SELECT title, summary, chosen_category
    FROM category_corrections
    ORDER BY created_at DESC
    LIMIT ?
    """, (limit,))
    rows = cursor.fetchall()
    conn.close()
    return [
        {"title": r["title"], "summary": r["summary"], "chosen_category": r["chosen_category"]}
        for r in rows
    ]

def serialize_field(val: Any, default: Any = None) -> str:
    """Safely serializes dicts, lists, Pydantic models to JSON string."""
    target = val if val is not None else default
    if hasattr(target, "model_dump"):
        target = target.model_dump()
    elif isinstance(target, list):
        target = [item.model_dump() if hasattr(item, "model_dump") else item for item in target]
    return json.dumps(target, default=lambda o: o.model_dump() if hasattr(o, "model_dump") else str(o))

# Reels Management
def save_reel(card: dict):
    conn = get_db()
    cursor = conn.cursor()
    media = card.get("media", {})
    if hasattr(media, "model_dump"):
        media = media.model_dump()

    reel_id = media.get("id") or card.get("id")
    category_name = card.get("category", "Other")
    category_id = card.get("category_id")

    if not category_id:
        cursor.execute("SELECT id FROM categories WHERE name = ? COLLATE NOCASE", (category_name,))
        match = cursor.fetchone()
        category_id = match["id"] if match else "other"

    source = card.get("source") or media.get("source") or "instagram"
    model_used = card.get("model_used", "gemini-2.5-flash")
    analysis_version = card.get("analysis_version", 1)
    note_type = card.get("note_type", "general")
    type_data = serialize_field(card.get("type_data"), {})
    key_moments = serialize_field(card.get("key_moments"), [])
    timestamped_transcript = serialize_field(card.get("timestamped_transcript"), [])

    cursor.execute("""
    INSERT OR REPLACE INTO reels (
        id, title, category, category_id, tags, summary, transcript, key_takeaways,
        extracted_resources, structured_content, uploader, channel,
        thumbnail, local_video_path, local_thumb_path, direct_video_url, webpage_url,
        status, suggested_new_category, source, model_used, processed_at, analysis_version,
        note_type, type_data, key_moments, timestamped_transcript
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?)
    """, (
        reel_id,
        card.get("title", "Untitled"),
        category_name,
        category_id,
        serialize_field(card.get("tags"), []),
        card.get("summary", ""),
        card.get("transcript", ""),
        serialize_field(card.get("key_takeaways"), []),
        serialize_field(card.get("extracted_resources"), []),
        serialize_field(card.get("structured_content"), {}),
        media.get("uploader", "Unknown"),
        media.get("channel", "unknown"),
        media.get("thumbnail", ""),
        media.get("local_video_path", f"media/videos/{reel_id}.mp4"),
        media.get("local_thumb_path", f"media/thumbs/{reel_id}.jpg"),
        media.get("direct_video_url", ""),
        media.get("webpage_url", ""),
        card.get("status", "unread"),
        card.get("suggested_new_category"),
        source,
        model_used,
        analysis_version,
        note_type,
        type_data,
        key_moments,
        timestamped_transcript
    ))
    conn.commit()

    # Sync FTS
    sync_reel_fts(conn, reel_id)
    conn.commit()
    conn.close()

def reprocess_reel_data(reel_id: str, ai_data: dict, model_used: str = "gemini-2.5-flash") -> Optional[Dict[str, Any]]:
    """
    Updates AI-generated fields during reprocessing.
    User-edited fields (user-chosen category_id, category, tags, status, reviewed_at)
    are strictly preserved and NEVER overwritten.
    """
    existing = get_reel_by_id(reel_id)
    if not existing:
        return None

    conn = get_db()
    cursor = conn.cursor()

    new_analysis_ver = (existing.get("analysis_version") or 1) + 1
    note_type = ai_data.get("note_type", "general")
    type_data = serialize_field(ai_data.get("type_data"), {})
    key_moments = serialize_field(ai_data.get("key_moments"), [])
    timestamped_transcript = serialize_field(ai_data.get("timestamped_transcript"), [])

    cursor.execute("""
    UPDATE reels SET
        title = ?,
        summary = ?,
        transcript = ?,
        key_takeaways = ?,
        extracted_resources = ?,
        structured_content = ?,
        model_used = ?,
        processed_at = CURRENT_TIMESTAMP,
        analysis_version = ?,
        note_type = ?,
        type_data = ?,
        key_moments = ?,
        timestamped_transcript = ?
    WHERE id = ?
    """, (
        ai_data.get("title", existing["title"]),
        ai_data.get("summary", existing["summary"]),
        ai_data.get("transcript", existing["transcript"]),
        serialize_field(ai_data.get("key_takeaways", existing["key_takeaways"]), []),
        serialize_field(ai_data.get("extracted_resources", existing["extracted_resources"]), []),
        serialize_field(ai_data.get("structured_content", existing["structured_content"]), {}),
        model_used,
        new_analysis_ver,
        note_type,
        type_data,
        key_moments,
        timestamped_transcript,
        reel_id
    ))
    conn.commit()

    sync_reel_fts(conn, reel_id)
    conn.commit()
    conn.close()

    return get_reel_by_id(reel_id)

def get_reels(search: str = "", category: str = "", status: str = "") -> List[Dict[str, Any]]:
    conn = get_db()
    cursor = conn.cursor()

    cleaned_search = search.strip() if search else ""
    fts_query = sanitize_fts5_query(cleaned_search) if cleaned_search else ""

    if fts_query:
        # FTS5 Search Query with bm25 ranking and field snippets
        base_query = """
        SELECT r.*,
               bm25(reels_fts, 5.0, 2.0, 1.5, 1.0, 1.0, 4.0, 1.5, 1.5) as fts_rank,
               snippet(reels_fts, 1, '<mark class="savyor-search-hit">', '</mark>', '...', 10) as snip_title,
               snippet(reels_fts, 2, '<mark class="savyor-search-hit">', '</mark>', '...', 12) as snip_summary,
               snippet(reels_fts, 3, '<mark class="savyor-search-hit">', '</mark>', '...', 12) as snip_notes,
               snippet(reels_fts, 4, '<mark class="savyor-search-hit">', '</mark>', '...', 12) as snip_transcript
        FROM reels_fts f
        JOIN reels r ON r.id = f.reel_id
        WHERE reels_fts MATCH ?
        """
        params = [fts_query]

        if category and category.lower() != "all":
            base_query += " AND (r.category = ? OR r.category_id = ?)"
            params.extend([category, category])

        if status and status.lower() not in ("all", ""):
            base_query += " AND r.status = ?"
            params.append(status.lower())
        else:
            base_query += " AND r.status != 'archived'"

        base_query += " ORDER BY fts_rank ASC"
        try:
            cursor.execute(base_query, params)
            rows = cursor.fetchall()
        except sqlite3.OperationalError:
            # Fallback if any FTS syntax issue
            rows = []
    else:
        # Standard query ordered by created_at DESC
        query = "SELECT * FROM reels WHERE 1=1"
        params = []
        if category and category.lower() != "all":
            query += " AND (category = ? OR category_id = ?)"
            params.extend([category, category])

        if status and status.lower() not in ("all", ""):
            query += " AND status = ?"
            params.append(status.lower())
        else:
            query += " AND status != 'archived'"

        query += " ORDER BY created_at DESC"
        cursor.execute(query, params)
        rows = cursor.fetchall()

    results = []
    for r in rows:
        r_keys = r.keys()
        r_source = r["source"] if ("source" in r_keys and r["source"]) else "instagram"

        # Determine best search snippet if searching
        search_snippet = None
        if fts_query and "snip_title" in r_keys:
            if r["snip_title"] and "<mark" in r["snip_title"]:
                search_snippet = {"field": "Title", "html": r["snip_title"]}
            elif r["snip_summary"] and "<mark" in r["snip_summary"]:
                search_snippet = {"field": "Summary", "html": r["snip_summary"]}
            elif r["snip_notes"] and "<mark" in r["snip_notes"]:
                search_snippet = {"field": "Notes", "html": r["snip_notes"]}
            elif r["snip_transcript"] and "<mark" in r["snip_transcript"]:
                search_snippet = {"field": "Transcript", "html": r["snip_transcript"]}

        results.append({
            "id": r["id"],
            "title": r["title"],
            "category": r["category"],
            "category_id": r["category_id"],
            "source": r_source,
            "tags": json.loads(r["tags"] or "[]"),
            "summary": r["summary"],
            "transcript": r["transcript"],
            "status": r["status"] or "unread",
            "reviewed_at": r["reviewed_at"],
            "suggested_new_category": r["suggested_new_category"],
            "key_takeaways": json.loads(r["key_takeaways"] or "[]"),
            "extracted_resources": json.loads(r["extracted_resources"] or "[]"),
            "structured_content": json.loads(r["structured_content"] or "{}"),
            "note_type": r["note_type"] if "note_type" in r_keys and r["note_type"] else "general",
            "type_data": json.loads(r["type_data"]) if "type_data" in r_keys and r["type_data"] else {},
            "key_moments": json.loads(r["key_moments"]) if "key_moments" in r_keys and r["key_moments"] else [],
            "timestamped_transcript": json.loads(r["timestamped_transcript"]) if "timestamped_transcript" in r_keys and r["timestamped_transcript"] else [],
            "model_used": r["model_used"] if "model_used" in r_keys else "gemini-2.5-flash",
            "processed_at": r["processed_at"] if "processed_at" in r_keys else None,
            "analysis_version": r["analysis_version"] if "analysis_version" in r_keys else 1,
            "search_snippet": search_snippet,
            "media": {
                "id": r["id"],
                "uploader": r["uploader"],
                "channel": r["channel"],
                "source": r_source,
                "thumbnail": r["thumbnail"],
                "local_video_path": r["local_video_path"],
                "local_thumb_path": r["local_thumb_path"],
                "proxy_thumb_url": f"/api/proxy/thumb/{r['id']}",
                "stream_url": f"/api/proxy/video/{r['id']}",
                "direct_video_url": r["direct_video_url"],
                "webpage_url": r["webpage_url"]
            },
            "created_at": r["created_at"]
        })
    conn.close()
    return results

def get_reel_by_id(reel_id: str) -> Optional[Dict[str, Any]]:
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM reels WHERE id = ?", (reel_id,))
    r = cursor.fetchone()
    conn.close()
    if not r:
        return None
    r_keys = r.keys()
    r_source = r["source"] if ("source" in r_keys and r["source"]) else "instagram"
    return {
        "id": r["id"],
        "title": r["title"],
        "category": r["category"],
        "category_id": r["category_id"],
        "source": r_source,
        "tags": json.loads(r["tags"] or "[]"),
        "summary": r["summary"],
        "transcript": r["transcript"],
        "status": r["status"] or "unread",
        "reviewed_at": r["reviewed_at"],
        "suggested_new_category": r["suggested_new_category"],
        "key_takeaways": json.loads(r["key_takeaways"] or "[]"),
        "extracted_resources": json.loads(r["extracted_resources"] or "[]"),
        "structured_content": json.loads(r["structured_content"] or "{}"),
        "note_type": r["note_type"] if "note_type" in r_keys and r["note_type"] else "general",
        "type_data": json.loads(r["type_data"]) if "type_data" in r_keys and r["type_data"] else {},
        "key_moments": json.loads(r["key_moments"]) if "key_moments" in r_keys and r["key_moments"] else [],
        "timestamped_transcript": json.loads(r["timestamped_transcript"]) if "timestamped_transcript" in r_keys and r["timestamped_transcript"] else [],
        "model_used": r["model_used"] if "model_used" in r_keys else "gemini-2.5-flash",
        "processed_at": r["processed_at"] if "processed_at" in r_keys else None,
        "analysis_version": r["analysis_version"] if "analysis_version" in r_keys else 1,
        "media": {
            "id": r["id"],
            "uploader": r["uploader"],
            "channel": r["channel"],
            "source": r_source,
            "thumbnail": r["thumbnail"],
            "local_video_path": r["local_video_path"],
            "local_thumb_path": r["local_thumb_path"],
            "proxy_thumb_url": f"/api/proxy/thumb/{r['id']}",
            "stream_url": f"/api/proxy/video/{r['id']}",
            "direct_video_url": r["direct_video_url"],
            "webpage_url": r["webpage_url"]
        },
        "created_at": r["created_at"]
    }

def update_reel(reel_id: str, updates: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    existing = get_reel_by_id(reel_id)
    if not existing:
        return None

    conn = get_db()
    cursor = conn.cursor()
    fields = []
    params = []

    if "category_id" in updates:
        cat_id = updates["category_id"]
        cursor.execute("SELECT name FROM categories WHERE id = ?", (cat_id,))
        match = cursor.fetchone()
        if match:
            fields.extend(["category_id = ?", "category = ?"])
            params.extend([cat_id, match["name"]])
            if existing["category"] != match["name"]:
                record_category_correction(existing["title"], existing["summary"], match["name"])

    if "tags" in updates and isinstance(updates["tags"], list):
        fields.append("tags = ?")
        params.append(json.dumps(updates["tags"]))

    if "status" in updates:
        st = updates["status"].lower()
        if st in ["unread", "reviewed", "tried", "archived"]:
            fields.append("status = ?")
            params.append(st)
            if st == "reviewed" and not existing["reviewed_at"]:
                fields.append("reviewed_at = CURRENT_TIMESTAMP")

    if "suggested_new_category" in updates:
        fields.append("suggested_new_category = ?")
        params.append(updates["suggested_new_category"])

    if "extracted_resources" in updates:
        fields.append("extracted_resources = ?")
        params.append(json.dumps(updates["extracted_resources"]))

    if fields:
        params.append(reel_id)
        cursor.execute(f"UPDATE reels SET {', '.join(fields)} WHERE id = ?", params)
        conn.commit()

    # Sync FTS
    sync_reel_fts(conn, reel_id)
    conn.commit()
    conn.close()

    return get_reel_by_id(reel_id)

def delete_reel(reel_id: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM reels_fts WHERE reel_id = ?", (reel_id,))
    cursor.execute("DELETE FROM reels WHERE id = ?", (reel_id,))
    conn.commit()
    conn.close()

# Job Functions
def create_job(job_id: str, url: str):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("""
    INSERT INTO jobs (id, url, status, progress) VALUES (?, ?, 'queued', 'Job queued for processing...')
    """, (job_id, url))
    conn.commit()
    conn.close()

def update_job(job_id: str, status: str, progress: str, error: str = None, reel_id: str = None):
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("""
    UPDATE jobs SET status = ?, progress = ?, error = ?, reel_id = COALESCE(?, reel_id), updated_at = CURRENT_TIMESTAMP
    WHERE id = ?
    """, (status, progress, error, reel_id, job_id))
    conn.commit()
    conn.close()

def get_job(job_id: str) -> Optional[Dict[str, Any]]:
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM jobs WHERE id = ?", (job_id,))
    r = cursor.fetchone()
    conn.close()
    if not r:
        return None
    return {
        "id": r["id"],
        "url": r["url"],
        "reel_id": r["reel_id"],
        "status": r["status"],
        "progress": r["progress"],
        "error": r["error"],
        "created_at": r["created_at"],
        "updated_at": r["updated_at"]
    }

# Ensure DB initialized
init_db()
