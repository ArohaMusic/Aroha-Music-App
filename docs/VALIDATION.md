# Aroha Music validation

## 1.3.1 updates, Settings and search — 22 November 2026

- Debug, optimized unsigned release and instrumentation builds pass, together with 381 JVM tests, 48 Python publication/release checks and debug lint with no errors. The new tests cover successful-check reuse, server backoff/restart, update-state presentation, release-note filtering and the backward-compatible Glimpses preference.
- Four targeted disposable-emulator checks cover available/current/checking/offline update states, download/cancel/install UI callbacks, 160% text with reachable actions, Glimpses hide/restore with four-tab navigation, missing/failed artist portraits, clipped artwork in every search category, player layering and a white played timeline. Artist/album navigation and collapsing headers are also checked.
- UI checks use generated local media and deterministic metadata. Update action callbacks are exercised without downloading or installing an arbitrary fixture release. Production APK signing and an in-place upgrade must still be checked before publication; this does not establish physical-phone or live-provider behavior.
- The public-source allowlist contains 455 files. Test screenshots, generated media, build outputs, logs and signing inputs remain outside public source. There are no new dependencies or permissions; existing installations keep Glimpses visible until the listener changes the new preference.
- The upgrade runner builds each instrumentation APK against its corresponding app revision, seeds with the baseline fixture, and installs the candidate fixture only after upgrading. This avoids cross-version Kotlin linkage failures before the preservation test starts.
- The corrected seeded-data upgrade from 1.3.0 passes on the local disposable emulator, including the second verification after process restart and the default visibility of Glimpses for an existing installation.

## 1.3.0 player and Home polish — 22 November 2026

- SDK-free debug, optimized unsigned release and instrumentation builds pass, together with 375 JVM tests, 48 release/publication Python tests and debug lint (0 errors; 55 existing warnings).
- Disposable-emulator checks cover the removed minimal-mode entry, lyrics settings without pausing audio, horizontal Speed Dial selection, the reachable Done action at 160% text size, the compact greeting under the logo, and vertical scrolling through all fixture Charts, Podcasts and Videos results. Collection metadata and explicit video playback actions are checked.
- Artwork checks cover forward/backward handoffs, one callback per swipe, the first-song boundary and the confirmed cover returning to its centered bounds. The integrated player test also exercises queue gestures and reduced-motion navigation.
- The public-source allowlist contains 452 files. Generated media, UI screenshots, logs, signing inputs and build outputs are excluded. No dependencies, permissions, storage schema or reporting defaults changed.
- UI verification uses generated local media and deterministic browse/lyrics fixtures. It does not establish physical-phone behavior or live-provider availability. Verify production signing, version and upgrade installation against the workflow APK before publishing.

## 1.2.0 listening experience — 21 November 2026

- SDK-free debug and optimized unsigned release builds pass, together with 375 JVM tests, 46 release/publication Python tests and debug lint (0 errors; 55 warnings outside the changed UI files).
- Targeted disposable-emulator checks cover minimal-mode playback/pause preservation and Back navigation, embedded lyrics settings, timing persistence without pausing audio, horizontal Speed Dial selection beyond the initial viewport, and the fixed Done action at 160% text size.
- The source audit covers 450 allowlisted files. Screenshots, generated test audio/artwork, build outputs and test logs remain outside public source. No dependencies, permissions or reporting defaults were changed.
- These UI checks use generated local media and a local lyrics fixture. They do not claim physical-phone testing or live-provider availability. Production identity and an upgrade installation must also be verified against the workflow's signed APK before publication.

## Original clean publication record

Clean publication snapshot checked on **20 November 2026**: **1.0.0 (1)**, application ID **`com.arohamusic.app`**. This snapshot uses the current working source after the latest UI removals.

- SDK-free debug, unsigned optimized release and instrumentation APK builds pass. All **359 JVM tests**, **46 Python tests** and **4 targeted emulator checks** pass. Debug lint reports **0 errors and 47 existing warnings**, with no unused-resource findings. Actionlint, workflow action pinning and shell syntax checks pass.
- The device checks cover Aroha identity, widget layout/controls, light/dark/AMOLED contrast and custom-accent contrast on a disposable fresh emulator. Physical-phone playback, production signing and optional Firebase delivery remain release checks.
- Source and APK inspection finds no bundled demo photos, recordings, video, saved libraries or databases. The only raster images in public source are the two final Vinyl Headset PNG exports. Redundant bundled/documentation copies and the generated design-reference image were removed; Android uses the native vectors.
- The reviewed binary inventory contains **8 files**: five licensed font weights, two final branding PNGs and the Gradle wrapper. Their SHA-256 values and purposes are pinned in `public-assets.json`. The wrapper matches Gradle's official published checksum. The PNG exports have no metadata fields.
- **430 public files** pass the source allowlist and credential/private-file checks. A separate case-insensitive scan finds no previous application name/package or discarded note-logo references. Gitleaks **8.30.1**, obtained from its official release with its archive checksum verified, reports no source credentials. Before pushing, the exact committed snapshot and all Git objects must pass the checks in [PUBLICATION_AUDIT.md](PUBLICATION_AUDIT.md).
- The optimized release excludes the intentional debug test-crash marker and Firebase SDK. Standard builds need no backend or Firebase file. Firebase builds require a configuration registered for this application ID; optional Crashlytics delivery has not been verified for this registration.
- The rebuilt publication repository is `dist/Aroha-Music`, with one initial `main` commit and a GitHub noreply author identity. It excludes prior Git history, local configuration, signing keys, builds, caches and obsolete exports. The original development history and recoverable removed local artifacts stay outside the publication snapshot.
- The matching source ZIP is `dist/aroha-music-source.zip`. A debug-signed local test APK is in `dist/local-testing/`; it is not a production signing identity. Local audit manifests and evidence are under ignored `dist/prepublish-audit/`, outside the published source.
- The publication target is `https://github.com/ArohaMusic/Aroha-Music-App`, verified through GitHub's repository redirect and current owner/admin access after the account rename. App update URLs, trusted release paths, monitoring and setup instructions use this repository. The migration regressions reject releases from the previous repository and owner path. No version tag or production release is created by source publication. See [first-push instructions](OPEN_SOURCE_GUIDE.md) and [setup](SETUP.md).
