#!/usr/bin/env python3
"""Reject Firebase client configuration registered for another Android application."""
import json
from pathlib import Path
import sys

from release_safety import APP_ID


def validate(path):
    try:
        config = json.loads(Path(path).read_text())
        clients = config.get("client", [])
        matched = any(client.get("client_info", {}).get("android_client_info", {}).get("package_name") == APP_ID
                      for client in clients)
    except (OSError, ValueError, TypeError, AttributeError):
        raise ValueError("Cannot read a valid Firebase Android client configuration") from None
    if not matched:
        raise ValueError(f"Register Firebase Android app {APP_ID} and download its google-services.json")


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            raise ValueError("Usage: python3 scripts/validate_firebase.py PATH")
        validate(sys.argv[1])
    except ValueError as error:
        sys.exit(str(error))
    print("Firebase Android application ID verified.")
