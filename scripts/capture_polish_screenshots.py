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

# 1. Install latest debug APK
print("Installing updated debug APK...")
apk_path = os.path.abspath("android/app/build/outputs/apk/debug/app-x86_64-debug.apk")
run_adb(["install", "-r", apk_path])

# Ensure dark mode on device
run_adb(["shell", "cmd", "uimode", "night", "yes"])

# Launch app once to initialize Room
run_adb(["shell", "am", "start", "-n", "com.savyor.app/.ui.MainActivity", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER"])
time.sleep(3)

# 2. Seed data
print("Seeding database...")
subprocess.run(["python", "scripts/seed_emulator_data.py"], check=True)
subprocess.run(["python", "scripts/insert_proc.py"], check=True)

# 3. Capture cold launch splash (verify_01)
print("Capturing verify_01_launch_animation.png...")
run_adb(["shell", "am", "force-stop", "com.savyor.app"])
time.sleep(1)
run_adb(["shell", "am", "start", "-n", "com.savyor.app/.ui.MainActivity", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER"])
time.sleep(0.35)
screencap("verify_01_launch_animation.png")

# 4. Wait for Home screen to settle
print("Waiting for Home screen...")
for i in range(20):
    time.sleep(0.5)
    f = run_adb(["shell", "dumpsys", "window"]).stdout
    if "com.savyor.app" in f and "MainActivity" in f:
        break
time.sleep(4)

# 5. Capture category strip scrolled to end showing + Topic
print("Scrolling category strip to the end...")
# Scroll right-to-left within category LazyRow: from x=800, y=370 to x=100, y=370
run_adb(["shell", "input", "swipe", "800", "370", "100", "370", "400"])
time.sleep(1)
screencap("verify_category_strip_end.png")

# 6. Scroll home body to bottom to verify FAB padding
print("Scrolling home screen to the very bottom...")
run_adb(["shell", "input", "swipe", "540", "1800", "540", "500", "400"])
time.sleep(1)
screencap("verify_02_home_scrolled_bottom.png")
screencap("verify_02_home_clean_categories.png")

print("All polish screenshots successfully captured!")
