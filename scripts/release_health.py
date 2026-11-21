#!/usr/bin/env python3
"""Check official release assets or download the previous stable APK for release validation."""
import argparse
import json
import os
import re
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from release_safety import fingerprint, inspect_apk, sha256, validate

REPOSITORY = "ArohaMusic/Aroha-Music-App"


def apk_names(tag, available):
    required = [f"Aroha-Music-{tag[1:]}.apk", "Aroha-Music-latest.apk"]
    for name in required:
        if name not in available:
            raise ValueError(f"Missing release asset: {name}")
    return required


def fetch_release():
    request = urllib.request.Request(f"https://api.github.com/repos/{REPOSITORY}/releases/latest",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "Aroha-Release-Health"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read(1_048_577))
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


def assets(release):
    if release.get("draft") or release.get("prerelease"):
        raise ValueError("Expected a published stable release")
    tag = release.get("tag_name", "")
    if not re.fullmatch(r"v\d+\.\d+\.\d+", tag):
        raise ValueError("Invalid stable release version")
    result = {}
    for asset in release.get("assets", []):
        name = asset.get("name", "")
        if name in result:
            raise ValueError("Duplicate release asset")
        parsed = urllib.parse.urlsplit(asset.get("browser_download_url", ""))
        if (parsed.scheme != "https" or parsed.netloc != "github.com" or parsed.query or parsed.fragment
                or parsed.path != f"/{REPOSITORY}/releases/download/{tag}/{name}"
                or "/" in name or ".." in name):
            raise ValueError("Unexpected release asset URL")
        if asset.get("state") != "uploaded" or asset.get("size", 0) <= 0:
            raise ValueError("Release asset is incomplete")
        result[name] = asset
    apk_names(tag, result)
    for required in ["SHA256SUMS.txt"]:
        if required not in result:
            raise ValueError(f"Missing release asset: {required}")
    if not release.get("body", "").strip():
        raise ValueError("Release notes are empty")
    return tag, result


def download(asset, destination, limit=200 * 1024 * 1024):
    request = urllib.request.Request(asset["browser_download_url"], headers={"User-Agent": "Aroha-Release-Health"})
    try:
        with urllib.request.urlopen(request, timeout=45) as response, destination.open("wb") as output:
            count = 0
            while chunk := response.read(64 * 1024):
                count += len(chunk)
                if count > limit:
                    raise ValueError("Release asset exceeds size limit")
                output.write(chunk)
        if count != asset["size"]:
            raise ValueError("Truncated release asset")
        digest = asset.get("digest")
        if digest and digest != "sha256:" + sha256(destination):
            raise ValueError("Release asset digest mismatch")
    except BaseException:
        destination.unlink(missing_ok=True)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--previous", type=Path, help="Download baseline APK; no monitor report")
    parser.add_argument("--allow-first-release", action="store_true")
    parser.add_argument("--tools", type=Path)
    parser.add_argument("--certificate")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    release = fetch_release()
    if release is None:
        if args.previous and args.allow_first_release:
            print("No stable release: explicit first-release bootstrap enabled.")
            return
        raise ValueError("No published stable release found")
    tag, available = assets(release)
    if args.previous:
        download(available[apk_names(tag, available)[1]], args.previous)
        print(f"Downloaded previous stable APK for {tag}.")
        return
    if not args.tools or not args.certificate:
        parser.error("Monitoring requires --tools and --certificate")
    with tempfile.TemporaryDirectory() as directory:
        directory = Path(directory)
        sums = directory / "SHA256SUMS.txt"
        download(available[sums.name], sums, limit=64 * 1024)
        expected = {}
        for line in sums.read_text().splitlines():
            digest, name = line.split(maxsplit=1)
            name = name.removeprefix("*").removeprefix("./")
            expected[name] = fingerprint(digest)
        identities = []
        names = apk_names(tag, available)
        for name in names:
            target = directory / name
            download(available[name], target)
            if expected.get(name) != sha256(target):
                raise ValueError("Published APK checksum mismatch")
            identity = inspect_apk(target, args.tools)
            validate(identity, args.certificate, tag[1:], identity["version_code"], first_release=True)
            identities.append(identity)
        if any(identity != identities[0] for identity in identities[1:]):
            raise ValueError("Latest and versioned APK contents differ")
        report = {"status": "passed", "tag": tag, "apk": identities[0]}
        if args.output:
            args.output.write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
