# 🔖 Savyor — Full Project Export

This document contains the complete architecture, source code, data schema, and API structure for **Savyor** ("Save the reel. Keep the idea."), an app that turns Instagram Reels into a personal knowledge library using Google Gemini, FastAPI, and React 19.

---

## 📌 Project Architecture Overview

* **Goal:** A user shares an Instagram Reel URL (via in-app paste or phone share sheet). The app extracts direct progressive MP4 streams, transcribes audio/captions with Gemini 3.5 Flash, extracts tools/GitHub repositories/takeaways, and saves them into an interactive Pinterest-style grid with a Notion-style inspection drawer.
* **Backend:** FastAPI (Python 3.15) with HTTP 206 Partial Content Range streaming proxy (`/api/proxy/video/{id}`) and image proxy (`/api/proxy/thumb/{id}`) to bypass Instagram hotlinking & adblocker restrictions.
* **Extraction:** `yt-dlp` extracts progressive 720p MP4 links, uploader metadata, and captions.
* **AI Intelligence:** Google Gemini 3.5 Flash Lite outputs structured JSON with categorization, takeaways, and extracted resource entities.
* **Database:** SQLite (`reels.db`).
* **Frontend:** React 19, Tailwind CSS v4, Lucide icons, custom vertical reels video player with range seeking, auto-looping, and playback speed.

---

## 📂 Source Code

### 1. `process_reel.py` (AI Extraction Engine)

```python
import os
import json
import yt_dlp
from google import genai
from google.genai import types

API_KEY = os.getenv("GEMINI_API_KEY")

EXTRACTION_SYSTEM_PROMPT = """
You are the AI engine of an elite knowledge app that combines Pinterest (aesthetic cards) and Notion (deep structured notes) for Instagram Reels.

Return a STRICT JSON object matching this schema:
{
  "title": "A crisp, descriptive title (remove clickbait; summarize exactly what is inside)",
  "category": "One of: Tech & Code, Health & Fitness, Business & Finance, Productivity & Tools, Cooking & Recipes, Creative & Design, Life & Mindset, Other",
  "tags": ["tag1", "tag2", "tag3", "tag4"],
  "summary": "A 2-3 sentence executive summary explaining what the reel covers and why it matters",
  "key_takeaways": [
    "Actionable bullet point 1",
    "Actionable bullet point 2",
    "Actionable bullet point 3"
  ],
  "extracted_resources": [
    {
      "name": "Name of tool, repo, product, or concept",
      "type": "github_repo | tool | product | website | concept",
      "url": "Direct URL or canonical link if known, or empty string",
      "description": "What it is and why it was mentioned"
    }
  ],
  "structured_content": {
    "type": "code | recipe | checklist | tutorial | analysis",
    "heading": "Heading for this section",
    "items_or_markdown": "Full formatted markdown content"
  }
}
"""

def extract_and_process(url: str):
    ydl_opts = {'skip_download': True, 'quiet': True, 'no_warnings': True}
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=False)
        meta = {
            'id': info.get('id'),
            'title': info.get('title'),
            'description': info.get('description'),
            'uploader': info.get('uploader'),
            'channel': info.get('channel'),
            'thumbnail': info.get('thumbnail'),
            'webpage_url': info.get('webpage_url'),
            'direct_video_url': info.get('url'),
        }
    
    client = genai.Client(api_key=API_KEY)
    prompt = f"Analyze this reel:\nCreator: {meta['uploader']} (@{meta['channel']})\nURL: {meta['webpage_url']}\nCaption:\n{meta['description']}"
    
    response = client.models.generate_content(
        model="gemini-3.5-flash-lite",
        contents=prompt,
        config=types.GenerateContentConfig(
            system_instruction=EXTRACTION_SYSTEM_PROMPT,
            response_mime_type="application/json"
        )
    )
    card = json.loads(response.text)
    card["media"] = meta
    return card
```

---

### 2. `server.py` (FastAPI Server with Range Streaming & Thumbnail Proxy)

```python
import os
import requests
from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.responses import StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
import db
import process_reel

app = FastAPI(title="ReelVault API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class SaveReelRequest(BaseModel):
    url: str

@app.get("/api/reels")
def list_reels(search: str = "", category: str = ""):
    return db.get_reels(search=search, category=category)

@app.get("/api/reels/{reel_id}")
def get_single_reel(reel_id: str):
    reel = db.get_reel_by_id(reel_id)
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")
    return reel

@app.get("/api/proxy/video/{reel_id}")
def proxy_video(reel_id: str, request: Request):
    reel = db.get_reel_by_id(reel_id)
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")
    video_url = reel.get("media", {}).get("direct_video_url")
    if not video_url:
        raise HTTPException(status_code=404, detail="Video URL not found")
    
    # Forward Range headers for seeking & Safari compatibility
    req_headers = {}
    range_header = request.headers.get("range")
    if range_header:
        req_headers["Range"] = range_header
    
    try:
        upstream = requests.get(video_url, headers=req_headers, stream=True, timeout=20)
        resp_headers = {
            "Accept-Ranges": "bytes",
            "Content-Type": upstream.headers.get("Content-Type", "video/mp4"),
            "Access-Control-Allow-Origin": "*",
        }
        for h in ["Content-Range", "Content-Length", "Last-Modified", "ETag"]:
            if h in upstream.headers:
                resp_headers[h] = upstream.headers[h]
                
        def stream_chunks():
            for chunk in upstream.iter_content(chunk_size=512 * 1024):
                if chunk:
                    yield chunk

        return StreamingResponse(
            stream_chunks(),
            status_code=upstream.status_code,
            headers=resp_headers,
            media_type=resp_headers["Content-Type"]
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to stream video: {str(e)}")

@app.get("/api/proxy/thumb/{reel_id}")
def proxy_thumbnail(reel_id: str):
    reel = db.get_reel_by_id(reel_id)
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")
    thumb_url = reel.get("media", {}).get("thumbnail")
    if not thumb_url:
        raise HTTPException(status_code=404, detail="Thumbnail not found")
    
    try:
        r = requests.get(thumb_url, timeout=10)
        return Response(
            content=r.content,
            media_type=r.headers.get("Content-Type", "image/jpeg"),
            headers={"Cache-Control": "public, max-age=86400", "Access-Control-Allow-Origin": "*"}
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to fetch thumbnail: {str(e)}")

@app.post("/api/reels")
def save_new_reel(req: SaveReelRequest):
    card = process_reel.extract_and_process(req.url.strip())
    db.save_reel(card)
    return db.get_reel_by_id(card["media"]["id"]) or card

@app.delete("/api/reels/{reel_id}")
def remove_reel(reel_id: str):
    db.delete_reel(reel_id)
    return {"status": "deleted", "id": reel_id}

if os.path.exists("frontend/dist"):
    app.mount("/", StaticFiles(directory="frontend/dist", html=True), name="frontend")
```

---

### 3. `db.py` (Database Layer)

```python
import sqlite3
import json
import os

DB_PATH = "reels.db"

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS reels (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        category TEXT NOT NULL,
        tags TEXT NOT NULL,
        summary TEXT NOT NULL,
        key_takeaways TEXT NOT NULL,
        extracted_resources TEXT NOT NULL,
        structured_content TEXT NOT NULL,
        uploader TEXT NOT NULL,
        channel TEXT NOT NULL,
        thumbnail TEXT,
        direct_video_url TEXT,
        webpage_url TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)
    conn.commit()
    conn.close()

def save_reel(card: dict):
    conn = get_db()
    cursor = conn.cursor()
    media = card.get("media", {})
    reel_id = media.get("id") or card.get("id") or card.get("title")
    
    cursor.execute("""
    INSERT OR REPLACE INTO reels (
        id, title, category, tags, summary, key_takeaways,
        extracted_resources, structured_content, uploader, channel,
        thumbnail, direct_video_url, webpage_url
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        reel_id,
        card.get("title", "Untitled"),
        card.get("category", "Other"),
        json.dumps(card.get("tags", [])),
        card.get("summary", ""),
        json.dumps(card.get("key_takeaways", [])),
        json.dumps(card.get("extracted_resources", [])),
        json.dumps(card.get("structured_content", {})),
        media.get("uploader", "Unknown"),
        media.get("channel", "unknown"),
        media.get("thumbnail", ""),
        media.get("direct_video_url", ""),
        media.get("webpage_url", "")
    ))
    conn.commit()
    conn.close()
```

---

### 4. Sample Processed Card Data (`processed_cards.json`)

```json
[
  {
    "title": "WinRust: Lightweight Rust-Based Replacements for Windows Core Components",
    "category": "Tech & Code",
    "tags": ["windows", "rust", "opensource", "productivity", "devtools"],
    "summary": "WinRust is an open-source project that replaces heavy Windows core components like the taskbar, search, and Task Manager with faster, lightweight alternatives written in Rust.",
    "key_takeaways": [
      "Swap resource-heavy default Windows components for high-performance Rust alternatives.",
      "Replaces native Windows taskbar, search functionality, and Task Manager.",
      "Easy installation process managed through a single executable (.exe) file."
    ],
    "extracted_resources": [
      {
        "name": "WinRust",
        "type": "github_repo",
        "url": "",
        "description": "An open-source collection of fast Windows core component replacements built in Rust."
      }
    ],
    "structured_content": {
      "type": "tutorial",
      "heading": "Overview of WinRust",
      "items_or_markdown": "### What is WinRust?\nWinRust optimizes the Windows experience by replacing heavy desktop shell elements.\n\n### Core Replacements:\n* Taskbar\n* Search\n* Task Manager\n\n### Installation:\nInstalled simply using a single `.exe` file."
    }
  }
]
```

---

## 💡 Suggested Prompt to give Claude along with this file:

> *"Here is the complete codebase and architecture for my project ReelVault (Pinterest meets Notion for Instagram Reels). Please review the architecture, the video streaming proxy with HTTP 206 range requests, the Gemini 3.5 Flash extraction prompt, and give me feedback or help me add [YOUR NEXT FEATURE HERE]."*
