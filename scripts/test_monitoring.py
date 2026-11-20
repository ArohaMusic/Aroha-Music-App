import json
import tempfile
import unittest
import urllib.error
from unittest.mock import patch
from pathlib import Path

from release_health import REPOSITORY, assets, apk_names, fetch_release
from security_audit import check_actions, queries


class MonitoringTest(unittest.TestCase):
    def release(self):
        names = ["Aroha-Music-1.25.0.apk", "Aroha-Music-latest.apk", "SHA256SUMS.txt"]
        return dict(tag_name="v1.25.0", body="Release notes", assets=[dict(name=name, state="uploaded", size=100,
            browser_download_url=f"https://github.com/{REPOSITORY}/releases/download/v1.25.0/{name}") for name in names])

    def test_release_contract(self):
        tag, available = assets(self.release())
        self.assertEqual("v1.25.0", tag)
        self.assertEqual(3, len(available))

    def test_unpublished_release_and_api_errors_remain_distinct(self):
        missing = urllib.error.HTTPError("https://api.github.com/fixture", 404, "missing", {}, None)
        with patch("release_health.urllib.request.urlopen", side_effect=missing) as request:
            self.assertIsNone(fetch_release())
            self.assertEqual(1, request.call_count)
            self.assertEqual("https://api.github.com/repos/lxexe/Aroha-Music-App/releases/latest", request.call_args.args[0].full_url)
        denied = urllib.error.HTTPError("https://api.github.com/fixture", 403, "denied", {}, None)
        with patch("release_health.urllib.request.urlopen", side_effect=denied) as request:
            with self.assertRaises(urllib.error.HTTPError):
                fetch_release()
            self.assertEqual(1, request.call_count)

    def test_missing_asset_and_untrusted_url_fail(self):
        release = self.release()
        release["assets"].pop()
        with self.assertRaises(ValueError):
            assets(release)
        release = self.release()
        release["assets"][0]["browser_download_url"] = "https://evil.example/update.apk"
        with self.assertRaises(ValueError):
            assets(release)

    def test_versioned_and_latest_apks_are_both_required(self):
        for missing in ("Aroha-Music-1.25.0.apk", "Aroha-Music-latest.apk"):
            release = self.release()
            release["assets"] = [a for a in release["assets"] if a["name"] != missing]
            with self.assertRaises(ValueError):
                assets(release)

    def test_drafts_and_incomplete_assets_fail(self):
        for key, value in [("draft", True), ("prerelease", True), ("body", "")]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                assets(dict(self.release(), **{key: value}))

    def test_inventory_includes_transitive_modules_and_deduplicates(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "inventory.json"
            path.write_text(json.dumps({"configurations": {"runtime": ["a:b:1", "a:c:2"], "other": ["a:b:1"]}}))
            self.assertEqual(2, len(queries([path, path])))

    def test_mutable_actions_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            workflows = root / ".github/workflows"
            workflows.mkdir(parents=True)
            workflow = workflows / "test.yml"
            workflow.write_text("steps:\n  - uses: actions/checkout@v7\n")
            self.assertEqual(1, len(check_actions(root)))
            workflow.write_text("steps:\n  - uses: actions/checkout@" + "a" * 40 + " # v7\n")
            self.assertEqual([], check_actions(root))
