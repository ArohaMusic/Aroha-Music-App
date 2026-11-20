#!/usr/bin/env python3
"""Run a real install-over-existing-data scenario on a fresh disposable emulator."""
import argparse
import json
import subprocess
from pathlib import Path

from release_safety import APP_ID, inspect_apk


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--tools", type=Path, required=True)
    parser.add_argument("--previous", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--tests", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    adb = [str(args.adb), "-s", args.serial]

    def run(*command):
        return subprocess.run([*adb, *command], check=True, capture_output=True, text=True).stdout

    if not args.serial.startswith("emulator-") or run("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise ValueError("Use a fresh disposable Android emulator, never a personal device")
    if run("shell", "pm", "list", "packages", APP_ID).strip():
        raise ValueError("Emulator already contains Aroha Music; refusing to replace or clear its data")
    previous = inspect_apk(args.previous, args.tools)
    candidate = inspect_apk(args.candidate, args.tools)
    if (previous["application_id"] != APP_ID or candidate["application_id"] != APP_ID
            or previous["certificate_sha256"] != candidate["certificate_sha256"]
            or candidate["version_code"] < previous["version_code"]):
        raise ValueError("Fixture APKs must share application/signing identity and cannot downgrade")
    run("install", str(args.previous))
    run("install", str(args.tests))

    def instrument(method):
        result = run("shell", "am", "instrument", "-w", "-r", "-e", "upgradeFixture", "true", "-e", "class",
                     f"com.arohamusic.app.UpgradePreservationTest#{method}",
                     "com.arohamusic.app.test/androidx.test.runner.AndroidJUnitRunner")
        if "OK (1 test)" not in result or "FAILURES" in result or "INSTRUMENTATION_FAILED" in result:
            raise ValueError(f"Upgrade fixture {method} did not pass: {result}")

    instrument("seed")
    run("shell", "am", "force-stop", APP_ID)
    run("install", "-r", str(args.candidate))
    instrument("verify")
    # Verify again after another process restart, without reseeding.
    run("shell", "am", "force-stop", APP_ID)
    instrument("verify")
    args.output.write_text(json.dumps({"status": "passed", "previous": previous, "candidate": candidate}, indent=2) + "\n")
    print("Upgrade preserved fixture library, settings, queue, local audio, history and reporting opt-outs across restart.")


if __name__ == "__main__":
    main()
