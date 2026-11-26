import os
import time
import subprocess
import xml.etree.ElementTree as ET

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

print("1. Force-stopping Savyor...")
run_adb(["shell", "am", "force-stop", "com.savyor.app"])
time.sleep(1)

print("2. Cold-launching Savyor and capturing verify_01...")
run_adb(["shell", "am", "start", "-n", "com.savyor.app/.ui.MainActivity", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER"])
time.sleep(0.35)
screencap("verify_01_launch_animation.png")

print("3. Waiting for MainActivity to settle...")
for i in range(20):
    time.sleep(0.5)
    f = run_adb(["shell", "dumpsys", "window"]).stdout
    if "com.savyor.app" in f and "MainActivity" in f:
        print(f"MainActivity focused after {i*0.5 + 0.35:.1f}s")
        break

time.sleep(5) # Let Compose finish drawing

print("4. Capturing verify_02_home_clean_categories.png...")
screencap("verify_02_home_clean_categories.png")
screencap("verify_04_unread_glow_and_processing.png")

print("5. Dumping UI hierarchy...")
run_adb(["shell", "uiautomator", "dump", "/data/local/tmp/home_dump.xml"])
run_adb(["pull", "/data/local/tmp/home_dump.xml", "home_dump.xml"])

tree = ET.parse("home_dump.xml")
root = tree.getroot()

def find_node(pred):
    for node in root.iter("node"):
        if pred(node):
            return node
    return None

def parse_bounds(bounds_str):
    # e.g. [884,352][1016,402]
    import re
    m = re.findall(r"\[(\d+),(\d+)\]", bounds_str)
    if len(m) == 2:
        x1, y1 = int(m[0][0]), int(m[0][1])
        x2, y2 = int(m[1][0]), int(m[1][1])
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None, None

for node in root.iter("node"):
    desc = node.attrib.get("content-desc", "")
    text = node.attrib.get("text", "")
    bounds = node.attrib.get("bounds", "")
    if any(k in desc or k in text for k in ["Search", "Settings", "Filter", "Topic", "PROCESSING"]):
        print(f"Node: desc='{desc}' text='{text}' bounds='{bounds}'")

filter_node = find_node(lambda n: "Filter" in n.attrib.get("content-desc", "") or "Filter" in n.attrib.get("text", ""))
if filter_node:
    fx, fy = parse_bounds(filter_node.attrib.get("bounds"))
    print(f"Tapping Filter at ({fx}, {fy})...")
    run_adb(["shell", "input", "tap", str(fx), str(fy)])
else:
    print("Filter node not found by text, trying default (950, 377)...")
    run_adb(["shell", "input", "tap", "950", "377"])

time.sleep(2)
screencap("verify_03_filter_date_picker.png")

print("Dismissing Filter sheet with Back...")
run_adb(["shell", "input", "keyevent", "4"])
time.sleep(1)

settings_node = find_node(lambda n: "Settings" in n.attrib.get("content-desc", ""))
if settings_node:
    sx, sy = parse_bounds(settings_node.attrib.get("bounds"))
    print(f"Tapping Settings at ({sx}, {sy})...")
    run_adb(["shell", "input", "tap", str(sx), str(sy)])
else:
    print("Settings node not found by desc, trying default (966, 228)...")
    run_adb(["shell", "input", "tap", "966", "228"])

time.sleep(2.5)
screencap("verify_05_settings_ytdlp_telemetry.png")

print("Dismissing Settings with Back...")
run_adb(["shell", "input", "keyevent", "4"])
time.sleep(1)

print("Testing Search BackHandler...")
search_node = find_node(lambda n: "Search" in n.attrib.get("content-desc", ""))
if search_node:
    cx, cy = parse_bounds(search_node.attrib.get("bounds"))
    print(f"Tapping Search at ({cx}, {cy})...")
    run_adb(["shell", "input", "tap", str(cx), str(cy)])
else:
    run_adb(["shell", "input", "tap", "734", "228"])

time.sleep(1.5)
screencap("verify_search_open.png")

print("Pressing 1x Back from search...")
run_adb(["shell", "input", "keyevent", "4"])
time.sleep(1)
f1 = run_adb(["shell", "dumpsys", "window"]).stdout
in_savyor_1 = "com.savyor.app" in f1 and "MainActivity" in f1
print("Focus after 1x Back:", "STILL IN SAVYOR (PASS)" if in_savyor_1 else f"EXITED: {f1[:200]}")
screencap("verify_search_back.png")

print("Testing Pull to Refresh...")
run_adb(["shell", "input", "swipe", "540", "700", "540", "1600", "350"])
time.sleep(1)
screencap("verify_pull_to_refresh.png")
time.sleep(2)

logcat_err = run_adb(["shell", "logcat", "-d", "-s", "AndroidRuntime:E"]).stdout
print("Logcat Fatal Errors:", logcat_err.strip() if logcat_err.strip() else "NONE (CLEAN)")
print("ALL AUTOMATION COMPLETE.")
