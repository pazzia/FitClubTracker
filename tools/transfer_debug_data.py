"""Backup FitClub debug data via USB/adb; optional, explicit reinstall and restore.

python transfer_debug_data.py --adb /path/to/adb --backup backup-folder
python transfer_debug_data.py --apk FitClub-v0.3.0-build8-debug.apk --allow-reinstall
Requires USB debugging and a single authorized device. No network calls.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
from datetime import datetime

APP = "com.fitclub.tracker"


def main():
    parser = argparse.ArgumentParser(description="Back up and transfer FitClub debug data")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--backup", type=Path)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--allow-reinstall", action="store_true", help="Explicitly allow uninstall only after verified backup")
    args = parser.parse_args()
    if args.apk and not args.apk.is_file():
        raise RuntimeError("APK does not exist")

    def adb(*parts, data=None, check=True):
        result = subprocess.run([args.adb, *parts], input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if check and result.returncode:
            raise RuntimeError(result.stderr.decode(errors="replace") or result.stdout.decode(errors="replace"))
        return result

    devices = adb("devices").stdout.decode().splitlines()[1:]
    if sum(line.endswith("\tdevice") for line in devices) != 1:
        raise RuntimeError("Connect exactly one authorized Android device")
    adb("shell", "run-as", APP, "pwd")  # Refuses non-debuggable builds before doing anything.
    adb("shell", "am", "force-stop", APP)
    folder = args.backup or Path("FitClub-backup-" + datetime.now().strftime("%Y%m%d-%H%M%S"))
    folder.mkdir(parents=True, exist_ok=False)
    candidates = ["databases/fitclub.db", "databases/fitclub.db-wal", "databases/fitclub.db-shm", "shared_prefs/profile.xml"]
    hashes = {}
    for name in candidates:
        result = adb("exec-out", "run-as", APP, "cat", name, check=False)
        if result.returncode:
            if name == "databases/fitclub.db":
                raise RuntimeError("Database backup failed; installation has not been changed")
            continue
        remote_hash = adb("shell", "run-as", APP, "sha256sum", name).stdout.decode().split()[0]
        digest = hashlib.sha256(result.stdout).hexdigest()
        if digest != remote_hash:
            raise RuntimeError("Backup checksum mismatch; installation has not been changed")
        target = folder / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(result.stdout)
        hashes[name] = digest
    database = (folder / "databases/fitclub.db").read_bytes()
    if not database.startswith(b"SQLite format 3\0"):
        raise RuntimeError("Invalid database backup; installation has not been changed")
    (folder / "checksums.json").write_text(json.dumps(hashes, indent=2), encoding="utf-8")
    print("Verified backup:", folder.resolve())
    if not args.apk:
        print("Backup only. Existing installation and data have been kept.")
        return
    install = adb("install", "-r", str(args.apk.resolve()), check=False)
    if install.returncode == 0 and b"Success" in install.stdout:
        print("Updated without uninstalling; data kept.")
        return
    message = (install.stdout + install.stderr).decode(errors="replace")
    if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" not in message:
        raise RuntimeError("Installation failed, existing app kept: " + message)
    if not args.allow_reinstall:
        raise RuntimeError("Different APK signing key. Backup is complete, old app kept. Only --allow-reinstall permits uninstall and restore.")
    # Every file is verified again BEFORE uninstalling.
    for name, digest in hashes.items():
        if hashlib.sha256((folder / name).read_bytes()).hexdigest() != digest:
            raise RuntimeError("Backup changed; old app kept")
    adb("uninstall", APP)
    adb("install", str(args.apk.resolve()))
    adb("shell", "run-as", APP, "mkdir", "-p", "databases", "shared_prefs")
    for name in candidates:
        adb("shell", "run-as", APP, "rm", "-f", name)
    for name, digest in hashes.items():
        adb("exec-in", "run-as", APP, "dd", "of=" + name, data=(folder / name).read_bytes())
        restored = adb("exec-out", "run-as", APP, "cat", name).stdout
        if hashlib.sha256(restored).hexdigest() != digest:
            raise RuntimeError("Restore mismatch. Verified backup remains at " + str(folder.resolve()))
    adb("shell", "am", "start", "-n", APP + "/.MainActivity")
    print("Restored and launched. Keep the backup until you check programs, history and profile.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("Stopped:", error, file=sys.stderr)
        sys.exit(1)
