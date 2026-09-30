"""Require the signing identity used by FitClub v0.3.0 build 8.

Never generate a key here: a missing or different key must stop CI.
"""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import sys

DEFAULT_FINGERPRINT = Path(__file__).resolve().parents[1] / "docs" / "debug-signing-sha256.txt"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--keystore", type=Path)
    mode.add_argument("--apk", type=Path)
    parser.add_argument("--apksigner")
    parser.add_argument("--expected", type=Path, default=DEFAULT_FINGERPRINT)
    args = parser.parse_args()
    expected = args.expected.read_text().strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise RuntimeError("Invalid approved certificate fingerprint")
    if args.keystore:
        if not args.keystore.is_file():
            raise RuntimeError("Approved signing key is missing. Restore the existing key from cache or FITCLUB_DEBUG_KEYSTORE_BASE64; do not generate a replacement.")
        certificate = subprocess.check_output(
            ["keytool", "-exportcert", "-keystore", str(args.keystore),
             "-alias", "androiddebugkey", "-storepass", "android"],
            stderr=subprocess.PIPE,
        )
        actual = hashlib.sha256(certificate).hexdigest()
    else:
        if not args.apk.is_file() or not args.apksigner:
            raise RuntimeError("An existing APK and --apksigner are required")
        output = subprocess.check_output(
            [args.apksigner, "verify", "--verbose", "--print-certs", str(args.apk)],
            stderr=subprocess.PIPE,
        ).decode()
        fingerprints = re.findall(r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", output)
        if len(fingerprints) != 1:
            raise RuntimeError("Expected exactly one verified APK signing certificate")
        actual = fingerprints[0].lower()
    if actual != expected:
        raise RuntimeError("Signing certificate differs from the installed build 8 identity. Refusing to publish an incompatible update.")
    print("Approved FitClub signing identity confirmed")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("Signing check failed:", error, file=sys.stderr)
        sys.exit(1)
