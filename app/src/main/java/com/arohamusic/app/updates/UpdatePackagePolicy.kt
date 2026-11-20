package com.arohamusic.app.updates

/** Shared by archive verification and unit tests; the Android installer remains the final authority. */
internal object UpdatePackagePolicy {
    fun accepts(installedId: String, installedCode: Long, installedSigners: Set<String>, candidateId: String,
        candidateCode: Long, candidateVersion: String?, candidateSigners: Set<String>, expectedVersion: String): Boolean =
        candidateId == installedId && candidateCode > installedCode && candidateVersion == expectedVersion &&
            installedSigners.isNotEmpty() && candidateSigners == installedSigners

    fun redirectAllowed(scheme: String, host: String, port: Int): Boolean = scheme == "https" && port == 443 &&
        host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
}
