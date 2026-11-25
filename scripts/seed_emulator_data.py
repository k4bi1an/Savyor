import os
import json
import sqlite3
import subprocess
from PIL import Image, ImageDraw, ImageFont

ADB = os.path.expandvars(r"$LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")

def create_crisp_frame(path, title, subtitle, bg_color, accent_color):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img = Image.new("RGB", (720, 960), color=bg_color)
    draw = ImageDraw.Draw(img)

    # Draw modern grid / abstract card
    draw.rectangle([40, 60, 680, 900], outline=accent_color, width=3)
    draw.rectangle([60, 80, 660, 240], fill=accent_color)

    # Text
    draw.text((80, 110), title, fill=(255, 255, 255))
    draw.text((80, 160), subtitle, fill=(240, 240, 240))
    draw.text((80, 320), "SAVYOR AI SNAPSHOT", fill=accent_color)
    draw.text((80, 360), "120Hz High-Density Reference Note", fill=(180, 180, 180))

    img.save(path, "WEBP", quality=90)

def main():
    local_staging = "build_staging_snapshots"
    os.makedirs(local_staging, exist_ok=True)

    reels = [
        {
            "shortcode": "DdKXX_mTXLx",
            "title": "Spin Up a Local PostgreSQL Vector DB with pgvector in 1 Command",
            "category": "Tech & Code",
            "creator": "tech_bytes",
            "source_language": "English",
            "use_when": "Testing RAG vector embeddings locally without cloud database bills",
            "summary": "Execute docker run with pgvector/pgvector:pg16 to launch a local PostgreSQL database with vector indexing pre-configured on port 5432.",
            "user_note": "Tested locally on Docker Desktop v4.30. Port 5432 works out of the box with pgvector extension enabled. Keep container memory capped at 2GB in docker-compose.yml.",
            "bg": (28, 35, 45),
            "accent": (74, 96, 122),
            "moments": [
                (2, "Terminal docker run execution"),
                (8, "pgvector extension verification")
            ]
        },
        {
            "shortcode": "DeoRFIMOVtn",
            "title": "Cinematic 60fps AI Motion Transitions in After Effects",
            "category": "Motion Design",
            "creator": "motion_director",
            "source_language": "Hindi + English",
            "use_when": "Refining fast cinematic UI or promo video transitions",
            "summary": "A comprehensive workflow guide for creating fluid 60fps keyframe transitions with custom cubic bezier curves and motion blur compensation.",
            "user_note": "Outgoing tangent 82% is ideal for mobile UI sizzle reels. Disable motion blur for vector icon layers to avoid edge blurring.",
            "bg": (25, 45, 40),
            "accent": (116, 185, 162),
            "moments": [
                (6, "Speed graph custom cubic bezier curve"),
                (14, "Composition motion blur settings")
            ]
        },
        {
            "shortcode": "DetWpkASgsT",
            "title": "Authentic Madurai Kari Dosa (Spicy Mutton Crepe)",
            "category": "Recipes & Cooking",
            "creator": "madurai_samayal",
            "source_language": "Tamil",
            "use_when": "Preparing a high-protein Sunday South Indian brunch",
            "summary": "Direct step-by-step method to cook traditional Madurai street-style Kari Dosa layered with spiced minced mutton, whipped eggs, and fragrant shallot masala.",
            "user_note": "Used 100g minced lamb instead of mutton; cooked keema for 15 mins beforehand. Added 1/2 tsp crushed black pepper on top before flipping.",
            "bg": (55, 30, 25),
            "accent": (179, 84, 70),
            "moments": [
                (4, "Spiced minced mutton consistency"),
                (12, "Layering egg and meat over thick batter")
            ]
        }
    ]

    # Generate snapshots
    for r in reels:
        sc = r["shortcode"]
        snap_dir = os.path.join(local_staging, sc)
        os.makedirs(snap_dir, exist_ok=True)
        thumb_path = os.path.join(snap_dir, "thumb.webp")
        create_crisp_frame(thumb_path, r["title"][:30], f"@{r['creator']}", r["bg"], r["accent"])

        for idx, (t, lbl) in enumerate(r["moments"]):
            m_path = os.path.join(snap_dir, f"moment_{idx}.webp")
            create_crisp_frame(m_path, f"Moment [{t}s]", lbl, r["bg"], r["accent"])

    print("Generated all local frames in staging.")

    # Push to device
    subprocess.run([ADB, "shell", "su", "0", "mkdir", "-p", "/data/data/com.savyor.app/files/snapshots"], check=True)
    subprocess.run([ADB, "push", local_staging + "/.", "/data/local/tmp/snapshots_staging"], check=True)
    subprocess.run([ADB, "shell", "su", "0", "cp", "-r", "/data/local/tmp/snapshots_staging/*", "/data/data/com.savyor.app/files/snapshots/"], check=True)
    subprocess.run([ADB, "shell", "su", "0", "chmod", "-R", "777", "/data/data/com.savyor.app/files/snapshots"], check=True)

    # Insert into SQLite database
    sql_commands = []

    # Insert custom categories
    sql_commands.append("INSERT OR REPLACE INTO categories (id, name, is_default, display_order, color_hex) VALUES (6, 'Motion Design', 0, 10, '#74B9A2');")
    sql_commands.append("INSERT OR REPLACE INTO categories (id, name, is_default, display_order, color_hex) VALUES (7, 'Coffee & Brews', 0, 11, '#80632E');")
    sql_commands.append("INSERT OR REPLACE INTO categories (id, name, is_default, display_order, color_hex) VALUES (8, 'Productivity', 0, 12, '#9E5A63');")

    for r in reels:
        sc = r["shortcode"]
        thumb_on_device = f"/data/user/0/com.savyor.app/files/snapshots/{sc}/thumb.webp"
        moments_json = json.dumps([
            {"timestampSec": t, "label": lbl, "webpPath": f"/data/user/0/com.savyor.app/files/snapshots/{sc}/moment_{i}.webp"}
            for i, (t, lbl) in enumerate(r["moments"])
        ])
        takeaways_json = json.dumps([
            "Clear, direct actionable knowledge point 1",
            "Tested workflow with zero hallucination",
            "High performance parameter settings"
        ])
        structured_json = json.dumps({
            "noteType": "tutorial",
            "steps": [{"stepNumber": 1, "instruction": "Step 1 details"}, {"stepNumber": 2, "instruction": "Step 2 details"}],
            "commands": ["docker run ...", "npm install"],
            "items": ["Item A recommendation", "Item B recommendation"]
        })
        resources_json = json.dumps([
            {"name": "Official Documentation", "url": "https://example.com/docs", "description": "Reference docs"}
        ])
        caveats_json = json.dumps(["Requires local environment setup"])

        sql = f"""
        INSERT OR REPLACE INTO reels (
            shortcode, original_url, clean_url, creator, source_language,
            title, summary, category, tags, transcript, original_transcript,
            key_takeaways, key_moments, structured_content, extracted_resources,
            caveats, use_when, user_note, visual_memory, is_starred, thumbnail_path, status, processing_state,
            created_at, updated_at
        ) VALUES (
            '{sc}', 'https://www.instagram.com/reel/{sc}/', 'https://www.instagram.com/reel/{sc}/', '{r["creator"]}', '{r["source_language"]}',
            '{r["title"].replace("'", "''")}', '{r["summary"].replace("'", "''")}', '{r["category"]}', '["tech"]',
            '[0:00] Overview transcript...', NULL,
            '{takeaways_json.replace("'", "''")}', '{moments_json.replace("'", "''")}', '{structured_json.replace("'", "''")}', '{resources_json.replace("'", "''")}',
            '{caveats_json.replace("'", "''")}', '{r["use_when"].replace("'", "''")}', '{r["user_note"].replace("'", "''")}', 'Studio desk with warm ambient lighting', 1, '{thumb_on_device}',
            'UNREAD', 'COMPLETED', 1700000000000, 1700000000000
        );
        """
        sql_commands.append(sql)

    full_sql = "\n".join(sql_commands)
    with open("seed_temp.sql", "w", encoding="utf-8") as f:
        f.write(full_sql)

    subprocess.run([ADB, "push", "seed_temp.sql", "/data/local/tmp/seed.sql"], check=True)
    subprocess.run([ADB, "shell", "su", "0", "sh", "-c", "cat /data/local/tmp/seed.sql | sqlite3 /data/data/com.savyor.app/databases/savyor.db"], check=True)
    print("Database seeded successfully with reels and custom categories.")

if __name__ == "__main__":
    main()
