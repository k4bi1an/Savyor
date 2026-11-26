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
) VALUES (
    'ProcDemo01', 'https://www.instagram.com/reel/ProcDemo01/', 'https://www.instagram.com/reel/ProcDemo01/', 'ai_lab', 'English',
    'Analyzing Local LLM Quantization Benchmarks on Mobile NPUs', 'Extracting multimodal frames and structured note summaries from video stream...', 'Tech & Code', '["ai", "llm"]',
    'Processing video audio and transcription...', NULL,
    '[]', '[]', '{}', '[]',
    '[]', 'In progress analysis', '', '', 0, '',
    'PROCESSING', 'ANALYZING', 1700000000000, 1700000000000
);
"""

with open("temp_proc.sql", "w", encoding="utf-8") as f:
    f.write(sql)

subprocess.run([ADB, "push", "temp_proc.sql", "/data/local/tmp/temp_proc.sql"], check=True)
cmd = "cat /data/local/tmp/temp_proc.sql | sqlite3 /data/data/com.savyor.app/databases/savyor.db"
subprocess.run([ADB, "shell", "su", "0", "sh", "-c", f'"{cmd}"'], check=True)

query_cmd = "sqlite3 /data/data/com.savyor.app/databases/savyor.db 'SELECT shortcode, status, processing_state FROM reels;'"
res = subprocess.run([ADB, "shell", "su", "0", "sh", "-c", f'"{query_cmd}"'], capture_output=True, text=True, check=True)
print("Reels in DB:\n", res.stdout)
