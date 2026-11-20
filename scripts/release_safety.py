#!/usr/bin/env python3
"""Verify APK identity and upgrade compatibility before publishing a release."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

APP_ID = "com.arohamusic.app"


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fingerprint(value):
    result = value.replace(":", "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", result):
        raise ValueError("Expected a SHA-256 production certificate fingerprint")
    return result


def inspect_apk(apk, tools):
    verified = subprocess.run([str(tools / "apksigner"), "verify", "--print-certs", str(apk)],
                              check=True, capture_output=True, text=True).stdout
    certs = re.findall(r"^Signer #\d+ certificate SHA-256 digest: (\S+)$", verified, re.M)
    if len(certs) != 1:
        raise ValueError("Expected one verified APK signer")
    badging = subprocess.run([str(tools / "aapt"), "dump", "badging", str(apk)],
                             check=True, capture_output=True, text=True).stdout
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not match:
        raise ValueError("Cannot read APK identity")
    return dict(application_id=match[1], version_code=int(match[2]), version=match[3],
                certificate_sha256=fingerprint(certs[0]), sha256=sha256(apk))


def validate(candidate, expected_cert, expected_version, expected_code, previous=None, first_release=False):
    if candidate["application_id"] != APP_ID:
        raise ValueError("Unexpected application ID")
    if candidate["certificate_sha256"] != fingerprint(expected_cert):
        raise ValueError("APK does not use the expected production signing certificate")
    if candidate["version"] != expected_version or candidate["version_code"] != int(expected_code):
        raise ValueError("APK version does not match the release source")
    if previous is None:
        if not first_release:
            raise ValueError("Previous stable APK required; bootstrap must be explicitly enabled")
    else:
        if previous["application_id"] != APP_ID or previous["certificate_sha256"] != candidate["certificate_sha256"]:
            raise ValueError("Previous production APK has a different application or signing identity")
        if candidate["version_code"] <= previous["version_code"]:
            raise ValueError("Release versionCode must increase over the previous stable APK")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--tools", type=Path, required=True)
    parser.add_argument("--certificate", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--previous", type=Path)
    parser.add_argument("--first-release", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    candidate = inspect_apk(args.apk, args.tools)
    previous = inspect_apk(args.previous, args.tools) if args.previous else None
    validate(candidate, args.certificate, args.version, args.version_code, previous, args.first_release)
    args.output.write_text(json.dumps(candidate, indent=2) + "\n")
    print("Production signing identity and upgrade version verified.")


if __name__ == "__main__":
    main()
