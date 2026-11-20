"""Exercise the security boundaries of the public-source exporter."""
import json
import hashlib
from pathlib import Path
import stat
import tempfile
import unittest
import zipfile

from export_source import export
from public_source import audit, public_files


class PublicSourceTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "app/src").mkdir(parents=True)
        (self.root / "gradlew").write_text("#!/bin/sh\n")
        (self.root / "app/src/song.kt").write_text('val title = "Song"\n')
        (self.root / "public-source.json").write_text(json.dumps({"files": ["gradlew"], "directories": ["app/src"]}))

    def test_export_excludes_history_and_machine_files_and_preserves_wrapper_mode(self):
        for name in [".git/config", "app/build/debug.apk", "dist/old.apk", "local.properties", "app/google-services.json"]:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("private local state")
        files = public_files(self.root)
        output = self.root / "public.zip"
        export(self.root, files, output)
        with zipfile.ZipFile(output) as archive:
            self.assertEqual(["aroha-music/app/src/song.kt", "aroha-music/gradlew"], archive.namelist())
            self.assertTrue((archive.getinfo("aroha-music/gradlew").external_attr >> 16) & stat.S_IXUSR)
            self.assertEqual(b'val title = "Song"\n', archive.read("aroha-music/app/src/song.kt"))

    def test_accidentally_added_signing_key_blocks_export(self):
        (self.root / "app/src/release.jks").write_bytes(b"private")
        with self.assertRaisesRegex(ValueError, "Private/generated"):
            public_files(self.root)

    def test_symlink_cannot_escape_source_tree(self):
        private = self.root / "private.txt"
        private.write_text("private")
        (self.root / "app/src/link.txt").symlink_to(private)
        with self.assertRaisesRegex(ValueError, "Symlink"):
            public_files(self.root)

    def test_secret_is_reported_without_echoing_it(self):
        credential = "ghp_" + "x" * 36
        path = Path("app/src/accidental.txt")
        (self.root / path).write_text(credential)
        result = audit(self.root, [path])
        self.assertEqual(1, len(result))
        self.assertIn("GitHub token", result[0])
        self.assertNotIn(credential, result[0])

    def test_symlink_ancestor_of_allowlisted_file_is_rejected(self):
        (self.root / "private").mkdir()
        (self.root / "private/config.txt").write_text("private")
        (self.root / "linked").symlink_to(self.root / "private", target_is_directory=True)
        (self.root / "public-source.json").write_text(json.dumps({"files": ["linked/config.txt"], "directories": []}))
        with self.assertRaisesRegex(ValueError, "Symlink"):
            public_files(self.root)

    def test_manifest_cannot_traverse_outside_workspace(self):
        (self.root / "public-source.json").write_text(json.dumps({"files": ["../private.txt"], "directories": []}))
        with self.assertRaisesRegex(ValueError, "relative in-tree"):
            public_files(self.root)

    def test_test_fixture_with_synthetic_stream_signature_is_valid(self):
        path = Path("app/src/fixture.kt")
        (self.root / path).write_text('val url = "https://r1.googlevideo.com/videoplayback?sig=first"')
        self.assertEqual([], audit(self.root, [path]))

    def test_manifest_cannot_explicitly_include_git_history(self):
        (self.root / ".git").mkdir()
        (self.root / ".git/config").write_text("private remote configuration")
        (self.root / "public-source.json").write_text(json.dumps({"files": [".git/config"], "directories": []}))
        with self.assertRaisesRegex(ValueError, "Private/generated directory"):
            public_files(self.root)

    def test_unknown_photo_or_media_is_rejected(self):
        (self.root / "app/src/unused.jpg").write_bytes(b"image")
        with self.assertRaisesRegex(ValueError, "Unreviewed file type"):
            public_files(self.root)

    def test_saved_database_blocks_export(self):
        (self.root / "app/src/listening.sqlite3").write_bytes(b"saved data")
        with self.assertRaisesRegex(ValueError, "Private/generated"):
            public_files(self.root)

    def test_dist_cannot_be_explicitly_exported(self):
        (self.root / "dist").mkdir()
        (self.root / "dist/old.kt").write_text("historical source")
        (self.root / "public-source.json").write_text(json.dumps({"files": ["dist/old.kt"], "directories": []}))
        with self.assertRaisesRegex(ValueError, "Private/generated directory"):
            public_files(self.root)

    def test_nul_cannot_hide_credential_in_text(self):
        path = Path("app/src/accidental.kt")
        credential = "ghp_" + "x" * 36
        (self.root / path).write_bytes(b"\0" + credential.encode())
        result = audit(self.root, [path])
        self.assertTrue(any("GitHub token" in x for x in result))
        self.assertTrue(any("NUL" in x for x in result))
        self.assertNotIn(credential, "\n".join(result))

    def test_unreviewed_png_is_rejected(self):
        path = Path("app/src/image.png")
        (self.root / path).write_bytes(b"\x89PNG\0unreviewed")
        self.assertIn("Unreviewed or modified binary", audit(self.root, [path])[0])

    def test_reviewed_binary_must_match_digest(self):
        path = Path("app/src/icon.png")
        content = b"\x89PNG\0reviewed fixture"
        (self.root / path).write_bytes(content)
        (self.root / "public-assets.json").write_text(json.dumps({"binary_assets": [{
            "path": str(path), "sha256": hashlib.sha256(content).hexdigest(), "purpose": "Synthetic scanner fixture"
        }]}))
        self.assertEqual([], audit(self.root, [path]))
        (self.root / path).write_bytes(content + b"modified")
        self.assertIn("Unreviewed or modified binary", audit(self.root, [path])[0])

    def test_stale_inventory_entry_is_rejected(self):
        (self.root / "public-assets.json").write_text(json.dumps({"binary_assets": [{
            "path": "missing.png", "sha256": "a" * 64, "purpose": "Removed asset"
        }]}))
        self.assertIn("has no public file", audit(self.root, [Path("gradlew")])[0])

    def test_duplicate_inventory_entries_are_rejected(self):
        entry = {"path": "missing.png", "sha256": "a" * 64, "purpose": "Synthetic scanner fixture"}
        (self.root / "public-assets.json").write_text(json.dumps({"binary_assets": [entry, entry]}))
        self.assertTrue(any("duplicate" in x for x in audit(self.root, [])))

    def test_non_utf8_source_cannot_bypass_scan(self):
        path = Path("app/src/accidental.kt")
        (self.root / path).write_bytes('password = "sensitive value"'.encode("utf-16"))
        self.assertIn("Non-UTF-8", audit(self.root, [path])[0])

    def test_production_dummy_artwork_is_rejected(self):
        path = Path("app/src/main/Demo.kt")
        (self.root / path).parent.mkdir(parents=True)
        (self.root / path).write_text('val image = "https://picsum.photos/400"')
        self.assertIn("demo media", audit(self.root, [path])[0])


if __name__ == "__main__":
    unittest.main()
