import os
import subprocess

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")

sql = """
INSERT OR REPLACE INTO reels (
    shortcode, original_url, clean_url, creator, source_language,
    title, summary, category, tags, transcript, original_transcript,
    key_takeaways, key_moments, structured_content, extracted_resources,
    caveats, use_when, user_note, visual_memory, is_starred, thumbnail_path, status, processing_state,
    created_at, updated_at
) VALUES 
('ReelExtra01', 'https://www.instagram.com/reel/Extra01/', 'https://www.instagram.com/reel/Extra01/', 'creator_alpha', 'English',
 'Advanced Compose Architecture & State Hoisting', 'Deep architectural patterns for Jetpack Compose state isolation.', 'Tech & Code', '["compose"]',
 'Transcript...', NULL, '[]', '[]', '{}', '[]', '[]', '', '', '', 0, '', 'UNREAD', 'COMPLETED', 1700000001000, 1700000001000),
('ReelExtra02', 'https://www.instagram.com/reel/Extra02/', 'https://www.instagram.com/reel/Extra02/', 'creator_beta', 'English',
 'Artisanal Sourdough Fermentation Timings', 'Step by step timetable for 80% hydration sourdough loaf.', 'Recipes & Cooking', '["baking"]',
 'Transcript...', NULL, '[]', '[]', '{}', '[]', '[]', '', '', '', 1, '', 'REVIEWED', 'COMPLETED', 1700000002000, 1700000002000),
('ReelExtra03', 'https://www.instagram.com/reel/Extra03/', 'https://www.instagram.com/reel/Extra03/', 'creator_gamma', 'English',
 'Minimalist Desk Setup with Ambient OLED Glow', 'Ergonomic workspace optimization for high productivity.', 'Productivity', '["desk"]',
 'Transcript...', NULL, '[]', '[]', '{}', '[]', '[]', '', '', '', 0, '', 'UNREAD', 'COMPLETED', 1700000003000, 1700000003000),
('ReelExtra04', 'https://www.instagram.com/reel/Extra04/', 'https://www.instagram.com/reel/Extra04/', 'creator_delta', 'English',
 'Fluid Kinetic Typography in After Effects', 'Keyframe curves and easing techniques for premium motion.', 'Motion Design', '["motion"]',
 'Transcript...', NULL, '[]', '[]', '{}', '[]', '[]', '', '', '', 0, '', 'TRIED', 'COMPLETED', 1700000004000, 1700000004000);
"""

with open("temp_extra.sql", "w", encoding="utf-8") as f:
    f.write(sql)

subprocess.run([ADB, "push", "temp_extra.sql", "/data/local/tmp/temp_extra.sql"], check=True)
cmd = "cat /data/local/tmp/temp_extra.sql | sqlite3 /data/data/com.savyor.app/databases/savyor.db"
subprocess.run([ADB, "shell", "su", "0", "sh", "-c", f'"{cmd}"'], check=True)
print("Extra reels inserted successfully.")
