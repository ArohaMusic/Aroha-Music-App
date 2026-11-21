# Operating app safety and updates

The repository maintainer owns security findings, stability triage, provider failures and release recovery. Implementation lives in the app, `scripts/` and the Android, Signed release, Security checks, Upgrade preservation and App health workflows. These workflows start after the changes are pushed to the connected repository; local edits do not enable hosted checks.

## One-time production setup

1. Run the existing private signing setup helper with the production key. It now also sets the public Actions variable `RELEASE_CERT_SHA256` from the certificate. Alternatively set that variable to the independently verified production certificate SHA-256 fingerprint. Never replace the existing production key to get a build passing.
2. For a genuinely first release only, set `ALLOW_FIRST_RELEASE=true` if there is no published stable APK. Remove it after publishing. Network/API errors never qualify as first-release bootstrap. Existing releases must pass comparison with the previous stable signed APK.
3. For optional Firebase builds, register Android app `com.arohamusic.app` in the intended Firebase project, download its client configuration, enable Crashlytics and configure fatal/regression alerts. SDK-free builds remain available. The app's separate crash reporting control defaults to off; basic usage statistics have their own independent preference.
4. Protect `main` and release tags. Require Android checks, Security checks and Upgrade preservation on pull requests, with code-owner review for workflows, signing, privacy and update code. Enable repository secret scanning and push protection where your plan permits them. Verify maintainer account MFA. These account controls are not applied by adding YAML or CODEOWNERS alone.
5. Enable GitHub Actions failure notifications for the maintainer. Review the App health workflow summary after each release and at least weekly. Review Security checks and dependency PRs weekly. Actions summaries/artifacts provide synthetic monitoring; Firebase provides optional reporting-instance stability/adoption information.
6. Keep two encrypted signing-key backups outside the repository, with privately stored passwords and a verified restore procedure. Record backup locations and recovery ownership in private maintainer records, never in source or issues.

## App controls

Settings → App health shows per-version local operation counts and average request times. Save health report writes only the displayed fixed-category counts, app version and Android version to a user-chosen JSON document. Counts can be cleared without clearing the library. They reset after an app version-code change and are not automatically uploaded. Request timings include the measured request/extraction work; they are not end-to-end time-to-audible playback measurements. Playback starts and error callbacks are separate event counts, so do not interpret their ratio as a session failure rate.

Optional Firebase builds expose Share crash reports. The app replaces forwarded Java exception messages/causes/suppressed details with an exception-class label and bounded source stack frames. It adds no custom user identifiers, media URLs, search/library fields or media breadcrumbs; Firebase can associate generic automatic Analytics event breadcrumbs when basic usage reporting is enabled. Crashlytics itself processes app/device/session metadata and supported OS ANR diagnostics; consult Firebase's collection disclosure. A foreground watchdog samples main-thread stalls longer than ten seconds when the app is resumed and no debugger is attached. These samples are nonfatal responsiveness reports, not Android-confirmed ANRs. Turning off reporting disables further collection and requests deletion of pending reports; it does not delete reports already processed by Firebase.

Release builds preserve R8 mappings as a separate Actions artifact. Only official release builds with `-PuploadCrashMappings=true` upload mappings to Firebase; ordinary local build verification skips that upload. Keep private mappings for every supported published build after Actions artifact retention expires. Test an intentionally crashing disposable opted-in installation and confirm a readable report before declaring production reporting verified. No production crash or real-user diagnostic upload is required for normal unit/device tests.

## Guided updates

The app downloads only on a user action, bounds download size and duration, validates the GitHub asset SHA-256 digest, checks application ID, exact release version, increasing Android version code and the installed signing identity, then offers Android's installation confirmation. It requests installation access only when Install update is selected. Redirects are restricted to known GitHub asset hosts. A release without a published GitHub SHA-256 asset digest uses the browser flow. The current verifier accepts the same signer set; planned signing-key rotations need a reviewed lineage migration before release.

Downloads can be canceled/retried, partial files are removed and interrupted downloads are cleared on process restart. The installer can be reopened if the user declines it. A launch of the installer is not treated as success: after a process restart the app checks the installed version code against the pending candidate. The browser remains an alternative. No silent update, exact background schedule or push delivery is promised.

## Release and upgrade checks

Signed release inspects both the previous production APK and candidate using Android build tools. It rejects a wrong app, wrong production certificate, source/APK version mismatch and non-increasing version code. It writes `release-identity.json` alongside the release APKs and existing checksums/source attachments.

Upgrade preservation builds the PR base and candidate with the same test signing key in separate source snapshots and runs on Android 8/API 26 and API 35 emulators. It seeds synthetic favorites, playlists, history, queue, local audio, settings and reporting opt-outs, installs the candidate with `adb install -r`, and verifies persistence again after restarting the process. Same-version reinstall is permitted in PR tests so dependency/fix PRs do not require version bumps; production signing/version checks are stricter. Download-manager database compatibility and real production-signed phone installation still require pre-publish testing. Never run the fixture on a personal device or an emulator containing an existing app installation: the runner refuses those cases.

The baseline seeder instrumentation must be built from the baseline source, and the verification instrumentation from the candidate source. Reusing candidate instrumentation against the old APK can fail before upgrade when Kotlin model signatures change.

Example against a fresh disposable emulator, with baseline and candidate app/instrumentation APKs already built using the same test key:

```sh
python3 scripts/upgrade_check.py --adb "$ANDROID_HOME/platform-tools/adb" \
  --serial emulator-5554 --tools "$ANDROID_HOME/build-tools/36.0.0" \
  --previous /tmp/previous.apk --candidate app/build/outputs/apk/debug/app-debug.apk \
  --previous-tests /tmp/previous-tests.apk \
  --tests app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --output /tmp/upgrade-report.json
```

Before publishing the draft, also install the actual signed APK over the previous production APK on a test phone. Verify saved downloads, audio controls, playback on Wi-Fi and mobile data, network switching, interrupted connectivity, notification denial, update dismissal and the browser fallback.

## Monitoring and response

Security checks query OSV using both exact committed runtime dependency inventories, including transitive modules; API errors fail the check. Any returned advisory needs triage. Inventory reproduction in Android/release CI ensures stale inventories fail rather than silently passing. CodeQL analyzes Java/Kotlin with a traced Android build. Reviewed action commit pins and the pin audit prevent mutable action tags from entering workflows; updates still need review.

App health runs on publication, weekly and on manual dispatch. It downloads both public APK filenames, verifies checksums, production identity and matching contents, and checks that notes and required assets exist. Provider checks run bounded public search, stream/partial download and lyrics requests on a disposable emulator. No full recording or real-user library is uploaded. Provider restrictions on hosted runner IPs can fail a synthetic check even when some phones work; reproduce on a phone before claiming a universal outage. Synthetic stream-byte checks are not proof of audible playback, all-provider coverage or mobile carrier availability.

When a check fails:

1. Inspect the failing check/artifact, identify the affected version/category and reproduce with synthetic data. Treat API outages and missing measurements as unknown, not healthy.
2. For a dependency advisory, inspect affected versions and reachability, update the direct/transitive dependency, regenerate both inventories and notices, then rerun app/provider checks. Do not disable the scan or compatibility protections merely to clear a check.
3. For playback/provider failures, compare network/region restrictions, timeouts and provider schema changes. Confirm recovery/fallback behavior and avoid retry storms.
4. For a faulty published app, prepare a corrective release with a higher version code, verify upgrade preservation and production identity, then publish reviewed notes. Android normally rejects downgrades; do not advise users to uninstall and lose their data as a routine rollback.
5. For suspected signing compromise, pause release automation, restrict access and investigate privately under SECURITY.md. Use the documented private key recovery/rotation procedure; do not rotate production signing identities casually.
6. Record the fix, validation limits and closure evidence. Never paste library backups, signed stream URLs, tokens or real-user crash payloads into public issues.

Useful official references: [Android signing](https://developer.android.com/studio/publish/app-signing), [Crashlytics collection controls](https://firebase.google.com/docs/crashlytics/android/customize-crash-reports), [GitHub workflow security](https://docs.github.com/en/actions/reference/security/secure-use), [OSV API](https://google.github.io/osv.dev/api/).
