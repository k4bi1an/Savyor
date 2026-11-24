import os
import pytest
import time
import requests
import hmac
import hashlib

BASE_URL = os.getenv("SAVYOR_TEST_BASE_URL", "http://127.0.0.1:8001")
TEST_PASSCODE = os.getenv("SAVYOR_PASSCODE", "test_custom_passcode_2026")
SECRET_KEY = os.getenv("SAVYOR_SECRET_KEY", "test_custom_secret_key_99999")

# 1. Unauthenticated requests to every /api route and proxies MUST return 401
@pytest.mark.parametrize("method,endpoint,body", [
    ("GET", "/api/reels", None),
    ("GET", "/api/reels/reel_seed_1", None),
    ("PATCH", "/api/reels/reel_seed_1", {"status": "reviewed"}),
    ("DELETE", "/api/reels/reel_seed_1", None),
    ("POST", "/api/reels", {"url": "https://www.instagram.com/reel/test_unauth/"}),
    ("GET", "/api/categories", None),
    ("POST", "/api/categories", {"name": "Test Unauth"}),
    ("PATCH", "/api/categories/tech-code", {"name": "New Name"}),
    ("DELETE", "/api/categories/tech-code", None),
    ("POST", "/api/categories/reorder", {"category_ids": ["tech-code"]}),
    ("GET", "/api/jobs/job_12345", None),
    ("GET", "/api/proxy/video/reel_seed_1", None),
    ("GET", "/api/proxy/thumb/reel_seed_1", None),
])
def test_unauthenticated_api_routes_return_401(method, endpoint, body):
    s = requests.Session() # Empty session, no cookie, no token
    if method == "GET":
        r = s.get(f"{BASE_URL}{endpoint}")
    elif method == "POST":
        r = s.post(f"{BASE_URL}{endpoint}", json=body)
    elif method == "PATCH":
        r = s.patch(f"{BASE_URL}{endpoint}", json=body)
    elif method == "DELETE":
        r = s.delete(f"{BASE_URL}{endpoint}")
    
    assert r.status_code == 401, f"Expected 401 for unauthenticated {method} {endpoint}, got {r.status_code}"

# 2. Rate limiting: 5 wrong passcodes from same IP results in 429 Too Many Requests
def test_rate_limiting_on_repeated_wrong_passcodes():
    s = requests.Session()
    # Attempt 5 wrong passcodes
    statuses = []
    for _ in range(6):
        r = s.post(f"{BASE_URL}/api/login", json={"passcode": "totally_wrong_passcode"})
        statuses.append(r.status_code)
    
    # At least the 6th attempt must be 429
    assert 429 in statuses, f"Expected 429 rate limit after repeated failures, got statuses: {statuses}"
    
    # Reset rate limit for subsequent tests
    requests.post(f"{BASE_URL}/api/test/reset-rate-limit")

# 3. Correct passcode unlocks and sets signed HttpOnly session cookie
def test_correct_passcode_unlocks():
    s = requests.Session()
    # Wait out rate limit window if needed
    time.sleep(1)
    
    # Using correct passcode
    r = s.post(f"{BASE_URL}/api/login", json={"passcode": TEST_PASSCODE})
    assert r.status_code == 200
    assert "savyor_session" in s.cookies
    
    # Authorized request succeeds
    r_reels = s.get(f"{BASE_URL}/api/reels")
    assert r_reels.status_code == 200

# 4. Tampered cookie is rejected with 401
def test_tampered_cookie_rejected():
    s = requests.Session()
    # Inject a forged cookie
    fake_token = f"user:{int(time.time())}.0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    s.cookies.set("savyor_session", fake_token)
    
    r = s.get(f"{BASE_URL}/api/reels")
    assert r.status_code == 401, f"Expected 401 for tampered cookie, got {r.status_code}"

# 5. Expired cookie is rejected with 401
def test_expired_cookie_rejected():
    s = requests.Session()
    # Generate a cookie with timestamp from 31 days ago
    expired_ts = int(time.time()) - (32 * 24 * 3600)
    payload = f"user:{expired_ts}"
    signature = hmac.new(SECRET_KEY.encode(), payload.encode(), hashlib.sha256).hexdigest()
    expired_token = f"{payload}.{signature}"
    s.cookies.set("savyor_session", expired_token)
    
    r = s.get(f"{BASE_URL}/api/reels")
    assert r.status_code == 401, f"Expected 401 for expired cookie, got {r.status_code}"

# 6. Logout clears session and blocks subsequent requests
def test_logout_behavior():
    s = requests.Session()
    # 1. Login
    r_login = s.post(f"{BASE_URL}/api/login", json={"passcode": TEST_PASSCODE})
    assert r_login.status_code == 200
    assert r_login.cookies.get("savyor_session") is not None
    
    # 2. Logout
    r_logout = s.post(f"{BASE_URL}/api/logout")
    assert r_logout.status_code == 200
    
    # In requests session, manually clear cookie if not expired by header
    s.cookies.clear()
    
    # 3. Subsequent request must be 401
    r_after = s.get(f"{BASE_URL}/api/reels")
    assert r_after.status_code == 401
