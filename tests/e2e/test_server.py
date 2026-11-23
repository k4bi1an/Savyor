import os
import sys
import shutil
import tempfile
import uvicorn
from contextlib import asynccontextmanager
from typing import Dict, Any, Optional, Callable

# Ensure project root is in sys.path
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

# Set environment before imports
TEST_DB_PATH = os.path.join(os.path.dirname(__file__), "test_reels.db")
os.environ["SAVYOR_DB_PATH"] = TEST_DB_PATH
os.environ["SAVYOR_PASSCODE"] = "test_custom_passcode_2026"
os.environ["SAVYOR_SECRET_KEY"] = "test_custom_secret_key_99999"
os.environ["CORS_ORIGINS"] = "http://localhost:8001,http://127.0.0.1:8001"

import db
import process_reel
from server import app

# Create dummy media for test streaming
os.makedirs("media/videos", exist_ok=True)
os.makedirs("media/thumbs", exist_ok=True)

SAMPLE_VIDEO = "media/videos/test_sample.mp4"
SAMPLE_THUMB = "media/thumbs/test_sample.jpg"

if os.path.exists("media/videos/DfBEEFBo52M.mp4"):
    shutil.copy("media/videos/DfBEEFBo52M.mp4", SAMPLE_VIDEO)
else:
    with open(SAMPLE_VIDEO, "wb") as f:
        f.write(b"\x00\x00\x00 ftypisom" + b"\x00" * 1024)

if os.path.exists("media/thumbs/DfBEEFBo52M.jpg"):
    shutil.copy("media/thumbs/DfBEEFBo52M.jpg", SAMPLE_THUMB)
else:
    with open(SAMPLE_THUMB, "wb") as f:
        f.write(b"\xFF\xD8\xFF\xE0\x00\x10JFIF" + b"\x00" * 256)

def seed_test_db():
    db.init_db()
    conn = db.get_db()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM reels")
    cursor.execute("DELETE FROM categories")
    conn.commit()
    conn.close()
    db.init_db()

    for seed in ["reel_seed_1", "reel_seed_2"]:
        shutil.copy(SAMPLE_VIDEO, f"media/videos/{seed}.mp4")
        shutil.copy(SAMPLE_THUMB, f"media/thumbs/{seed}.jpg")

    db.save_reel({
        "id": "reel_seed_1",
        "title": "Mastering Modern C++ Concurrency with Virtual Threads",
        "category": "Tech & Code",
        "category_id": "tech-code",
        "tags": ["cpp", "concurrency", "performance"],
        "summary": "An in-depth guide on modern concurrency primitives, comparing thread pools with virtual lightweight tasks.",
        "transcript": "Today we explore memory fences, atomic compare exchange, and how lightweight coroutines outperform thread pools.",
        "key_takeaways": ["Use lock-free data structures when possible", "Coroutines yield negligible memory footprint"],
        "extracted_resources": [
            {"name": "CppCoro", "type": "github_repo", "url": "https://github.com/lewissbaker/cppcoro", "description": "C++ coroutine library"}
        ],
        "structured_content": {
            "type": "code",
            "heading": "Example Coroutine Task",
            "items_or_markdown": "```cpp\ntask<int> async_compute() { co_return 42; }\n```"
        },
        "status": "unread",
        "media": {
            "id": "reel_seed_1",
            "uploader": "Systems Programmer",
            "channel": "sys_coder",
            "local_video_path": f"media/videos/reel_seed_1.mp4",
            "local_thumb_path": f"media/thumbs/reel_seed_1.jpg",
            "direct_video_url": "https://example.com/video1.mp4",
            "webpage_url": "https://www.instagram.com/reel/reel_seed_1/"
        }
    })

    db.save_reel({
        "id": "reel_seed_2",
        "title": "Sourdough Bread Scoring and Open Crumb Technique",
        "category": "Food",
        "category_id": "food",
        "tags": ["baking", "sourdough", "bread"],
        "summary": "Essential techniques for achieving maximum oven spring and blistering ears on artisanal sourdough boules.",
        "transcript": "Make sure your dutch oven is preheated to 250 degrees celsius for at least forty-five minutes before scoring.",
        "key_takeaways": ["Preheat iron dutch oven thoroughly", "Slash at a 30 degree angle with a curved razor blade"],
        "extracted_resources": [
            {"name": "Artisan Bread Lame", "type": "tool", "url": "https://example.com/lame", "description": "Curved scoring blade"}
        ],
        "structured_content": {
            "type": "recipe",
            "heading": "Baking Schedule",
            "items_or_markdown": "1. 20 mins lid on\n2. 20 mins lid off"
        },
        "status": "reviewed",
        "media": {
            "id": "reel_seed_2",
            "uploader": "Baker Jane",
            "channel": "jane_bakes",
            "local_video_path": f"media/videos/reel_seed_2.mp4",
            "local_thumb_path": f"media/thumbs/reel_seed_2.jpg",
            "direct_video_url": "https://example.com/video2.mp4",
            "webpage_url": "https://www.instagram.com/reel/reel_seed_2/"
        }
    })

seed_test_db()

# Fake yt-dlp & Gemini mocks
def mock_download_reel(url: str, shortcode: str, progress_callback: Optional[Callable[[str], None]] = None) -> Dict[str, Any]:
    if "fail-ytdlp" in url or "private" in url:
        raise RuntimeError("Instagram video is private or unavailable.")
    
    if progress_callback:
        progress_callback("Connecting to Instagram & fetching stream...")
        progress_callback("Video stream downloaded successfully.")

    v_path = f"media/videos/{shortcode}.mp4"
    t_path = f"media/thumbs/{shortcode}.jpg"
    shutil.copy(SAMPLE_VIDEO, v_path)
    shutil.copy(SAMPLE_THUMB, t_path)

    return {
        "id": shortcode,
        "uploader": "Mocked Creator",
        "channel": "mock_creator",
        "description": "Mocked reel description with test details.",
        "thumbnail": "https://example.com/thumb.jpg",
        "local_video_path": v_path,
        "local_thumb_path": t_path,
        "direct_video_url": "https://example.com/stream.mp4",
        "webpage_url": f"https://www.instagram.com/reel/{shortcode}/",
    }

def mock_analyze_with_gemini(media_info: Dict[str, Any], progress_callback: Optional[Callable[[str], None]] = None) -> process_reel.ReelKnowledgeCard:
    shortcode = media_info.get("id", "")
    if "fail-gemini" in shortcode:
        raise RuntimeError("Gemini Files API quota exceeded (ResourceExhausted: 429).")

    if progress_callback:
        progress_callback("Uploading video to Gemini Files API...")
        progress_callback("Gemini is watching video & transcribing audio...")

    return process_reel.ReelKnowledgeCard(
        title=f"Automated Test Reel {shortcode}",
        category="Tech & Code",
        suggested_new_category=None,
        tags=["automation", "testing", "fastapi"],
        summary="A verified automated test reel processed through mocked pipeline.",
        transcript="This transcript contains the unique secret keyword xylophone for search verification.",
        key_takeaways=["Testing makes software robust", "Mocking eliminates network flakiness"],
        extracted_resources=[
            process_reel.ExtractedResource(
                name="Playwright Test Runner",
                type="tool",
                url="https://playwright.dev",
                description="End-to-end testing framework."
            )
        ],
        structured_content=process_reel.StructuredContent(
            type="tutorial",
            heading="Test Execution",
            items_or_markdown="Run tests with `pytest tests/e2e`."
        )
    )

# Monkeypatch process_reel for the test server
process_reel.download_reel = mock_download_reel
process_reel.analyze_with_gemini = mock_analyze_with_gemini

import server

@app.post("/api/test/reset-rate-limit")
def api_reset_rate_limit():
    server.login_attempts.clear()
    return {"status": "ok"}

@app.post("/api/test/reset-db")
def api_reset_db():
    seed_test_db()
    server.login_attempts.clear()
    return {"status": "ok"}

if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8001, log_level="warning")
