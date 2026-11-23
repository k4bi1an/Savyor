import requests

BASE = "http://127.0.0.1:8000"

print("--- Testing /api/reels ---")
r = requests.get(f"{BASE}/api/reels")
assert r.status_code == 200, f"Expected 200, got {r.status_code}"
reels = r.json()
assert len(reels) >= 2, f"Expected at least 2 reels, got {len(reels)}"
print(f"PASS: Fetched {len(reels)} reels successfully.")

reel_id = reels[0]["id"]
print(f"\n--- Testing /api/proxy/thumb/{reel_id} ---")
r_thumb = requests.get(f"{BASE}/api/proxy/thumb/{reel_id}")
assert r_thumb.status_code == 200, f"Thumb failed: {r_thumb.status_code}"
assert "image" in r_thumb.headers.get("content-type", ""), f"Wrong mime: {r_thumb.headers.get('content-type')}"
assert len(r_thumb.content) > 1000, "Thumb content empty"
print(f"PASS: Thumbnail proxy returned {len(r_thumb.content)} bytes image.")

print(f"\n--- Testing /api/proxy/video/{reel_id} full stream ---")
r_vid = requests.get(f"{BASE}/api/proxy/video/{reel_id}", stream=True)
assert r_vid.status_code == 200, f"Video stream failed: {r_vid.status_code}"
chunk = next(r_vid.iter_content(chunk_size=4096))
assert len(chunk) == 4096, "Failed to read video chunk"
print("PASS: Full video stream returned chunk successfully.")

print(f"\n--- Testing /api/proxy/video/{reel_id} HTTP Range 206 request ---")
r_range = requests.get(f"{BASE}/api/proxy/video/{reel_id}", headers={"Range": "bytes=0-1024"}, stream=True)
assert r_range.status_code == 206, f"Expected 206 Partial Content, got {r_range.status_code}"
assert "bytes" in r_range.headers.get("Content-Range", ""), "Missing Content-Range header"
print(f"PASS: Range request returned 206 with Content-Range: {r_range.headers.get('Content-Range')}")

print("\nALL BACKEND TESTS PASSED FLACID-FREE!")
