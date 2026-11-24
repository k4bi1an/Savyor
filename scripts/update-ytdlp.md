# Updating yt-dlp in Savyor

Instagram frequently updates its web client, CDN endpoints, and bot detection mechanisms. When Instagram changes how videos are served, `yt-dlp` releases updates to keep extraction functional.

---

## 1. When to Update `yt-dlp`

Update `yt-dlp` if:
- Reel downloads fail with errors such as `Unable to extract video data`, `HTTP 403 Forbidden`, or unexpected formats.
- A new stable release of `yt-dlp` is published addressing Instagram extraction issues.

---

## 2. Update Instructions

### In Local Virtual Environment (`.venv`):
```bash
# Activate your venv
.\.venv\Scripts\activate

# Upgrade yt-dlp to the latest release
python -m pip install --upgrade yt-dlp

# Inspect the new version
yt-dlp --version
```

### Update `requirements.txt`:
Record the exact installed version in `requirements.txt`:
```bash
python -c "import yt_dlp; print(f'yt-dlp=={yt_dlp.version.__version__}')"
```
Update the `yt-dlp==<version>` line in `requirements.txt`.

---

## 3. Verify Against Test Suite

Before deploying or committing:
```bash
.\.venv\Scripts\python.exe -m pytest tests/test_savyor.py -v
```
Ensure all tests pass. Note that Savyor unit and integration tests use a temporary isolated `SAVYOR_DATA_DIR` and mock external network APIs to protect the production database.

---

## 4. In Docker / Production Deployment

If deploying with Docker, rebuild the container image so the new pinned version is baked in:
```bash
docker compose build --no-cache savyor
docker compose up -d
```
