import json
from pathlib import Path
import tempfile
import unittest

from validate_firebase import validate


class FirebaseConfigurationTest(unittest.TestCase):
    def test_only_matching_registered_android_client_is_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "config.json"
            for package in ("com.example.another", "com.arohamusic.app"):
                path.write_text(json.dumps({"client": [{"client_info": {"android_client_info": {"package_name": package}}}]}))
                if package == "com.arohamusic.app":
                    validate(path)
                else:
                    with self.assertRaises(ValueError):
                        validate(path)

    def test_malformed_or_absent_config_fails_without_echoing_content(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "config.json"
            for content in ("not JSON", "[]", '{"client":null}'):
                path.write_text(content)
                with self.assertRaisesRegex(ValueError, "Cannot read a valid Firebase"):
                    validate(path)
            path.unlink()
            with self.assertRaises(ValueError):
                validate(path)
