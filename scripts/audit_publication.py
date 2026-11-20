#!/usr/bin/env python3
"""Audit the exact committed source before publishing, without trusting a working-tree scan."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

from public_source import ROOT, checked_files


def git(root: Path, *args: str) -> bytes:
    return subprocess.check_output(["git", "-C", str(root), *args], stderr=subprocess.PIPE)


def audit_commit(root: Path, fresh: bool = False) -> dict:
    if git(root, "status", "--porcelain", "--untracked-files=all").strip():
        raise ValueError("Commit or remove pending source changes before auditing publication")
    commit = git(root, "rev-parse", "HEAD").decode().strip()
    count = int(git(root, "rev-list", "--count", "--all"))
    if fresh:
        refs = git(root, "for-each-ref", "--format=%(refname)").decode().splitlines()
        if count != 1 or refs != ["refs/heads/main"]:
            raise ValueError("Fresh publication requires exactly one initial main commit and no other refs")
        identities = git(root, "show", "-s", "--format=%ae%n%ce", "HEAD").decode().splitlines()
        if any(not value.endswith("@users.noreply.github.com") for value in identities):
            raise ValueError("Fresh publication must use the reviewed GitHub noreply author/committer identity")
    entries = git(root, "ls-tree", "-rz", "HEAD").split(b"\0")
    paths = set()
    for entry in filter(None, entries):
        metadata, encoded = entry.split(b"\t", 1)
        mode, kind, _ = metadata.split()
        path = Path(encoded.decode("utf-8"))
        if mode not in (b"100644", b"100755") or kind != b"blob":
            raise ValueError(f"Symlink, submodule or unsupported Git entry: {path}")
        if path.is_absolute() or ".." in path.parts:
            raise ValueError("Unsafe committed path")
        paths.add(path)
    with tempfile.TemporaryDirectory(prefix="aroha-publication-") as temporary:
        snapshot = Path(temporary)
        with zipfile.ZipFile(io.BytesIO(git(root, "archive", "--format=zip", "HEAD"))) as archive:
            for entry in archive.infolist():
                path = Path(entry.filename)
                if path.is_absolute() or ".." in path.parts:
                    raise ValueError("Unsafe archived path")
                if entry.is_dir():
                    continue
                destination = snapshot / path
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(archive.read(entry))
        audited = set(checked_files(snapshot))
        if paths != audited:
            extra = sorted(str(p) for p in paths - audited)
            raise ValueError("Committed tree differs from the audited source allowlist: " + ", ".join(extra))
        files = [{"path": str(p), "bytes": (snapshot / p).stat().st_size,
                  "sha256": hashlib.sha256((snapshot / p).read_bytes()).hexdigest()} for p in sorted(paths)]
    return {"commit": commit, "reachable_commits": count, "fresh_history": fresh,
            "file_count": len(files), "files": files}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fresh", action="store_true", help="require a single reviewed initial main commit")
    parser.add_argument("--output", type=Path, help="write a non-secret per-file hash manifest outside tracked source")
    args = parser.parse_args()
    try:
        report = audit_commit(ROOT, fresh=args.fresh)
        if args.output:
            output = args.output.resolve()
            if ".git" in output.parts or (output.is_relative_to(ROOT) and not output.is_relative_to(ROOT / "dist")):
                raise ValueError("Write the audit report outside source or under ignored dist/")
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Committed publication audit passed: {report['file_count']} files, {report['reachable_commits']} reachable commit(s), HEAD {report['commit'][:12]}.")
        return 0
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        # Do not print command stderr or matched credential values.
        print(str(error) if isinstance(error, ValueError) else "Publication audit could not complete", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
