#!/usr/bin/env python3
"""
Savyor Hosting Pre-Flight Test Script
Evaluates whether a cloud hosting provider's IP range is clean or blocked/rate-limited by Instagram.
Performs unauthenticated downloads matching production yt-dlp configurations.
"""

import sys
import os
import json
import time
import shutil
import tempfile
import urllib.request
from typing import Dict, Any, List
import yt_dlp

def get_public_ip_info() -> Dict[str, str]:
    """Retrieves machine's public IP address and country via ipinfo.io."""
    try:
        req = urllib.request.Request(
            "https://ipinfo.io/json",
            headers={"User-Agent": "curl/7.68.0"}
        )
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return {
                "ip": data.get("ip", "Unknown"),
                "country": data.get("country", "Unknown"),
                "region": data.get("region", "Unknown"),
                "org": data.get("org", "Unknown"),
            }
    except Exception:
        try:
            req = urllib.request.Request("https://api.ipify.org?format=json")
            with urllib.request.urlopen(req, timeout=5) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                return {"ip": data.get("ip", "Unknown"), "country": "Unknown", "region": "Unknown", "org": "Unknown"}
        except Exception:
            return {"ip": "Unavailable", "country": "Unavailable", "region": "Unavailable", "org": "Unavailable"}

def categorize_error(err_str: str) -> str:
    """Classifies extraction error into actionable categories."""
    low = err_str.lower()
    if any(k in low for k in ["login", "sign in", "checkpoint", "log in", "confirm you're a human"]):
        return "login required"
    if any(k in low for k in ["429", "too many requests", "rate limit", "temporarily blocked"]):
        return "rate limited"
    if any(k in low for k in ["private", "unavailable", "deleted", "removed", "does not exist", "not found", "404"]):
        return "private or unavailable"
    if any(k in low for k in ["timed out", "timeout", "connection refused", "network is unreachable", "temporary failure"]):
        return "network or timeout"
    return "other"

def test_download_url(url: str, timeout_seconds: int = 60) -> Dict[str, Any]:
    """Tests downloading a single URL with Savyor production yt-dlp settings."""
    temp_dir = tempfile.mkdtemp(prefix="savyor_host_test_")
    start_time = time.time()
    
    # Use production format; if ffmpeg is missing in test environment, fall back to best single stream
    has_ffmpeg = shutil.which("ffmpeg") is not None
    format_spec = (
        'bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best'
        if has_ffmpeg else
        'best[height<=720][ext=mp4]/best'
    )

    ydl_opts = {
        'format': format_spec,
        'outtmpl': os.path.join(temp_dir, 'test_media.%(ext)s'),
        'writethumbnail': True,
        'quiet': True,
        'no_warnings': True,
        'overwrites': True,
        'socket_timeout': 15,
    }

    result = {
        "url": url,
        "success": False,
        "elapsed_seconds": 0.0,
        "file_size_bytes": 0,
        "category": "unknown",
        "error": None,
    }

    try:
        # Enforce execution timeout
        from concurrent.futures import ThreadPoolExecutor, TimeoutError
        def _execute():
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                return ydl.extract_info(url, download=True)

        with ThreadPoolExecutor(max_workers=1) as executor:
            future = executor.submit(_execute)
            info = future.result(timeout=timeout_seconds)

        elapsed = round(time.time() - start_time, 2)
        result["elapsed_seconds"] = elapsed
        result["success"] = True
        result["category"] = "success"

        # Calculate downloaded file size
        total_size = 0
        for f in os.listdir(temp_dir):
            fp = os.path.join(temp_dir, f)
            if os.path.isfile(fp):
                total_size += os.path.getsize(fp)
        result["file_size_bytes"] = total_size

    except TimeoutError:
        result["elapsed_seconds"] = round(time.time() - start_time, 2)
        result["success"] = False
        result["category"] = "network or timeout"
        result["error"] = f"Download exceeded timeout of {timeout_seconds}s"
    except Exception as e:
        result["elapsed_seconds"] = round(time.time() - start_time, 2)
        err_msg = str(e).split("\n")[0].strip()
        result["success"] = False
        result["category"] = categorize_error(err_msg)
        result["error"] = err_msg
    finally:
        # Cleanup downloaded file immediately
        try:
            shutil.rmtree(temp_dir, ignore_errors=True)
        except Exception:
            pass

    return result

def main():
    urls_file = os.path.join(os.path.dirname(__file__), "..", "urls.txt")
    if not os.path.exists(urls_file):
        urls_file = "urls.txt"

    if not os.path.exists(urls_file):
        print(f"Error: URLs file not found. Create 'urls.txt' with one Instagram reel URL per line.")
        sys.exit(1)

    with open(urls_file, "r", encoding="utf-8") as f:
        urls = [line.strip().lstrip('\ufeff') for line in f if line.strip() and not line.strip().lstrip('\ufeff').startswith("#")]

    if not urls:
        print("Error: 'urls.txt' is empty. Add at least 1 URL to test.")
        sys.exit(1)

    print("==============================================================")
    print("           SAVYOR HOSTING PRE-FLIGHT TEST")
    print("==============================================================")
    
    geo = get_public_ip_info()
    print(f" Public IP  : {geo['ip']}")
    print(f" Region     : {geo.get('region', 'N/A')}, {geo.get('country', 'N/A')}")
    print(f" Org/ISP    : {geo.get('org', 'N/A')}")
    print(f" URLs to test: {len(urls)}")
    print("--------------------------------------------------------------")

    results: List[Dict[str, Any]] = []
    
    for idx, url in enumerate(urls, start=1):
        print(f"[{idx}/{len(urls)}] Testing {url[:48]}... ", end="", flush=True)
        res = test_download_url(url, timeout_seconds=60)
        results.append(res)
        
        if res["success"]:
            size_mb = res["file_size_bytes"] / (1024 * 1024)
            print(f"OK ({res['elapsed_seconds']}s, {size_mb:.1f} MB)")
        else:
            print(f"FAILED [{res['category']}] - {res['error'][:40]}")

    # Summary calculation
    total = len(results)
    succeeded = sum(1 for r in results if r["success"])
    failed = total - succeeded
    cat_counts: Dict[str, int] = {}
    for r in results:
        if not r["success"]:
            cat_counts[r["category"]] = cat_counts.get(r["category"], 0) + 1

    success_times = [r["elapsed_seconds"] for r in results if r["success"]]
    avg_time = round(sum(success_times) / len(success_times), 2) if success_times else 0.0
    success_rate = (succeeded / total) * 100 if total > 0 else 0.0

    print("==============================================================")
    print("                        SUMMARY")
    print("==============================================================")
    print(f" Total Tested    : {total}")
    print(f" Succeeded       : {succeeded} ({success_rate:.1f}%)")
    print(f" Failed          : {failed}")
    if failed > 0:
        for cat, cnt in sorted(cat_counts.items()):
            print(f"   - {cat:<22}: {cnt}")
    print(f" Avg Download Time : {avg_time}s")
    print("--------------------------------------------------------------")
    if success_rate >= 80:
        print(" VERDICT: CLEAN IP. This host can reliably extract reels without proxies.")
    elif success_rate >= 50:
        print(" VERDICT: MODERATE RISK. Some extraction failures encountered. Monitor closely.")
    else:
        print(" VERDICT: FLAGGED IP. Instagram is blocking this hosting provider's IP range.")
        print(" RECOMMENDATION: Recreate instance in another subnet/region or configure residential proxy.")
    print("==============================================================")

    # Save to hosting_test_results.json
    output_path = "hosting_test_results.json"
    output_data = {
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime()),
        "network": geo,
        "summary": {
            "total": total,
            "succeeded": succeeded,
            "failed": failed,
            "success_rate_percent": success_rate,
            "average_download_seconds": avg_time,
            "failures_by_category": cat_counts
        },
        "results": results
    }
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output_data, f, indent=2)

    print(f"Detailed results saved to {output_path}")

if __name__ == "__main__":
    main()
