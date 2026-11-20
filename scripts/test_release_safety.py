import unittest

from release_safety import APP_ID, fingerprint, validate


class ReleaseSafetyTest(unittest.TestCase):
    def setUp(self):
        self.candidate = dict(application_id=APP_ID, version="1.25.0", version_code=45, certificate_sha256="ab" * 32)
        self.previous = dict(self.candidate, version="1.24.13", version_code=44)

    def check(self, candidate=None, previous=None, **kwargs):
        validate(candidate or self.candidate, "ab" * 32, "1.25.0", 45,
                 self.previous if previous is None else previous, **kwargs)

    def test_valid_upgrade(self):
        self.check()

    def test_rejects_wrong_signer_app_or_version(self):
        for key, value in [("certificate_sha256", "cd" * 32), ("application_id", "other.app"),
                           ("version", "1.26.0"), ("version_code", 44)]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.check(dict(self.candidate, **{key: value}))

    def test_rejects_old_or_equal_version_and_different_previous_identity(self):
        for changes in [dict(version_code=45), dict(version_code=46), dict(certificate_sha256="cd" * 32), dict(application_id="other")]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                self.check(previous=dict(self.previous, **changes))

    def test_requires_explicit_bootstrap(self):
        with self.assertRaises(ValueError):
            validate(self.candidate, "ab" * 32, "1.25.0", 45)
        validate(self.candidate, "ab" * 32, "1.25.0", 45, first_release=True)

    def test_fingerprint_format(self):
        self.assertEqual("ab" * 32, fingerprint(":".join(["AB"] * 32)))
        for value in ["", "a" * 63, "z" * 64]:
            with self.assertRaises(ValueError):
                fingerprint(value)
