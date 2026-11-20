#!/usr/bin/env python3
"""Check resolved Maven dependencies against OSV and workflow action immutability."""
import argparse
import json
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def queries(paths):
    modules = set()
    for path in paths:
        for values in json.loads(path.read_text())["configurations"].values():
            modules.update(values)
    result = []
    for module in sorted(modules):
        group, name, version = module.split(":", 2)
        result.append({"package": {"ecosystem": "Maven", "name": f"{group}:{name}"}, "version": version})
    return result


def check_actions(root=ROOT):
    findings = []
    for path in sorted((root / ".github/workflows").glob("*.y*ml")):
        for line in path.read_text().splitlines():
            match = re.match(r"\s*(?:-\s*)?uses:\s*([^\s#]+)", line)
            if match and not match[1].startswith("./") and not re.fullmatch(r"[^@]+@[0-9a-f]{40}", match[1]):
                findings.append(f"{path.name}: action must use a full commit SHA: {match[1]}")
    return findings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--actions-only", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    findings = check_actions()
    checked = 0
    if not args.actions_only:
        dependencies = queries(sorted((ROOT / "LICENSES").glob("runtime-dependencies*.json")))
        checked = len(dependencies)
        # Fail on API errors instead of reporting unverified dependencies as clean.
        for offset in range(0, len(dependencies), 100):
            batch = dependencies[offset:offset + 100]
            request = urllib.request.Request("https://api.osv.dev/v1/querybatch",
                data=json.dumps({"queries": batch}).encode(), headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(request, timeout=45) as response:
                results = json.load(response)["results"]
            if len(results) != len(batch):
                raise ValueError("Incomplete advisory response")
            for dependency, result in zip(batch, results):
                for advisory in result.get("vulns", []):
                    findings.append(f"{dependency['package']['name']}:{dependency['version']} — {advisory['id']}")
    report = {"dependencies_checked": checked, "findings": findings}
    if args.output:
        args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    raise SystemExit(1 if findings else 0)


if __name__ == "__main__":
    main()
