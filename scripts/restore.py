#!/usr/bin/env python3
"""
Savyor Database and Media Restore Script
Safely restores a chosen backup.
Always creates a safety backup of current data before overwriting.
"""
import os
import sys
import shutil
import sqlite3
import argparse
from pathlib import Path

# Add project root to sys.path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

try:
    import db
except ImportError:
    db = None

from scripts.backup import run_backup, format_size, get_dir_size

def list_available_backups(backups_root: str) -> list[str]:
    """Lists directories in backups folder sorted newest first."""
    if not os.path.exists(backups_root):
        return []
    entries = []
    for item in os.listdir(backups_root):
        item_path = os.path.join(backups_root, item)
        if os.path.isdir(item_path):
            entries.append((item_path, os.path.getmtime(item_path)))
    entries.sort(key=lambda x: x[1], reverse=True)
    return [p for p, _ in entries]

def count_reels_in_db(db_path: str) -> int:
    """Returns reel count or -1 if unreadable."""
    if not os.path.exists(db_path):
        return 0
    try:
        conn = sqlite3.connect(db_path)
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        cursor.execute("SELECT count(*) as count FROM reels")
        count = cursor.fetchone()["count"]
        conn.close()
        return count
    except Exception:
        return -1

def run_restore(backup_path: str = None, auto_confirm: bool = False):
    backups_root = os.path.join(PROJECT_ROOT, "backups")
    available = list_available_backups(backups_root)

    if not available:
        print(f"Error: No backups found in {backups_root}.")
        sys.exit(1)

    target_backup = None

    if backup_path:
        if not os.path.isabs(backup_path):
            backup_path = os.path.join(backups_root, backup_path)
        if os.path.isdir(backup_path):
            target_backup = backup_path
        else:
            print(f"Error: Specified backup path '{backup_path}' does not exist.")
            sys.exit(1)
    else:
        print("\nAvailable backups (newest first):")
        for i, b_path in enumerate(available, 1):
            folder_name = os.path.basename(b_path)
            db_file = os.path.join(b_path, "reels.db")
            reels = count_reels_in_db(db_file)
            media_size, media_files = get_dir_size(os.path.join(b_path, "media"))
            print(f"  [{i}] {folder_name} - {reels} reels, {media_files} media files ({format_size(media_size)})")

        choice = input("\nEnter number to restore (or 'q' to cancel): ").strip()
        if choice.lower() in ('q', 'quit', 'exit', ''):
            print("Restore cancelled.")
            sys.exit(0)
        try:
            idx = int(choice) - 1
            if 0 <= idx < len(available):
                target_backup = available[idx]
            else:
                print("Invalid selection.")
                sys.exit(1)
        except ValueError:
            print("Invalid input.")
            sys.exit(1)

    backup_name = os.path.basename(target_backup)
    b_db = os.path.join(target_backup, "reels.db")
    reels_in_backup = count_reels_in_db(b_db)

    print(f"\nTarget Backup: {backup_name}")
    print(f"Reels in backup: {reels_in_backup}")

    if not auto_confirm:
        confirm = input(f"\n⚠️  WARNING: Restoring will overwrite the current database and media.\nType 'yes' to proceed with restore from '{backup_name}': ").strip()
        if confirm.lower() != 'yes':
            print("Restore cancelled by user.")
            sys.exit(0)

    # 1. Safety backup before restore
    print("\n[Step 1/3] Creating automatic pre-restore safety backup of current data...")
    safety_dir = run_backup(custom_label="safety_before_restore")
    print(f"✓ Safety backup saved to: {safety_dir}")

    # Resolve active destination paths
    if db:
        dest_db_path = db.get_db_path()
        dest_media_path = os.path.dirname(db.get_videos_dir())
    else:
        data_dir = os.getenv("SAVYOR_DATA_DIR", "").strip()
        dest_db_path = os.path.join(data_dir, "reels.db") if data_dir else "reels.db"
        dest_media_path = os.path.join(data_dir, "media") if data_dir else "media"

    if not os.path.isabs(dest_db_path):
        dest_db_path = os.path.join(PROJECT_ROOT, dest_db_path)
    if not os.path.isabs(dest_media_path):
        dest_media_path = os.path.join(PROJECT_ROOT, dest_media_path)

    # 2. Restore Database using SQLite backup API
    print(f"\n[Step 2/3] Restoring database to {dest_db_path}...")
    if os.path.exists(b_db):
        os.makedirs(os.path.dirname(dest_db_path) or ".", exist_ok=True)
        src_conn = sqlite3.connect(b_db)
        dest_conn = sqlite3.connect(dest_db_path)
        try:
            with dest_conn:
                src_conn.backup(dest_conn)
            print("✓ Database restored successfully via SQLite backup API.")
        finally:
            dest_conn.close()
            src_conn.close()
    else:
        print("⚠️ No reels.db found in backup folder.")

    # 3. Restore Media folder
    print(f"\n[Step 3/3] Restoring media to {dest_media_path}...")
    src_media = os.path.join(target_backup, "media")
    if os.path.exists(src_media):
        os.makedirs(dest_media_path, exist_ok=True)
        shutil.copytree(src_media, dest_media_path, dirs_exist_ok=True)
        m_size, m_files = get_dir_size(dest_media_path)
        print(f"✓ Media folder restored ({m_files} files, {format_size(m_size)}).")
    else:
        print("ℹ️ No media folder in backup.")

    # Verification
    final_reels = count_reels_in_db(dest_db_path)
    print("\n=== Restore Complete ===")
    print(f"Restored from:  {backup_name}")
    print(f"Active reels:   {final_reels}")
    print(f"Safety backup:  {os.path.basename(safety_dir)}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Restore Savyor database and media from a backup.")
    parser.add_argument("backup_path", nargs="?", help="Specific backup directory or name to restore")
    parser.add_argument("-y", "--yes", action="store_true", help="Confirm restore without interactive prompt")
    args = parser.parse_args()
    run_restore(args.backup_path, auto_confirm=args.yes)
