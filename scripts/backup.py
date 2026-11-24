#!/usr/bin/env python3
"""
Savyor Database and Media Backup Script
Uses SQLite's online backup API for safe, consistent live backups.
Keeps the newest 10 backups and deletes older ones.
"""
import os
import sys
import shutil
import sqlite3
import re
from datetime import datetime
from pathlib import Path

# Add project root to sys.path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

try:
    import db
except ImportError:
    db = None

MAX_BACKUPS_RETAINED = 10

def format_size(size_bytes: int) -> str:
    """Formats bytes into human readable format."""
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    elif size_bytes < 1024 * 1024 * 1024:
        return f"{size_bytes / (1024 * 1024):.1f} MB"
    return f"{size_bytes / (1024 * 1024 * 1024):.2f} GB"

def get_dir_size(dir_path: str) -> tuple[int, int]:
    """Returns (total_bytes, file_count) for a directory."""
    total_size = 0
    file_count = 0
    if not os.path.exists(dir_path):
        return 0, 0
    for root, _, files in os.walk(dir_path):
        for f in files:
            fp = os.path.join(root, f)
            if not os.path.islink(fp) and os.path.exists(fp):
                total_size += os.path.getsize(fp)
                file_count += 1
    return total_size, file_count

def run_backup(custom_label: str = None) -> str:
    """Executes a consistent backup of reels.db and media/ directory."""
    if db:
        db_path = db.get_db_path()
        media_path = os.path.dirname(db.get_videos_dir())
    else:
        data_dir = os.getenv("SAVYOR_DATA_DIR", "").strip()
        db_path = os.path.join(data_dir, "reels.db") if data_dir else "reels.db"
        media_path = os.path.join(data_dir, "media") if data_dir else "media"

    if not os.path.isabs(db_path):
        db_path = os.path.join(PROJECT_ROOT, db_path)
    if not os.path.isabs(media_path):
        media_path = os.path.join(PROJECT_ROOT, media_path)

    backups_root = os.path.join(PROJECT_ROOT, "backups")
    os.makedirs(backups_root, exist_ok=True)

    timestamp = datetime.now().strftime("%Y-%m-%d_%H%M%S")
    folder_name = f"{custom_label}_{timestamp}" if custom_label else timestamp
    backup_dir = os.path.join(backups_root, folder_name)
    os.makedirs(backup_dir, exist_ok=True)

    print(f"=== Savyor Backup: {folder_name} ===")
    print(f"Backup target directory: {backup_dir}")

    # 1. Consistent SQLite live backup using SQLite online backup API
    dest_db_path = os.path.join(backup_dir, "reels.db")
    db_size = 0
    reel_count = 0

    if os.path.exists(db_path):
        print(f"Connecting to source database: {db_path}")
        source_conn = sqlite3.connect(db_path)
        dest_conn = sqlite3.connect(dest_db_path)
        try:
            with dest_conn:
                source_conn.backup(dest_conn)
            print("✓ Database backup completed via SQLite backup API.")
        finally:
            dest_conn.close()
            source_conn.close()

        db_size = os.path.getsize(dest_db_path)

        # Count reels in backed up copy
        verify_conn = sqlite3.connect(dest_db_path)
        verify_conn.row_factory = sqlite3.Row
        try:
            cursor = verify_conn.cursor()
            cursor.execute("SELECT count(*) as count FROM reels")
            reel_count = cursor.fetchone()["count"]
        except Exception as e:
            reel_count = -1
        finally:
            verify_conn.close()
    else:
        print(f"⚠️ Source database not found at {db_path}; skipping DB backup.")

    # 2. Media folder copy
    dest_media_path = os.path.join(backup_dir, "media")
    media_size = 0
    media_files = 0
    if os.path.exists(media_path):
        shutil.copytree(media_path, dest_media_path, dirs_exist_ok=True)
        media_size, media_files = get_dir_size(dest_media_path)
        print(f"✓ Media folder backup completed ({media_files} files, {format_size(media_size)}).")
    else:
        print("ℹ️ Media folder does not exist; skipping media copy.")

    total_size = db_size + media_size
    print("\n--- Backup Summary ---")
    print(f"Location:   {backup_dir}")
    print(f"Database:   {format_size(db_size)} ({reel_count} reels)")
    print(f"Media:      {format_size(media_size)} ({media_files} files)")
    print(f"Total Size: {format_size(total_size)}")

    # 3. Retention policy: Keep newest 10 backups, delete older ones
    cleanup_old_backups(backups_root, MAX_BACKUPS_RETAINED)

    return backup_dir

def cleanup_old_backups(backups_root: str, max_retain: int):
    """Retains the newest max_retain backups and removes older standard backups."""
    entries = []
    pattern = re.compile(r"^(\d{4}-\d{2}-\d{2}_\d{6})$")
    for item in os.listdir(backups_root):
        item_path = os.path.join(backups_root, item)
        if os.path.isdir(item_path):
            # Match standard timestamped backups or custom labeled backups
            entries.append((item_path, os.path.getmtime(item_path)))

    # Sort newest to oldest
    entries.sort(key=lambda x: x[1], reverse=True)

    if len(entries) > max_retain:
        print(f"\nCleaning up older backups (retaining newest {max_retain})...")
        for old_dir, _ in entries[max_retain:]:
            try:
                shutil.rmtree(old_dir)
                print(f"  Deleted old backup: {os.path.basename(old_dir)}")
            except Exception as e:
                print(f"  Warning: could not delete {old_dir}: {e}")

if __name__ == "__main__":
    run_backup()
