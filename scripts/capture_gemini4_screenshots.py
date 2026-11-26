import os
import time
import subprocess

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
OUT_DIR = os.path.abspath("ui_review_screenshots")
os.makedirs(OUT_DIR, exist_ok=True)

def run_adb(args):
    return subprocess.run([ADB] + args, capture_output=True, text=True)

def screencap(filename):
    remote = f"/data/local/tmp/{filename}"
    local = os.path.join(OUT_DIR, filename)
    run_adb(["shell", "screencap", "-p", remote])
    run_adb(["pull", remote, local])
    print(f"Captured {local} ({os.path.getsize(local)} bytes)")
    return local

# Launch app and wait for savyor.db to be created by Room
run_adb(["shell", "am", "start", "-n", "com.savyor.app/.ui.MainActivity", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER"])
for _ in range(20):
    res = run_adb(["shell", "su", "0", "ls", "/data/data/com.savyor.app/databases/savyor.db"])
    if res.returncode == 0:
        break
    time.sleep(1)
time.sleep(1)

# Seed completed + processing reels
subprocess.run(["python", "scripts/seed_emulator_data.py"], check=True)
subprocess.run(["python", "scripts/insert_proc.py"], check=True)

# Cold-launch to Home
run_adb(["shell", "am", "force-stop", "com.savyor.app"])
time.sleep(1)
run_adb(["shell", "am", "start", "-n", "com.savyor.app/.ui.MainActivity", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER"])
time.sleep(6)

# Capture Home showing Processing linear progress bar + Completed cards with no shortcode
screencap("verify_processing_linear_bar.png")
screencap("verify_completed_no_shortcode.png")

# Tap first completed reel card (top-left at ~270, 850) to open Reel Detail Notes view
run_adb(["shell", "input", "tap", "270", "850"])
time.sleep(2.5)
screencap("verify_detail_instagram_icon.png")

# Back to Home
run_adb(["shell", "input", "keyevent", "4"])
time.sleep(1)

# Open Settings (966, 228) to capture Model selector
run_adb(["shell", "input", "tap", "966", "228"])
time.sleep(2)
screencap("verify_settings_gemini_model.png")

print("Screenshot capture complete!")
