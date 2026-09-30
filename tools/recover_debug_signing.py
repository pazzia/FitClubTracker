"""Locate an existing build-8 key; never generate or expose a private key."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    guard = Path(__file__).with_name("check_debug_signing.py")
    canonical = Path.home() / ".android" / "debug.keystore"
    candidates = [canonical]
    if args.report.is_file():
        report = args.report.read_text(encoding="utf-8", errors="replace")
        for value in re.findall(r"^\s*Store:\s*(.+debug\.keystore)\s*$", report, flags=re.MULTILINE):
            candidates.append(Path(value.strip()))
    for variable in ("ANDROID_USER_HOME", "ANDROID_PREFS_ROOT", "ANDROID_SDK_HOME", "TEST_TMPDIR"):
        value = os.environ.get(variable)
        if value:
            candidates += [Path(value) / "debug.keystore", Path(value) / ".android" / "debug.keystore"]
    visited = set()
    for candidate in candidates:
        path = candidate.resolve()
        if path in visited:
            continue
        visited.add(path)
        print(f"Checking existing debug key location: {path}")
        if not path.is_file():
            continue
        result = subprocess.run([sys.executable, str(guard), "--keystore", str(path)], capture_output=True)
        if result.returncode != 0:
            print("Existing file does not have the approved signing identity")
            continue
        canonical.parent.mkdir(parents=True, exist_ok=True)
        if path != canonical.resolve():
            shutil.copyfile(path, canonical)
        canonical.chmod(0o600)
        print("Existing approved key restored to the canonical CI location")
        return
    raise RuntimeError("Build 8 key was not found. Supply its original keystore through FITCLUB_DEBUG_KEYSTORE_BASE64; no replacement has been generated.")

if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("Key recovery failed:", error, file=sys.stderr)
        sys.exit(1)
