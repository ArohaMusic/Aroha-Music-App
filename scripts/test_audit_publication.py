"""Verify publication checks against committed Git trees and fresh-history boundaries."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from audit_publication import audit_commit
from public_source import LEGAL_PAIRS


class PublicationAuditTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        for source, asset in LEGAL_PAIRS:
            for name in (source, f"app/src/main/assets/licenses/{asset}"):
                p = self.root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text("License fixture\n")
        for name in ("app/src/main/assets/fonts/PlusJakartaSans-OFL.txt", "app/src/main/java/com/arohamusic/app/ui/LegalInformation.kt"):
            p = self.root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("Fixture\n")
        files = sorted(p.name for p in self.root.iterdir() if p.is_file())
        (self.root / "public-source.json").write_text(json.dumps({"files": [*files, "public-source.json"], "directories": ["app", "LICENSES"]}))
        self.git("init", "--template=", "-b", "main")
        self.git("config", "user.name", "Fixture")
        self.git("config", "user.email", "fixture@users.noreply.github.com")
        self.commit()

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.root), *args], stderr=subprocess.PIPE)

    def commit(self):
        self.git("add", ".")
        self.git("-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null", "commit", "-qm", "Fixture")

    def test_clean_initial_committed_source_passes(self):
        report = audit_commit(self.root, fresh=True)
        self.assertEqual(1, report["reachable_commits"])
        self.assertEqual(len(self.git("ls-files").splitlines()), report["file_count"])

    def test_second_commit_is_rejected_for_fresh_publication(self):
        self.git("-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null", "commit", "--allow-empty", "-qm", "Second")
        with self.assertRaisesRegex(ValueError, "exactly one"):
            audit_commit(self.root, fresh=True)

    def test_additional_ref_is_rejected(self):
        self.git("branch", "old-history")
        with self.assertRaisesRegex(ValueError, "no other refs"):
            audit_commit(self.root, fresh=True)

    def test_untracked_file_prevents_false_clean_report(self):
        (self.root / "pending.kt").write_text("pending")
        with self.assertRaisesRegex(ValueError, "pending source"):
            audit_commit(self.root)

    def test_extra_committed_file_outside_export_allowlist_is_rejected(self):
        (self.root / "unreviewed.txt").write_text("outside manifest")
        self.commit()
        with self.assertRaisesRegex(ValueError, "differs from"):
            audit_commit(self.root)

    def test_committed_symlink_is_rejected(self):
        (self.root / "app/link.txt").symlink_to("../LICENSE")
        self.commit()
        with self.assertRaisesRegex(ValueError, "Symlink"):
            audit_commit(self.root)

    def test_committed_secret_is_detected_without_echo(self):
        credential = "ghp_" + "x" * 36
        (self.root / "app/accidental.kt").write_text(credential)
        self.commit()
        with self.assertRaisesRegex(ValueError, "GitHub token") as result:
            audit_commit(self.root)
        self.assertNotIn(credential, str(result.exception))

    def test_private_author_identity_is_rejected(self):
        self.git("config", "user.email", "private@example.invalid")
        self.git("-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null", "commit", "--amend", "--reset-author", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "noreply"):
            audit_commit(self.root, fresh=True)


if __name__ == "__main__":
    unittest.main()
