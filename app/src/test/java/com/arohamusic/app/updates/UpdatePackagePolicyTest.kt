package com.arohamusic.app.updates

import org.junit.Assert.*
import org.junit.Test

class UpdatePackagePolicyTest {
    private fun accepts(id: String = "com.arohamusic.app", code: Long = 45, version: String = "1.25.0", signers: Set<String> = setOf("production")) =
        UpdatePackagePolicy.accepts("com.arohamusic.app", 44, setOf("production"), id, code, version, signers, "1.25.0")
    @Test fun sameIdentityAndNewerVersionPass() { assertTrue(accepts()) }
    @Test fun wrongApplicationSignerOrVersionIsRejected() {
        assertFalse(accepts(id = "malicious.app")); assertFalse(accepts(signers = setOf("other")))
        assertFalse(accepts(version = "1.26.0")); assertFalse(accepts(code = 44)); assertFalse(accepts(code = 43))
        assertFalse(accepts(signers = emptySet())); assertFalse(accepts(signers = setOf("production", "other")))
    }
    @Test fun redirectPolicyRejectsCleartextSpoofedHostsAndNonstandardPorts() {
        assertTrue(UpdatePackagePolicy.redirectAllowed("https", "release-assets.githubusercontent.com", 443))
        assertFalse(UpdatePackagePolicy.redirectAllowed("http", "github.com", 443))
        assertFalse(UpdatePackagePolicy.redirectAllowed("https", "github.com.evil.example", 443))
        assertFalse(UpdatePackagePolicy.redirectAllowed("https", "github.com", 444))
    }
}
