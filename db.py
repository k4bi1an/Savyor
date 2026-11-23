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

def get_db():
    conn = sqlite3.connect(get_db_path())
    conn.row_factory = sqlite3.Row
    return conn

def slugify(text: str) -> str:
    slug = re.sub(r'[^a-zA-Z0-9]+', '-', text.lower()).strip('-')
    return slug or "cat"

def init_db():
    conn = get_db()
    cursor = conn.cursor()
    
    # Categories Table
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

    # Category Corrections Table (for Gemini prompt memory)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS category_corrections (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        summary TEXT NOT NULL,
        chosen_category TEXT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)
    
    # Reels Table
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
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)
    
    # Jobs Table for Async Background Processing
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS jobs (
        id TEXT PRIMARY KEY,
        url TEXT NOT NULL,
        reel_id TEXT,
        status TEXT NOT NULL, -- queued, downloading, analyzing, done, failed
        progress TEXT NOT NULL,
        error TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)
    
    # Migrations for existing reels table
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

    # Migrate category_id for reels where category_id IS NULL
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
    
    # Check duplicate
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
        # Check duplicate
        cursor.execute("SELECT id FROM categories WHERE name = ? COLLATE NOCASE AND id != ?", (clean_name, cat_id))
        if cursor.fetchone():
            conn.close()
            raise ValueError(f"Category '{clean_name}' already exists.")
        updates.append("name = ?")
        params.append(clean_name)
        
        # Also update category text in reels
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

    # Move reels to 'Other'
    cursor.execute("UPDATE reels SET category_id = 'other', category = 'Other' WHERE category_id = ?", (cat_id,))
    
    # Delete category
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

# Category Corrections (Learning from user updates)
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

# Reels Management
def save_reel(card: dict):
    conn = get_db()
    cursor = conn.cursor()
    media = card.get("media", {})
    reel_id = media.get("id") or card.get("id")
    category_name = card.get("category", "Other")
    category_id = card.get("category_id")
    
    # Resolve category_id if not provided
    if not category_id:
        cursor.execute("SELECT id FROM categories WHERE name = ? COLLATE NOCASE", (category_name,))
        match = cursor.fetchone()
        category_id = match["id"] if match else "other"
    
    source = card.get("source") or media.get("source") or "instagram"

    cursor.execute("""
    INSERT OR REPLACE INTO reels (
        id, title, category, category_id, tags, summary, transcript, key_takeaways,
        extracted_resources, structured_content, uploader, channel,
        thumbnail, local_video_path, local_thumb_path, direct_video_url, webpage_url,
        status, suggested_new_category, source
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        reel_id,
        card.get("title", "Untitled"),
        category_name,
        category_id,
        json.dumps(card.get("tags", [])),
        card.get("summary", ""),
        card.get("transcript", ""),
        json.dumps(card.get("key_takeaways", [])),
        json.dumps(card.get("extracted_resources", [])),
        json.dumps(card.get("structured_content", {})),
        media.get("uploader", "Unknown"),
        media.get("channel", "unknown"),
        media.get("thumbnail", ""),
        media.get("local_video_path", f"media/videos/{reel_id}.mp4"),
        media.get("local_thumb_path", f"media/thumbs/{reel_id}.jpg"),
        media.get("direct_video_url", ""),
        media.get("webpage_url", ""),
        card.get("status", "unread"),
        card.get("suggested_new_category"),
        source
    ))
    conn.commit()
    conn.close()

def get_reels(search: str = "", category: str = "", status: str = "") -> List[Dict[str, Any]]:
    conn = get_db()
    cursor = conn.cursor()
    query = "SELECT * FROM reels WHERE 1=1"
    params = []
    
    if category and category.lower() != "all":
        # Support filter by category name or id
        query += " AND (category = ? OR category_id = ?)"
        params.extend([category, category])

    if status and status.lower() not in ("all", ""):
        query += " AND status = ?"
        params.append(status.lower())
    else:
        # Default behavior (empty or 'all'): hide archived reels unless specifically requested
        query += " AND status != 'archived'"
        
    if search:
        query += " AND (title LIKE ? OR summary LIKE ? OR tags LIKE ? OR transcript LIKE ? OR uploader LIKE ? OR channel LIKE ?)"
        s = f"%{search}%"
        params.extend([s, s, s, s, s, s])
        
    query += " ORDER BY created_at DESC"
    cursor.execute(query, params)
    rows = cursor.fetchall()
    
    results = []
    for r in rows:
        r_source = r["source"] if ("source" in r.keys() and r["source"]) else "instagram"
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
    r_source = r["source"] if ("source" in r.keys() and r["source"]) else "instagram"
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
            # Record category correction if changed
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

    if fields:
        params.append(reel_id)
        cursor.execute(f"UPDATE reels SET {', '.join(fields)} WHERE id = ?", params)
        conn.commit()

    conn.close()
    return get_reel_by_id(reel_id)

def delete_reel(reel_id: str):
    conn = get_db()
    cursor = conn.cursor()
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
