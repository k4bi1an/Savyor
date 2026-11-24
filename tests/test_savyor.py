import os
import sys
import tempfile
import atexit
import shutil

# Set up isolated temp directory for test database and media storage BEFORE importing any app modules
TEST_DATA_DIR = tempfile.mkdtemp(prefix="savyor_test_isolated_")
os.environ["SAVYOR_DATA_DIR"] = TEST_DATA_DIR
os.environ["SAVYOR_PASSCODE"] = "test_custom_passcode_2026"
os.environ["SAVYOR_SECRET_KEY"] = "test_secure_session_secret_key_849204820184"
os.environ["SAVYOR_REQUIRE_PASSCODE"] = "0"
os.environ["TRUST_PROXY"] = "0"

def _cleanup_test_data():
    if os.path.exists(TEST_DATA_DIR):
        shutil.rmtree(TEST_DATA_DIR, ignore_errors=True)

atexit.register(_cleanup_test_data)

import pytest
import re
import requests
import db
import process_reel
import server

# Initialize database schema in isolated test directory
db.init_db()

# Seed test reels required for CRUD & status tests in isolated test DB
SEED_REEL_1 = {
    "id": "DfBEEFBo52M",
    "title": "Tech Code Test Reel",
    "category": "Tech & Code",
    "category_id": "tech-code",
    "tags": ["tech", "code"],
    "summary": "Summary of code testing",
    "transcript": "Transcript snippet for tech code",
    "key_takeaways": ["Clean code", "Automation"],
    "extracted_resources": [],
    "structured_content": {},
    "media": {
        "id": "DfBEEFBo52M",
        "uploader": "TechMaster",
        "channel": "techmaster",
        "thumbnail": "",
        "local_video_path": "",
        "local_thumb_path": "",
        "webpage_url": "https://www.instagram.com/reel/DfBEEFBo52M/"
    },
    "status": "unread"
}

SEED_REEL_2 = {
    "id": "Df-OIaqgGZy",
    "title": "Health Fitness Test Reel",
    "category": "Health & Fitness",
    "category_id": "health-fitness",
    "tags": ["health", "fitness"],
    "summary": "Summary of health fitness",
    "transcript": "Transcript snippet for fitness",
    "key_takeaways": ["Stay active", "Consistent habit"],
    "extracted_resources": [],
    "structured_content": {},
    "media": {
        "id": "Df-OIaqgGZy",
        "uploader": "FitnessPro",
        "channel": "fitnesspro",
        "thumbnail": "",
        "local_video_path": "",
        "local_thumb_path": "",
        "webpage_url": "https://www.instagram.com/reel/Df-OIaqgGZy/"
    },
    "status": "unread"
}

db.save_reel(SEED_REEL_1)
db.save_reel(SEED_REEL_2)

# 1. URL Parsing & Shortcode Normalization
def test_url_parsing_and_shortcode_normalization():
    assert process_reel.extract_instagram_shortcode("https://www.instagram.com/reel/DfBEEFBo52M/") == "DfBEEFBo52M"
    assert process_reel.extract_instagram_shortcode("https://instagram.com/reel/Df-OIaqgGZy?igsh=MXR2...") == "Df-OIaqgGZy"
    assert process_reel.extract_instagram_shortcode("https://www.instagram.com/p/ABC123_xyz/") == "ABC123_xyz"
    assert process_reel.extract_instagram_shortcode("https://www.instagram.com/share/reel/Z9Y8X7W6/") == "Z9Y8X7W6"
    assert process_reel.get_normalized_reel_url("Df-OIaqgGZy") == "https://www.instagram.com/reel/Df-OIaqgGZy/"

    with pytest.raises(ValueError):
        process_reel.extract_instagram_shortcode("https://youtube.com/shorts/12345")
    with pytest.raises(ValueError):
        process_reel.extract_instagram_shortcode("")

# 2. Share-Target Link Extraction from Text
def test_share_target_extraction():
    regex = r"https?://(?:www\.)?instagram\.com/(?:reel|reels|p|share/reel)/[A-Za-z0-9_-]+[^\s]*"
    shared_text = "Hey look at this great idea: https://www.instagram.com/reel/Df-OIaqgGZy/?utm_source=ig_web_copy_link you must try it"
    match = re.search(regex, shared_text)
    assert match is not None
    assert "Df-OIaqgGZy" in match.group(0)

# 3. Category CRUD & Fallback to 'Other' on Deletion
def test_category_crud_and_delete_moves_to_other():
    cat = db.create_category("Gardening & Plants", "#477A64")
    cat_id = cat["id"]
    assert cat["name"] == "Gardening & Plants"

    updated = db.update_category(cat_id, name="Home Gardening", color="#336644")
    assert updated["name"] == "Home Gardening"
    assert updated["color"] == "#336644"

    reel_id = "DfBEEFBo52M"
    db.update_reel(reel_id, {"category_id": cat_id})
    reel = db.get_reel_by_id(reel_id)
    assert reel["category_id"] == cat_id
    assert reel["category"] == "Home Gardening"

    db.delete_category(cat_id)
    reel_after = db.get_reel_by_id(reel_id)
    assert reel_after["category_id"] == "other"
    assert reel_after["category"] == "Other"

    with pytest.raises(ValueError):
        db.delete_category("other")
    with pytest.raises(ValueError):
        db.update_category("other", name="AnythingElse")

    db.update_reel(reel_id, {"category_id": "tech-code"})

# 4. Category Validation of Model Output
def test_category_validation_of_model_output():
    categories = db.get_categories()
    valid_map = {c["name"].lower(): c["name"] for c in categories}

    test_input = "tech & code"
    validated = valid_map.get(test_input.lower(), "Other")
    assert validated == "Tech & Code"

    test_hallucinated = "Quantum Computing Future Trends"
    validated_fallback = valid_map.get(test_hallucinated.lower(), "Other")
    assert validated_fallback == "Other"

# 5. Reel Status Lifecycle
def test_reel_status_updates():
    reel_id = "Df-OIaqgGZy"

    db.update_reel(reel_id, {"status": "reviewed"})
    reel = db.get_reel_by_id(reel_id)
    assert reel["status"] == "reviewed"
    assert reel["reviewed_at"] is not None

    reviewed_list = db.get_reels(status="reviewed")
    assert any(r["id"] == reel_id for r in reviewed_list)

    db.update_reel(reel_id, {"status": "archived"})
    default_list = db.get_reels(status="All")
    assert not any(r["id"] == reel_id for r in default_list)

    db.update_reel(reel_id, {"status": "unread"})
    restored = db.get_reel_by_id(reel_id)
    assert restored["status"] == "unread"

# 6. Session Auth & 401 Behavior
def test_login_and_auth_routes():
    from fastapi.testclient import TestClient
    client = TestClient(server.app)

    r_unauth = client.get("/api/categories")
    assert r_unauth.status_code == 401

    r_bad = client.post("/api/login", json={"passcode": "wrong_code_xyz"})
    assert r_bad.status_code == 401

    r_login = client.post("/api/login", json={"passcode": "test_custom_passcode_2026"})
    assert r_login.status_code == 200
    assert "savyor_session" in client.cookies

    r_auth = client.get("/api/categories")
    assert r_auth.status_code == 200
    assert len(r_auth.json()) >= 8

# 7. Unauthenticated Health Check Endpoint
def test_healthz_endpoint():
    from fastapi.testclient import TestClient
    client = TestClient(server.app)
    resp = client.get("/healthz")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}

# 8. SAVYOR_DATA_DIR Configuration & Resolution
def test_data_dir_configuration(monkeypatch, tmp_path):
    custom_dir = str(tmp_path / "custom_data")
    monkeypatch.setenv("SAVYOR_DATA_DIR", custom_dir)
    monkeypatch.delenv("SAVYOR_DB_PATH", raising=False)

    assert db.get_data_dir() == custom_dir
    assert db.get_db_path() == os.path.join(custom_dir, "reels.db")
    assert db.get_videos_dir() == os.path.join(custom_dir, "media", "videos")
    assert db.get_thumbs_dir() == os.path.join(custom_dir, "media", "thumbs")

    monkeypatch.delenv("SAVYOR_DATA_DIR", raising=False)
    assert db.get_data_dir() is None
    assert db.get_db_path() == "reels.db"
    assert db.get_videos_dir() == os.path.join("media", "videos")
    assert db.get_thumbs_dir() == os.path.join("media", "thumbs")

# 9. Secret Key Validation Security Check
def test_secret_key_validation_security():
    import subprocess

    env_missing = os.environ.copy()
    env_missing["SAVYOR_PASSCODE"] = "my_passcode"
    env_missing["SAVYOR_SECRET_KEY"] = ""
    res_missing = subprocess.run(
        [sys.executable, "-c", "import server"],
        env=env_missing,
        capture_output=True,
        text=True
    )
    assert res_missing.returncode != 0
    assert "CRITICAL CONFIGURATION ERROR: SAVYOR_PASSCODE is set, but SAVYOR_SECRET_KEY is missing" in res_missing.stderr

    env_default = os.environ.copy()
    env_default["SAVYOR_PASSCODE"] = "my_passcode"
    env_default["SAVYOR_SECRET_KEY"] = "savyor_secure_session_secret_2026"
    res_default = subprocess.run(
        [sys.executable, "-c", "import server"],
        env=env_default,
        capture_output=True,
        text=True
    )
    assert res_default.returncode != 0
    assert "cannot equal the old default" in res_default.stderr

# 10. SAVYOR_REQUIRE_PASSCODE Fail-Safe Check
def test_require_passcode_failsafe():
    import subprocess

    # Case: SAVYOR_REQUIRE_PASSCODE=1 but SAVYOR_PASSCODE is empty -> exits with error
    env_fail = os.environ.copy()
    env_fail["SAVYOR_REQUIRE_PASSCODE"] = "1"
    env_fail["SAVYOR_PASSCODE"] = ""
    res_fail = subprocess.run(
        [sys.executable, "-c", "import server"],
        env=env_fail,
        capture_output=True,
        text=True
    )
    assert res_fail.returncode != 0
    assert "CRITICAL CONFIGURATION ERROR: SAVYOR_REQUIRE_PASSCODE is set to '1', but SAVYOR_PASSCODE is empty" in res_fail.stderr

    # Case: SAVYOR_REQUIRE_PASSCODE=0 or unset, and SAVYOR_PASSCODE is empty -> starts normally on localhost
    env_ok = os.environ.copy()
    env_ok["SAVYOR_REQUIRE_PASSCODE"] = "0"
    env_ok["SAVYOR_PASSCODE"] = ""
    res_ok = subprocess.run(
        [sys.executable, "-c", "import server; print('SERVER_IMPORTED_OK')"],
        env=env_ok,
        capture_output=True,
        text=True
    )
    assert res_ok.returncode == 0
    assert "SERVER_IMPORTED_OK" in res_ok.stdout

# 11. Session Cookie Secure Flag Logic
def test_session_cookie_secure_flag(monkeypatch):
    from fastapi.testclient import TestClient

    # Plain HTTP request -> secure=False
    client_http = TestClient(server.app, base_url="http://localhost:8000")
    r1 = client_http.post("/api/login", json={"passcode": "test_custom_passcode_2026"})
    assert r1.status_code == 200
    set_cookie_http = r1.headers.get("set-cookie", "")
    assert "secure" not in set_cookie_http.lower()

    # HTTPS request -> secure=True
    client_https = TestClient(server.app, base_url="https://localhost:8000")
    r2 = client_https.post("/api/login", json={"passcode": "test_custom_passcode_2026"})
    assert r2.status_code == 200
    set_cookie_https = r2.headers.get("set-cookie", "")
    assert "secure" in set_cookie_https.lower()

    # HTTP request with X-Forwarded-Proto: https and TRUST_PROXY=1 -> secure=True
    monkeypatch.setattr(server, "TRUST_PROXY", True)
    r3 = client_http.post(
        "/api/login",
        json={"passcode": "test_custom_passcode_2026"},
        headers={"X-Forwarded-Proto": "https"}
    )
    assert r3.status_code == 200
    set_cookie_proto = r3.headers.get("set-cookie", "")
    assert "secure" in set_cookie_proto.lower()

    # HTTP request with X-Forwarded-Proto: https but TRUST_PROXY=0 -> secure=False
    monkeypatch.setattr(server, "TRUST_PROXY", False)
    r4 = client_http.post(
        "/api/login",
        json={"passcode": "test_custom_passcode_2026"},
        headers={"X-Forwarded-Proto": "https"}
    )
    assert r4.status_code == 200
    set_cookie_no_trust = r4.headers.get("set-cookie", "")
    assert "secure" not in set_cookie_no_trust.lower()

# 12. Video Upload Tests
VALID_MP4_HEADER = b"\x00\x00\x00\x18ftypmp42\x00\x00\x00\x00isommp42\x00\x00\x00\x08free"
VALID_MOV_HEADER = b"\x00\x00\x00\x14ftypqt  \x00\x00\x00\x00qt  \x00\x00\x00\x08free"

def test_upload_unauthenticated_rejected():
    from fastapi.testclient import TestClient
    client = TestClient(server.app)

    files = {"file": ("test.mp4", VALID_MP4_HEADER, "video/mp4")}
    r = client.post("/api/reels/upload", files=files)
    assert r.status_code == 401
    assert "Unauthorized" in r.json().get("detail", "")

def test_upload_wrong_extension_rejected():
    from fastapi.testclient import TestClient
    client = TestClient(server.app)
    client.post("/api/login", json={"passcode": "test_custom_passcode_2026"})

    for bad_name in ["video.avi", "clip.mkv", "image.png", "notes.txt"]:
        files = {"file": (bad_name, VALID_MP4_HEADER, "application/octet-stream")}
        r = client.post("/api/reels/upload", files=files)
        assert r.status_code == 400
        assert "Only .mp4 and .mov files are allowed" in r.json().get("detail", "")

def test_upload_fake_mp4_rejected():
    from fastapi.testclient import TestClient
    client = TestClient(server.app)
    client.post("/api/login", json={"passcode": "test_custom_passcode_2026"})

    fake_bytes = b"RIFF\x24\x00\x00\x00WAVEfmt \x10\x00\x00\x00\x01\x00\x02\x00"
    files = {"file": ("fake_video.mp4", fake_bytes, "video/mp4")}
    r = client.post("/api/reels/upload", files=files)
    assert r.status_code == 400
    assert "Invalid video signature" in r.json().get("detail", "")

    empty_files = {"file": ("empty.mp4", b"", "video/mp4")}
    r_empty = client.post("/api/reels/upload", files=empty_files)
    assert r_empty.status_code == 400

def test_upload_oversize_rejected(monkeypatch):
    from fastapi.testclient import TestClient
    client = TestClient(server.app)
    client.post("/api/login", json={"passcode": "test_custom_passcode_2026"})

    assert server.MAX_UPLOAD_SIZE == 100 * 1024 * 1024

    # Temporarily set max limit to 30 bytes to test 413 without generating 100MB in memory
    monkeypatch.setattr(server, "MAX_UPLOAD_SIZE", 30)

    oversize_payload = VALID_MP4_HEADER + b"X" * 100
    files = {"file": ("big_video.mp4", oversize_payload, "video/mp4")}
    r = client.post("/api/reels/upload", files=files)
    assert r.status_code == 413
    assert "File too large" in r.json().get("detail", "")

def test_upload_valid_mp4_and_delete_removes_files(monkeypatch):
    from fastapi.testclient import TestClient
    client = TestClient(server.app)
    client.post("/api/login", json={"passcode": "test_custom_passcode_2026"})

    files = {"file": ("my_cool_clip.mp4", VALID_MP4_HEADER + b"\x00" * 64, "video/mp4")}
    r = client.post("/api/reels/upload", files=files)
    assert r.status_code == 202
    data = r.json()
    assert data["status"] == "queued"
    job_id = data["job_id"]
    reel_id = data["reel_id"]
    assert reel_id.startswith("upload_")

    expected_video_path = os.path.join(db.get_videos_dir(), f"{reel_id}.mp4")
    assert os.path.exists(expected_video_path)

    # Mock Gemini analysis for background processing
    mock_ai_card = process_reel.ReelKnowledgeCard(
        title="Direct Uploaded Video Tutorial",
        category="Tech & Code",
        suggested_new_category=None,
        tags=["upload", "tutorial"],
        summary="A comprehensive video on Python direct upload and analysis.",
        transcript="In this video we are demonstrating the upload workflow.",
        key_takeaways=["Direct upload works seamlessly", "Stored with upload source marker"],
        extracted_resources=[],
        structured_content=process_reel.StructuredContent(
            type="tutorial",
            heading="Direct Video Upload",
            items_or_markdown="# Steps\n1. Select MP4 or MOV\n2. Stream to disk"
        )
    )

    monkeypatch.setattr(process_reel, "analyze_with_gemini", lambda *args, **kwargs: mock_ai_card)

    # Run the upload processing worker
    server.run_upload_processing_job(job_id, reel_id, expected_video_path, None, "my_cool_clip.mp4")

    job = db.get_job(job_id)
    assert job["status"] == "done"

    reel = db.get_reel_by_id(reel_id)
    assert reel is not None
    assert reel["title"] == "Direct Uploaded Video Tutorial"
    assert reel["source"] == "upload"
    assert reel["media"]["source"] == "upload"
    assert reel["status"] == "unread"

    # Verify proxy_video streams the uploaded video
    r_stream = client.get(f"/api/proxy/video/{reel_id}")
    assert r_stream.status_code in (200, 206)
    assert "video/mp4" in r_stream.headers.get("Content-Type", "")

    # Verify delete_reel removes reel and file from disk
    r_del = client.delete(f"/api/reels/{reel_id}")
    assert r_del.status_code == 200
    assert r_del.json()["status"] == "deleted"

    assert db.get_reel_by_id(reel_id) is None
    assert not os.path.exists(expected_video_path)

def test_upload_valid_mov_accepted_and_deleted(monkeypatch):
    from fastapi.testclient import TestClient
    client = TestClient(server.app)
    client.post("/api/login", json={"passcode": "test_custom_passcode_2026"})

    files = {"file": ("iphone_recording.mov", VALID_MOV_HEADER + b"\x00" * 32, "video/quicktime")}
    r = client.post("/api/reels/upload", files=files)
    assert r.status_code == 202
    data = r.json()
    reel_id = data["reel_id"]

    mov_path = os.path.join(db.get_videos_dir(), f"{reel_id}.mov")
    assert os.path.exists(mov_path)

    # Stream should return video/quicktime
    r_stream = client.get(f"/api/proxy/video/{reel_id}")
    assert r_stream.status_code in (200, 206)
    assert "video/quicktime" in r_stream.headers.get("Content-Type", "")

    # Delete should clean up the .mov file
    r_del = client.delete(f"/api/reels/{reel_id}")
    assert r_del.status_code == 200
    assert not os.path.exists(mov_path)

# 13. Passcode and Secret Leakage & Static File Mount Security
def test_no_secret_leak_in_responses_or_errors():
    from fastapi.testclient import TestClient
    client = TestClient(server.app)

    passcode = os.environ.get("SAVYOR_PASSCODE", "test_custom_passcode_2026")
    secret_key = os.environ.get("SAVYOR_SECRET_KEY", "test_secure_session_secret_key_849204820184")
    api_key = os.environ.get("GEMINI_API_KEY", "")

    secrets_to_check = [s for s in [passcode, secret_key, api_key] if s and len(s) >= 8]

    # 1. Login success response
    r_login = client.post("/api/login", json={"passcode": passcode})
    assert r_login.status_code == 200
    resp_text = r_login.text
    for s in secrets_to_check:
        assert s not in resp_text

    # 2. Login error response
    r_wrong = client.post("/api/login", json={"passcode": "incorrect_guess_xyz"})
    assert r_wrong.status_code == 401
    for s in secrets_to_check:
        assert s not in r_wrong.text

    # 3. Auth status response
    r_status = client.get("/api/auth/status")
    for s in secrets_to_check:
        assert s not in r_status.text

    # 4. Error 404 response
    r_404 = client.get("/api/nonexistent_route")
    for s in secrets_to_check:
        assert s not in r_404.text

    # 5. Bad request 400 response
    r_400 = client.post("/api/reels", json={"url": "https://not_instagram.com/bad"})
    for s in secrets_to_check:
        assert s not in r_400.text

def test_static_mount_security_and_traversal_blocked():
    from fastapi.testclient import TestClient
    client = TestClient(server.app)

    # Attempt to access sensitive files and folders
    sensitive_targets = [
        "/.env",
        "/.env.example",
        "/reels.db",
        "/server.py",
        "/db.py",
        "/process_reel.py",
        "/media",
        "/media/",
        "/media/videos/test.mp4",
        "/backups",
        "/scripts/backup.py",
        "/../.env",
        "/../../reels.db",
        "/../../server.py"
    ]

    for target in sensitive_targets:
        resp = client.get(target)
        assert resp.status_code in (404, 401), f"Expected 404 or 401 for '{target}', got {resp.status_code}"

def test_server_refuses_to_start_unbound_without_passcode():
    import subprocess

    # Server must refuse to start on 0.0.0.0 if SAVYOR_PASSCODE is unset
    env_unbound = os.environ.copy()
    env_unbound["SAVYOR_PASSCODE"] = ""
    env_unbound["SAVYOR_REQUIRE_PASSCODE"] = "0"

    res = subprocess.run(
        [sys.executable, "server.py", "--host", "0.0.0.0"],
        env=env_unbound,
        capture_output=True,
        text=True
    )
    assert res.returncode != 0
    assert "CRITICAL CONFIGURATION ERROR: SAVYOR_PASSCODE is unset" in res.stderr

    # Server binds to 127.0.0.1 when passcode is unset, printing a warning during startup
    res_local = subprocess.run(
        [sys.executable, "-c", "import server; from fastapi.testclient import TestClient; \nwith TestClient(server.app):\n    print('LOCALHOST_BOUND_OK')"],
        env=env_unbound,
        capture_output=True,
        text=True
    )
    assert res_local.returncode == 0
    assert "LOCALHOST_BOUND_OK" in res_local.stdout
    assert "WARNING: SAVYOR_PASSCODE is unset" in res_local.stdout or "WARNING: SAVYOR_PASSCODE is unset" in res_local.stderr
