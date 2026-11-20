# App safety and update backlog

Updated: 19 November 2026. The app and repository safeguards below are implemented for 1.25.0; production account configuration, hosted workflow runs and real-phone release checks remain separate. Unchecked items still need work or verification. See [operations and setup](OPERATIONS.md) and the implementation evidence in [validation](VALIDATION.md).

The initial implementation covers all eight work areas; complete the remaining production checks in the numbered order below. Record a completion date and link to the change or validation evidence when closing a task. Keep credentials and personal diagnostics out of this document and public issues.

| ID | Priority | Task | Status | Depends on |
| --- | --- | --- | --- | --- |
| SAFE-01 | 1 | Optional crash and freeze reporting | Implemented; deployment/setup pending | — |
| SAFE-02 | 2 | Automatic release safeguards | Implemented; deployment/setup pending | — |
| SAFE-03 | 3 | Update installation and data preservation tests | Implemented; deployment/setup pending | SAFE-02 |
| SAFE-04 | 4 | Build and repository security | Implemented; deployment/setup pending | — |
| SAFE-05 | 5 | Provider health checks | Implemented; deployment/setup pending | — |
| SAFE-06 | 6 | Update delivery monitoring | Implemented; deployment/setup pending | SAFE-02, SAFE-03 |
| SAFE-07 | Follow-up | Guided download and installation | Implemented; deployment/setup pending | SAFE-02, SAFE-03, SAFE-06 |
| SAFE-08 | Alongside rollout | Maintainer health dashboard and response process | Implemented; deployment/setup pending | Data from SAFE-01, SAFE-04, SAFE-05, SAFE-06 |

## SAFE-01: Optional crash and freeze reporting

Validation update: the user-provided Firebase dashboard screenshot confirms the expected `CrashlyticsVerificationCrash` from 1.25.0 reached the configured project (one event, one reporting user). Debug fatal-crash delivery is verified; alert configuration and the remaining coverage/privacy checks below are still pending.

Before this implementation: optional Firebase builds provide basic usage statistics; crash reporting uploads are not implemented. Standard builds contain no Firebase SDK.

- [x] Select and integrate crash reporting in the optional reporting build, retaining the SDK-free build.
- [x] Add a separate user-controlled diagnostics preference; keep remote crash reporting off until the user opts in and preserve that choice across upgrades and restarts.
- [x] Record app/build version, Android version, sanitized error category and stack trace. Support application-not-responding reports where the platform and reporting service allow them; document coverage limits.
- [ ] Remove song/search/library data, profile names, identifiers supplied by the user, cookies, tokens and signed stream URLs from diagnostic payloads and breadcrumbs. Review SDK-collected metadata and defaults as well as our own logs.
- [ ] Upload release obfuscation mappings securely so production stack traces are readable.
- [x] Update the privacy disclosure, bundled legal copy and Settings explanation with collection, retention, deletion and withdrawal behavior.
- [ ] Configure maintainer alerts for new fatal issues and sudden stability regressions.

Completion: a controlled test crash from a disposable opted-in installation produces a readable report for the correct build; an opted-out installation sends no diagnostic report. Verify restart, upgrade and withdrawal behavior, SDK-free builds, and supported freeze-reporting coverage. Record the evidence without personal payloads.

## SAFE-02: Automatic release safeguards

Before this implementation: the release workflow validates the tag/version name, builds and checks the app, signs and verifies the APK, publishes checksums and creates a draft release. It does not compare the signing certificate or version code with the previous production release.

- [x] Enforce a strictly increasing `versionCode` against the previous published stable APK or independently verified release metadata. Define the first-release case and fail clearly if the baseline cannot be verified.
- [x] Compare the signed APK's application ID, version name/code and production signing certificate fingerprint against the expected release identity. Test rejection of an incorrect key or version.
- [ ] Publish the production certificate fingerprint and document how users can verify release authenticity; distinguish the APK checksum from its signing identity.
- [ ] Confirm encrypted private backups of the production keystore and passwords exist outside the repository; document a private recovery procedure and verify recovery without publishing secrets.
- [ ] Document a signing-key incident procedure and restrict who can access or change release secrets and release automation.
- [x] Keep draft APK review before publication and document how to ship a corrective release with a higher version code if a published version is faulty.

Completion: release checks reject the wrong identity and non-increasing version code, a correctly signed release passes, and the private key recovery process has been verified. Store only non-secret evidence in the repository.

## SAFE-03: Update installation and data preservation tests

Before this implementation: unit and device tests cover update discovery and presentation; release guidance calls for manual APK testing.

- [x] Add an automated upgrade scenario that seeds the previous version with synthetic favorites, playlists, settings and queue state, then installs the candidate over it without clearing app data.
- [ ] Check history, download records, offline files and backup compatibility where supported; verify any storage migration and behavior after process restart.
- [ ] Use a dedicated test signing key for automated fixtures and separately test the actual production-signed APK over the previous production APK before publication.
- [ ] Cover the oldest supported Android version and a current supported version; include representative real-device checks.
- [ ] Confirm playback, search, background controls and offline playback work after upgrading; check Wi-Fi/mobile switching and interrupted network recovery on a phone.
- [ ] Attach a concise release checklist recording the old/new versions, device/Android versions, signing identity and pass/fail results.

Completion: the upgrade preserves seeded data and supported offline files, the installed version is correct, and core flows pass on the documented test matrix. Any device or carrier gaps remain explicitly recorded.

## SAFE-04: Build and repository security

Before this implementation: weekly Dependabot checks, wrapper validation, source auditing, runtime dependency inventories, tests and lint are configured. Workflow actions currently use version tags.

- [x] Lock GitHub Actions dependencies to reviewed immutable references using supported workflow lockfiles or full commit SHAs; keep automated reviewable updates.
- [ ] Add dependency vulnerability review to pull requests and a scheduled advisory check. Include transitive dependencies and both SDK-free and Firebase variants where supported.
- [x] Configure appropriate source security analysis for Kotlin/Java and triage findings.
- [ ] Verify live repository settings for secret scanning/push protection where available, protected main/release tags, required checks and review of workflow/signing changes.
- [ ] Review workflow token permissions and isolate signing secrets to the steps/jobs that require them; protect release environments where supported.
- [ ] Confirm maintainer account protection and establish an owner and response process for security alerts.
- [ ] Review compatibility exclusions against security advisories, including dependencies intentionally held below major upgrades. Record a mitigation or migration task for unresolved vulnerabilities.

Completion: security checks run and report actionable results, workflow dependencies are locked, live repository controls are recorded as verified or unavailable, and findings have an owner and resolution or documented mitigation.

## SAFE-05: Provider health checks

Before this implementation: provider and playback device tests exist, but continuous provider health monitoring is not established by this review.

- [x] Define a small, permitted public test catalog for search, stream resolution, playback startup, lyrics and supported downloads; keep synthetic/library fixtures separate from user data.
- [x] Run lightweight scheduled smoke checks with bounded requests, timeouts, retries and provider-friendly frequency.
- [x] Record provider availability, failure category and latency; distinguish missing content, network failures, rate limits and provider verification restrictions.
- [ ] Alert on sustained failures or regressions rather than individual unavailable tracks; assign a maintainer to triage failures.
- [x] Document manual real-device and mobile-network checks that hosted monitoring cannot represent.
- [ ] Review fallback and user-facing recovery behavior when a provider is unavailable.

Completion: a controlled provider failure is detected and categorized, healthy checks recover cleanly, and the response procedure identifies who investigates and which user flows need manual verification.

## SAFE-06: Update delivery monitoring

Before this implementation: morning/evening GitHub checks, foreground catch-up, manual checks, cached release notes, backoff, trusted download links and optional local update notifications are implemented. Downloads open in the browser and installation is manual.

- [x] Check each published stable release for the expected versioned/latest APKs, matching contents, checksums, release notes and installable release identity.
- [ ] Add a post-publication download-link check and recurring low-frequency check; report failures without repeatedly downloading large APKs.
- [ ] Extend automated scenarios for offline/reconnection, rate limits, notification denial, dismissal, reboot, clock changes and Android background deferral wherever current coverage is incomplete.
- [ ] Verify update notification taps open the correct release notes and a newer release remains discoverable after an earlier version was dismissed.
- [x] Document how the maintainer detects and corrects a broken release asset or update endpoint.
- [x] Use existing optional version-adoption analytics where available, labeling counts as participating installations. If adding update failure telemetry, make it optional, sanitized and disclosed; do not treat a download click as successful installation.

Completion: broken/missing release assets are detected, update discovery and notification flows pass the documented scenarios, and a manual end-to-end check reaches installation of the correct signed version.

## SAFE-07: Guided download and installation

- [x] Design progress, cancellation, retry and clear installation guidance for the official APK.
- [x] Download only from trusted release assets; verify integrity, application ID, expected signing identity and that the version is newer before opening the Android installer.
- [x] Request installation access only when the user chooses to install; use Android's installation confirmation and handle denial, insufficient storage and interrupted downloads.
- [x] Use narrowly scoped file sharing, clean up temporary files and preserve a browser-download fallback.
- [x] Recheck the installed version on next launch instead of assuming an installer launch means success; update privacy and release documentation.

Completion: the user can safely download, cancel, retry and install a valid update; invalid APKs are rejected and installer failure or denial leaves the current app usable.

## SAFE-08: Maintainer health dashboard and response process

- [x] Create one maintainer view linking stability reports, provider checks, update delivery checks, dependency alerts and release workflow results.
- [ ] Track crashes/freezes by app version, playback failures and startup latency, search/lyrics/download failures, update-check failures and version adoption as data becomes available. Distinguish synthetic checks, optional diagnostic samples and local-only measurements.
- [ ] Define metric denominators, data retention, access permissions and useful alert thresholds after collecting a baseline; avoid uploading listening content or raw stream URLs.
- [ ] Assign an owner for each alert category and review unresolved alerts weekly and after every release.
- [x] Document incident triage, reproduction, user-facing communication through release notes/support channels, corrective release validation and closure evidence.

Completion: each metric has a source and coverage limits, a controlled failure reaches the expected maintainer view, and the documented response process can be followed without accessing personal listening data.

## Implemented source and validation

- App: `analytics/`, `updates/UpdateDownload.kt`, `updates/UpdateInstaller.kt` and Settings → App health.
- Release identity: `scripts/release_safety.py`, `scripts/release_health.py` and the signed release workflow.
- Security/provider/upgrade monitoring: `.github/workflows/security.yml`, `health.yml`, `upgrade.yml`, `scripts/security_audit.py` and `scripts/upgrade_check.py`.
- Verification: Kotlin policy/download/privacy tests, Python release/monitor tests, and a successful local install-over-baseline preservation check on a disposable emulator. Final device/build details are in VALIDATION.md.

## Implementation references

- [Release and signing workflow](RELEASING.md)
- [Optional Firebase setup](FIREBASE.md)
- [Existing validation evidence and device limitations](VALIDATION.md)
- [Contribution and dependency review requirements](../CONTRIBUTING.md)
- [Security reporting](../SECURITY.md)
- [Current privacy disclosure](../PRIVACY.md)

Consult current official Android, GitHub and reporting-service documentation when implementing platform or service changes. Implementation in this checkout is distinct from deployment: hosted checks require a push, console controls require account setup, and a public APK requires the normal reviewed release process.
