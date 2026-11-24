import os
import sys
import uuid
import time
import hmac
import hashlib
from datetime import datetime, timezone
from contextlib import asynccontextmanager
from typing import Optional, List, Dict, Any
from collections import defaultdict

import requests
import shutil
import subprocess
from fastapi import FastAPI, HTTPException, Request, Response, BackgroundTasks, Depends, Header, UploadFile, File
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from dotenv import load_dotenv

import db
import process_reel

load_dotenv()

# Passcode & Security Settings
OLD_DEFAULT_SECRET = "savyor_secure_session_secret_2026"
SAVYOR_PASSCODE = os.getenv("SAVYOR_PASSCODE", "").strip()
SAVYOR_REQUIRE_PASSCODE = os.getenv("SAVYOR_REQUIRE_PASSCODE", "").strip() == "1"

if SAVYOR_REQUIRE_PASSCODE and not SAVYOR_PASSCODE:
    sys.exit("CRITICAL CONFIGURATION ERROR: SAVYOR_REQUIRE_PASSCODE is set to '1', but SAVYOR_PASSCODE is empty. A secure passcode must be configured.")

SAVYOR_SECRET = os.getenv("SAVYOR_SECRET_KEY", "").strip()

if SAVYOR_PASSCODE:
    if not SAVYOR_SECRET:
        sys.exit("CRITICAL CONFIGURATION ERROR: SAVYOR_PASSCODE is set, but SAVYOR_SECRET_KEY is missing. Please define a unique SAVYOR_SECRET_KEY.")
    if SAVYOR_SECRET == OLD_DEFAULT_SECRET:
        sys.exit(f"CRITICAL CONFIGURATION ERROR: SAVYOR_SECRET_KEY cannot equal the old default ('{OLD_DEFAULT_SECRET}'). Please configure a unique, secure secret key.")

TRUST_PROXY = os.getenv("TRUST_PROXY", "").strip().lower() in ("true", "1")
COOKIE_NAME = "savyor_session"
SESSION_MAX_AGE = 30 * 24 * 3600  # 30 days

# Rate limiting for login (5 failed attempts per minute per IP)
login_attempts = defaultdict(list)

def get_client_ip(request: Request) -> str:
    if TRUST_PROXY:
        cf_ip = request.headers.get("CF-Connecting-IP")
        if cf_ip:
            return cf_ip.strip()
        xfwd = request.headers.get("X-Forwarded-For")
        if xfwd:
            return xfwd.split(",")[0].strip()
    return request.client.host if request.client else "127.0.0.1"

def is_request_https(request: Request) -> bool:
    if TRUST_PROXY:
        proto = request.headers.get("X-Forwarded-Proto")
        if proto:
            return proto.split(",")[0].strip().lower() == "https"
    return request.url.scheme.lower() == "https"

def is_rate_limited(ip: str) -> bool:
    now = time.time()
    # Filter attempts within the last 60 seconds
    attempts = [t for t in login_attempts[ip] if now - t < 60]
    login_attempts[ip] = attempts
    return len(attempts) >= 5

def record_failed_login(ip: str):
    login_attempts[ip].append(time.time())

def clear_failed_logins(ip: str):
    login_attempts.pop(ip, None)

def sign_session(payload: str) -> str:
    signature = hmac.new(SAVYOR_SECRET.encode(), payload.encode(), hashlib.sha256).hexdigest()
    return f"{payload}.{signature}"

def verify_session(token: str) -> bool:
    if not token or "." not in token:
        return False
    parts = token.rsplit(".", 1)
    if len(parts) != 2:
        return False
    payload, signature = parts
    expected = hmac.new(SAVYOR_SECRET.encode(), payload.encode(), hashlib.sha256).hexdigest()
    if not hmac.compare_digest(signature, expected):
        return False
    try:
        ts = int(payload.split(":")[1])
        if time.time() - ts > SESSION_MAX_AGE:
            return False
        return True
    except Exception:
        return False

# Startup Lifespan
@asynccontextmanager
async def lifespan(app: FastAPI):
    # 1. Startup: Verify GEMINI_API_KEY
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        print("⚠️ CRITICAL WARNING: GEMINI_API_KEY is not set in environment or .env!")
    else:
        print("✓ GEMINI_API_KEY verified.")
        
    # 2. Startup: Security & Passcode Check
    if not SAVYOR_PASSCODE:
        for i, a in enumerate(sys.argv):
            if a in ("--host", "-h") and i + 1 < len(sys.argv):
                bound_h = sys.argv[i + 1].strip()
                if bound_h not in ("127.0.0.1", "localhost"):
                    sys.exit(f"CRITICAL CONFIGURATION ERROR: SAVYOR_PASSCODE is unset. The server refuses to start when bound to '{bound_h}'. Network exposure requires setting SAVYOR_PASSCODE.")
            elif a.startswith("--host="):
                bound_h = a.split("=", 1)[1].strip()
                if bound_h not in ("127.0.0.1", "localhost"):
                    sys.exit(f"CRITICAL CONFIGURATION ERROR: SAVYOR_PASSCODE is unset. The server refuses to start when bound to '{bound_h}'. Network exposure requires setting SAVYOR_PASSCODE.")
        print("⚠️ WARNING: SAVYOR_PASSCODE is unset. Running open on localhost (127.0.0.1) only. Network exposure requires setting SAVYOR_PASSCODE.")
    else:
        print("✓ SAVYOR_PASSCODE is active with signed HttpOnly sessions.")

    # 3. Startup: Initialize Database
    db.init_db()
    print("✓ SQLite Database initialized with custom categories and status.")
    
    # Ensure media directories exist
    os.makedirs(db.get_videos_dir(), exist_ok=True)
    os.makedirs(db.get_thumbs_dir(), exist_ok=True)
    
    yield
    print("Shutting down Savyor server.")

app = FastAPI(title="Savyor API", description="Save the reel. Keep the idea.", lifespan=lifespan)

# Configurable CORS (No wildcard when allow_credentials=True)
CORS_ORIGINS_RAW = os.getenv("CORS_ORIGINS", "http://localhost:5173,http://localhost:8000,http://127.0.0.1:5173,http://127.0.0.1:8000,http://localhost:3000")
ALLOWED_ORIGINS = [o.strip() for o in CORS_ORIGINS_RAW.split(",") if o.strip()]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.middleware("http")
async def add_cache_control_headers(request: Request, call_next):
    response = await call_next(request)
    if request.url.path.startswith("/api/") and not request.url.path.startswith("/api/proxy/thumb"):
        response.headers["Cache-Control"] = "no-store, no-cache, must-revalidate"
    return response

# Unauthenticated health check endpoint
@app.get("/healthz")
def healthz():
    return {"status": "ok"}

# Authentication Dependency
def require_auth(request: Request):
    # If no passcode is set in .env, running open on localhost
    if not SAVYOR_PASSCODE:
        return "open_access"

    # 1. Check HttpOnly signed session cookie
    cookie_token = request.cookies.get(COOKIE_NAME)
    if cookie_token and verify_session(cookie_token):
        return "session_valid"

    # 2. Check query parameter session token fallback (for media streaming)
    token = request.query_params.get("session") or request.query_params.get("token")
    if token and verify_session(token):
        return "session_valid"

    raise HTTPException(status_code=401, detail="Unauthorized: Passcode or valid session required.")

# Auth Endpoints
class LoginRequest(BaseModel):
    passcode: str

@app.post("/api/login")
def login(payload: LoginRequest, request: Request, response: Response):
    client_ip = get_client_ip(request)
    if is_rate_limited(client_ip):
        raise HTTPException(status_code=429, detail="Too many login attempts. Please wait 1 minute.")

    entered = payload.passcode.strip()
    
    # If no passcode set in .env, accept any login
    is_valid = False
    if not SAVYOR_PASSCODE:
        is_valid = True
    else:
        is_valid = hmac.compare_digest(entered.encode(), SAVYOR_PASSCODE.encode())

    if not is_valid:
        record_failed_login(client_ip)
        raise HTTPException(status_code=401, detail="Incorrect passcode.")

    clear_failed_logins(client_ip)

    # Create signed session
    ts = int(time.time())
    session_payload = f"user:{ts}"
    signed = sign_session(session_payload)

    # Set HttpOnly SameSite=Lax cookie with 30-day max-age
    is_https = is_request_https(request)
    response.set_cookie(
        key=COOKIE_NAME,
        value=signed,
        max_age=SESSION_MAX_AGE,
        httponly=True,
        samesite="lax",
        secure=is_https,
    )

    return {"status": "ok"}

@app.post("/api/auth/verify")
def verify_auth_passcode(payload: LoginRequest, request: Request, response: Response):
    # Compatibility alias for login
    return login(payload, request, response)

@app.get("/api/auth/status")
def get_auth_status(request: Request):
    is_required = bool(SAVYOR_PASSCODE)
    is_authenticated = False
    cookie_token = request.cookies.get(COOKIE_NAME)
    if cookie_token and verify_session(cookie_token):
        is_authenticated = True
    elif not is_required:
        is_authenticated = True
    return {"auth_required": is_required, "is_authenticated": is_authenticated}

@app.post("/api/logout")
def logout(request: Request, response: Response):
    is_https = is_request_https(request)
    response.delete_cookie(key=COOKIE_NAME, secure=is_https, httponly=True, samesite="lax")
    return {"status": "logged_out"}

# Category Endpoints
class CreateCategoryRequest(BaseModel):
    name: str
    color: Optional[str] = None

class UpdateCategoryRequest(BaseModel):
    name: Optional[str] = None
    color: Optional[str] = None
    sort_order: Optional[int] = None

class ReorderCategoriesRequest(BaseModel):
    category_ids: List[str]

@app.get("/api/categories")
def list_categories(auth: str = Depends(require_auth)):
    return db.get_categories()

@app.post("/api/categories")
def create_category(payload: CreateCategoryRequest, auth: str = Depends(require_auth)):
    try:
        cat = db.create_category(name=payload.name, color=payload.color)
        return cat
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.patch("/api/categories/{cat_id}")
def update_category(cat_id: str, payload: UpdateCategoryRequest, auth: str = Depends(require_auth)):
    try:
        updated = db.update_category(cat_id=cat_id, name=payload.name, color=payload.color, sort_order=payload.sort_order)
        if not updated:
            raise HTTPException(status_code=404, detail="Category not found")
        return updated
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.delete("/api/categories/{cat_id}")
def delete_category(cat_id: str, auth: str = Depends(require_auth)):
    try:
        db.delete_category(cat_id)
        return {"status": "deleted", "id": cat_id, "moved_to": "other"}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/api/categories/reorder")
def reorder_categories(payload: ReorderCategoriesRequest, auth: str = Depends(require_auth)):
    db.reorder_categories(payload.category_ids)
    return {"status": "ok", "categories": db.get_categories()}

# Reels Endpoints
class SaveReelRequest(BaseModel):
    url: str

class UpdateReelRequest(BaseModel):
    category_id: Optional[str] = None
    status: Optional[str] = None
    tags: Optional[List[str]] = None
    suggested_new_category: Optional[str] = None

@app.get("/api/reels")
def list_reels(search: str = "", category: str = "", status: str = "", auth: str = Depends(require_auth)):
    return db.get_reels(search=search, category=category, status=status)

@app.get("/api/reels/{reel_id}")
def get_single_reel(reel_id: str, auth: str = Depends(require_auth)):
    reel = db.get_reel_by_id(reel_id)
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")
    return reel

@app.patch("/api/reels/{reel_id}")
def update_reel(reel_id: str, payload: UpdateReelRequest, auth: str = Depends(require_auth)):
    updates = {}
    if payload.category_id is not None:
        updates["category_id"] = payload.category_id
    if payload.status is not None:
        updates["status"] = payload.status
    if payload.tags is not None:
        updates["tags"] = payload.tags
    if payload.suggested_new_category is not None:
        updates["suggested_new_category"] = payload.suggested_new_category

    updated = db.update_reel(reel_id, updates)
    if not updated:
        raise HTTPException(status_code=404, detail="Reel not found")
    return updated

# Background Job Worker
def run_reel_processing_job(job_id: str, url: str):
    def update_progress(msg: str):
        current_status = "downloading"
        if "Gemini" in msg or "analyzing" in msg or "watching" in msg:
            current_status = "analyzing"
        elif "saved" in msg:
            current_status = "done"
        db.update_job(job_id, status=current_status, progress=msg)

    try:
        shortcode = process_reel.extract_instagram_shortcode(url)
        existing = db.get_reel_by_id(shortcode)
        if existing:
            db.update_job(job_id, status="done", progress="Reel already saved in Savyor.", reel_id=shortcode)
            return

        db.update_job(job_id, status="downloading", progress="Starting download...", reel_id=shortcode)
        saved_card = process_reel.process_and_save_reel(url, progress_callback=update_progress)
        db.update_job(job_id, status="done", progress="Processing complete!", reel_id=saved_card["id"])
    except Exception as e:
        error_msg = str(e)
        print(f"Job {job_id} failed: {error_msg}")
        db.update_job(job_id, status="failed", progress="Failed to process reel", error=error_msg)

def generate_thumbnail_ffmpeg(video_path: str, thumb_path: str) -> Optional[str]:
    if not shutil.which("ffmpeg"):
        return None
    try:
        cmd = [
            "ffmpeg", "-y", "-ss", "00:00:01",
            "-i", video_path,
            "-vframes", "1",
            "-vf", "scale=720:-1",
            thumb_path
        ]
        res = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        if res.returncode == 0 and os.path.exists(thumb_path) and os.path.getsize(thumb_path) > 0:
            return thumb_path
        cmd[3] = "00:00:00"
        res = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        if res.returncode == 0 and os.path.exists(thumb_path) and os.path.getsize(thumb_path) > 0:
            return thumb_path
    except Exception as e:
        print(f"Thumbnail generation failed: {e}")
    return None

def run_upload_processing_job(job_id: str, upload_id: str, video_path: str, thumb_path: Optional[str], original_filename: str):
    def update_progress(msg: str):
        current_status = "analyzing"
        if "saved" in msg:
            current_status = "done"
        db.update_job(job_id, status=current_status, progress=msg)

    try:
        db.update_job(job_id, status="analyzing", progress="Starting AI analysis...", reel_id=upload_id)
        saved_card = process_reel.process_and_save_upload(
            upload_id=upload_id,
            video_path=video_path,
            thumb_path=thumb_path,
            original_filename=original_filename,
            progress_callback=update_progress
        )
        db.update_job(job_id, status="done", progress="Processing complete!", reel_id=saved_card["id"])
    except Exception as e:
        error_msg = str(e)
        print(f"Upload job {job_id} failed: {error_msg}")
        db.update_job(job_id, status="failed", progress="Failed to process uploaded video", error=error_msg)

MAX_UPLOAD_SIZE = 100 * 1024 * 1024  # 100 MB

@app.post("/api/reels/upload", status_code=202)
async def upload_video(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    auth: str = Depends(require_auth)
):
    filename = file.filename or ""
    ext = os.path.splitext(filename)[1].lower()
    if ext not in [".mp4", ".mov"]:
        raise HTTPException(
            status_code=400,
            detail="Invalid file extension. Only .mp4 and .mov files are allowed."
        )

    upload_id = f"upload_{uuid.uuid4().hex[:12]}"
    videos_dir = db.get_videos_dir()
    thumbs_dir = db.get_thumbs_dir()
    os.makedirs(videos_dir, exist_ok=True)
    os.makedirs(thumbs_dir, exist_ok=True)

    dest_video_path = os.path.join(videos_dir, f"{upload_id}{ext}")
    dest_thumb_path = os.path.join(thumbs_dir, f"{upload_id}.jpg")

    chunk_size = 1024 * 1024  # 1 MB chunk
    first_chunk = await file.read(chunk_size)
    if not first_chunk or len(first_chunk) < 8:
        raise HTTPException(status_code=400, detail="Invalid video file: file is empty or corrupted.")

    if first_chunk[4:8] != b"ftyp":
        raise HTTPException(status_code=400, detail="Invalid video signature. File is not a valid MP4 or MOV.")

    total_bytes = len(first_chunk)
    if total_bytes > MAX_UPLOAD_SIZE:
        raise HTTPException(status_code=413, detail="File too large. Maximum allowed size is 100 MB.")

    try:
        with open(dest_video_path, "wb") as f:
            f.write(first_chunk)
            while True:
                chunk = await file.read(chunk_size)
                if not chunk:
                    break
                total_bytes += len(chunk)
                if total_bytes > MAX_UPLOAD_SIZE:
                    f.close()
                    if os.path.exists(dest_video_path):
                        os.remove(dest_video_path)
                    raise HTTPException(status_code=413, detail="File too large. Maximum allowed size is 100 MB.")
                f.write(chunk)
    except HTTPException:
        raise
    except Exception as e:
        if os.path.exists(dest_video_path):
            os.remove(dest_video_path)
        raise HTTPException(status_code=500, detail=f"Failed to save video: {str(e)}")

    thumb_path = generate_thumbnail_ffmpeg(dest_video_path, dest_thumb_path)

    job_id = f"job_{uuid.uuid4().hex[:12]}"
    db.create_job(job_id, f"upload://{upload_id}")
    background_tasks.add_task(
        run_upload_processing_job,
        job_id,
        upload_id,
        dest_video_path,
        thumb_path,
        filename
    )

    return {"job_id": job_id, "status": "queued", "reel_id": upload_id}

@app.post("/api/reels", status_code=202)
def enqueue_save_reel(req: SaveReelRequest, background_tasks: BackgroundTasks, auth: str = Depends(require_auth)):
    url = req.url.strip()
    try:
        shortcode = process_reel.extract_instagram_shortcode(url)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    
    existing = db.get_reel_by_id(shortcode)
    if existing:
        raise HTTPException(status_code=409, detail="This reel is already in your vault.")

    job_id = f"job_{uuid.uuid4().hex[:12]}"
    db.create_job(job_id, url)
    background_tasks.add_task(run_reel_processing_job, job_id, url)
    return {"job_id": job_id, "status": "queued", "reel_id": shortcode}

@app.get("/api/jobs/{job_id}")
def check_job_status(job_id: str, auth: str = Depends(require_auth)):
    job = db.get_job(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    
    reel = None
    if job.get("status") == "done" and job.get("reel_id"):
        reel = db.get_reel_by_id(job["reel_id"])
        
    return {
        "job_id": job["id"],
        "url": job["url"],
        "reel_id": job["reel_id"],
        "status": job["status"],
        "progress": job["progress"],
        "error": job["error"],
        "reel": reel
    }

# Media Proxies (Support signed session cookie and token auth)
@app.get("/api/proxy/video/{reel_id}")
def proxy_video(reel_id: str, request: Request, auth: str = Depends(require_auth)):
    local_path = None
    for candidate_ext in [".mp4", ".mov"]:
        p = os.path.join(db.get_videos_dir(), f"{reel_id}{candidate_ext}")
        if os.path.exists(p):
            local_path = p
            break

    reel = None
    if not local_path or not os.path.exists(local_path):
        reel = db.get_reel_by_id(reel_id)
        if reel and reel.get("media", {}).get("local_video_path"):
            candidate = reel["media"]["local_video_path"]
            if os.path.exists(candidate):
                local_path = candidate

    if local_path and os.path.exists(local_path):
        media_type = "video/quicktime" if local_path.lower().endswith(".mov") else "video/mp4"
        return FileResponse(local_path, media_type=media_type, filename=os.path.basename(local_path))
    
    if not reel:
        reel = db.get_reel_by_id(reel_id)
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")
    video_url = reel.get("media", {}).get("direct_video_url")
    if not video_url:
        raise HTTPException(status_code=404, detail="Video URL not found")
        
    req_headers = {}
    range_header = request.headers.get("range")
    if range_header:
        req_headers["Range"] = range_header
        
    try:
        upstream = requests.get(video_url, headers=req_headers, stream=True, timeout=20)
        resp_headers = {
            "Accept-Ranges": "bytes",
            "Content-Type": upstream.headers.get("Content-Type", "video/mp4"),
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
def proxy_thumbnail(reel_id: str, auth: str = Depends(require_auth)):
    local_thumb_path = os.path.join(db.get_thumbs_dir(), f"{reel_id}.jpg")
    reel = None
    if not os.path.exists(local_thumb_path):
        reel = db.get_reel_by_id(reel_id)
        if reel and reel.get("media", {}).get("local_thumb_path"):
            candidate = reel["media"]["local_thumb_path"]
            if os.path.exists(candidate):
                local_thumb_path = candidate

    if os.path.exists(local_thumb_path):
        return FileResponse(local_thumb_path, media_type="image/jpeg")

    if not reel:
        reel = db.get_reel_by_id(reel_id)
    if not reel:
        raise HTTPException(status_code=404, detail="Reel not found")
    thumb_url = reel.get("media", {}).get("thumbnail")
    if not thumb_url:
        raise HTTPException(status_code=404, detail="Thumbnail URL not found")
    
    try:
        r = requests.get(thumb_url, timeout=10)
        if r.status_code != 200:
            raise HTTPException(status_code=r.status_code, detail="Upstream thumbnail unavailable")
        
        with open(local_thumb_path, "wb") as f:
            f.write(r.content)
            
        return Response(
            content=r.content,
            media_type=r.headers.get("Content-Type", "image/jpeg"),
            headers={"Cache-Control": "public, max-age=86400"}
        )
    except Exception as e:
        if isinstance(e, HTTPException):
            raise e
        raise HTTPException(status_code=500, detail=f"Failed to fetch thumbnail: {str(e)}")

@app.delete("/api/reels/{reel_id}")
def remove_reel(reel_id: str, auth: str = Depends(require_auth)):
    reel = db.get_reel_by_id(reel_id)
    db.delete_reel(reel_id)
    candidates = [
        os.path.join(db.get_videos_dir(), f"{reel_id}.mp4"),
        os.path.join(db.get_videos_dir(), f"{reel_id}.mov"),
        os.path.join(db.get_thumbs_dir(), f"{reel_id}.jpg"),
        f"media/videos/{reel_id}.mp4",
        f"media/videos/{reel_id}.mov",
        f"media/thumbs/{reel_id}.jpg"
    ]
    if reel:
        media = reel.get("media", {})
        if media.get("local_video_path"):
            candidates.append(media["local_video_path"])
        if media.get("local_thumb_path"):
            candidates.append(media["local_thumb_path"])
    for p in candidates:
        if p and os.path.exists(p):
            try:
                os.remove(p)
            except Exception:
                pass
    return {"status": "deleted", "id": reel_id}

# Static Files & SPA Catch-all
if os.path.exists("frontend/dist"):
    assets_dir = os.path.abspath(os.path.join("frontend/dist", "assets"))
    dist_dir = os.path.abspath("frontend/dist")

    @app.get("/assets/{asset_name:path}")
    def serve_asset(asset_name: str):
        target = os.path.abspath(os.path.join(assets_dir, asset_name))
        if not target.startswith(assets_dir):
            raise HTTPException(status_code=404, detail="Asset not found")
        if os.path.isfile(target):
            media_type = "application/javascript" if asset_name.endswith(".js") else "text/css" if asset_name.endswith(".css") else None
            return FileResponse(target, media_type=media_type)
        # Graceful fallback for older asset hashes cached by Service Worker
        if os.path.exists(assets_dir):
            if asset_name.endswith(".js"):
                for f in os.listdir(assets_dir):
                    if f.endswith(".js"):
                        return FileResponse(os.path.join(assets_dir, f), media_type="application/javascript")
            elif asset_name.endswith(".css"):
                for f in os.listdir(assets_dir):
                    if f.endswith(".css"):
                        return FileResponse(os.path.join(assets_dir, f), media_type="text/css")
        raise HTTPException(status_code=404, detail="Asset not found")

    BLOCKED_NAMES = {".env", "reels.db", "server.py", "db.py", "process_reel.py", "ai_processor.py", "requirements.txt", "urls.txt"}
    BLOCKED_PREFIXES = ("media", ".env", "backups", "tests", "scripts", ".git", ".venv")
    BLOCKED_EXTENSIONS = {".env", ".db", ".sqlite", ".py", ".log", ".sh", ".ps1", ".bat", ".key", ".jks", ".keystore", ".bak"}

    @app.get("/{full_path:path}")
    def serve_spa(full_path: str):
        if full_path.startswith("api/"):
            raise HTTPException(status_code=404, detail="API route not found")

        clean_path = full_path.strip("/\\")
        base_name = os.path.basename(clean_path).lower()
        ext = os.path.splitext(base_name)[1].lower()

        # Reject path traversals, sensitive files, media folder, or dotfiles
        if (
            base_name in BLOCKED_NAMES
            or ext in BLOCKED_EXTENSIONS
            or any(clean_path.lower().startswith(p) for p in BLOCKED_PREFIXES)
            or any(part.startswith(".") for part in clean_path.split("/") if part)
        ):
            raise HTTPException(status_code=404, detail="Not found")

        target_file = os.path.abspath(os.path.join(dist_dir, full_path))
        if not target_file.startswith(dist_dir):
            raise HTTPException(status_code=404, detail="Not found")

        if full_path and os.path.isfile(target_file):
            return FileResponse(target_file)

        # Nonexistent asset files should return 404 instead of index.html
        if ext:
            raise HTTPException(status_code=404, detail="Not found")

        return FileResponse(
            os.path.join(dist_dir, "index.html"),
            headers={"Cache-Control": "no-cache, no-store, must-revalidate"}
        )

if __name__ == "__main__":
    import uvicorn
    import argparse

    parser = argparse.ArgumentParser(description="Savyor API Server")
    parser.add_argument("--host", default=None, help="Bind socket host")
    parser.add_argument("--port", type=int, default=8000, help="Bind socket port")
    args, _ = parser.parse_known_args()

    default_host = "0.0.0.0" if SAVYOR_PASSCODE else "127.0.0.1"
    host = args.host or os.getenv("HOST") or default_host

    if not SAVYOR_PASSCODE:
        if host not in ("127.0.0.1", "localhost"):
            sys.exit(f"CRITICAL CONFIGURATION ERROR: SAVYOR_PASSCODE is unset. The server refuses to start when bound to '{host}'. It can only bind to 127.0.0.1 (localhost) when no passcode is set.")
        print("⚠️ WARNING: SAVYOR_PASSCODE is unset. Running open on localhost (127.0.0.1) only.")

    uvicorn.run(app, host=host, port=args.port)
