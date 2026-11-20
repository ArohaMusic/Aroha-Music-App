# Aroha Music privacy

This document describes Aroha Music version 1.0.0, Android application ID `com.arohamusic.app`. Update this document and the bundled copy when behavior changes.

## On-device data

Aroha Music stores favorites, playlists, track metadata, queue position, settings, optional profile name, searches, listening history and statistics in app-private storage. Listening Insights are local aggregates, not remote telemetry. Listening analytics and recently played history can be disabled separately in Settings; history can be cleared there.

Quick Picks ranks music locally using likes, recent listening, completed plays (at least 80% actually heard), early skips (under 30 seconds heard) and Not interested feedback. Seeking to the end does not count as completion, and playback failures do not count as skips. Disabling Listening analytics stops recording listening/completion/skip aggregates. Explicit likes and Not interested remain usable separately. These feedback records are not uploaded to Firebase. Fetching recommendations still makes normal provider requests for selected public song IDs or artist searches, as described below.

Artwork, lyrics, discovery metadata and stream data may be cached. Offline downloads stay on the device until removed or the app is uninstalled. Local audio import uses Android's document picker and URI grants. Android automatic app backup is disabled.

Glimpses also learns locally from actual preview listening time, completed excerpts and early swipes, alongside likes, followed artists, full-song listening and Not interested feedback. Up to 300 preview records with stable track metadata stay on the device. Pausing, buffering and opening action sheets do not add listening time; preview activity does not count as full-song plays in Listening Insights. Turning off Listening analytics stops collecting these preview records. Clear history removes them. These records are not uploaded to Firebase or any recommendation backend.

## External requests

Searches, public video/channel/playlist IDs, track metadata and playback requests go to YouTube/YouTube Music and related delivery servers. Lyric requests can send title/artist and, where needed, album/duration to LRCLIB (`lrclib.net`), Better Lyrics (`api.betterlyrics.org`), KuGou (`mobileservice.kugou.com` and `lyrics.kugou.com`) or lyrics.ovh (`api.lyrics.ovh`); the YouTube Music lyrics source receives the public song ID. AMLL (`api.amll.dev`) receives the song title for searching and a public lyric identifier when retrieving a matched result. KuGou also receives its public recording hash and lyric identifiers when fetching a matched result. Automatic is the default lyric selection mode. It remembers successful sources per recording and bounded source success counts locally for up to seven days; this selection history stays on the device. Settings let you choose a specific source and disable fallback. With fallback enabled, Word by word mode can request other sources even when readable lyrics are already available, to find real word timestamps. At most two lyric sources are checked simultaneously. Glimpses can prepare upcoming public media URLs and bounded cached byte ranges while the app is in the foreground to reduce startup delay. Signed playback URLs remain in memory, while cached media bytes use stable format keys and share the existing evicted streaming cache.

Artwork loads from the host returned by the provider; current and upcoming covers may be prefetched and cached on the device. Browsing and Settings backgrounds reuse current-song artwork locally.

These services receive normal network information such as an IP address and request headers and apply their own policies. Aroha Music does not control their logging. Never post signed playback URLs in public reports.

Country selection can use network/SIM/device country or a manual setting; GPS/location permission is not requested. Voice search launches an installed speech-recognition activity, whose provider may process audio under its own terms.

## App update checks

The app requests the latest published stable release from GitHub in morning and evening windows (around 08:00 and 19:00 local time), with a catch-up check when the app opens if that window has not been checked. Settings → Check for updates requests a manual check. Android controls background timing; no exact alarm or new runtime permission prompt is used. A persisted system job survives restarts using the boot-completed permission.

Requests go to `api.github.com/repos/lxexe/Aroha-Music-App/releases/latest` without a GitHub account, token or installation identifier. GitHub receives normal network information such as your IP address and a generic update-checker user agent. Music, profile, library and listening information are not included. The app also retrieves the release's bounded `release-identity.json` asset from the same official repository to verify that its Android application ID matches before offering the update. This request is independent of the Firebase reporting switch. The last check time, cached release notes, cache identifier, dismissed version and last notified version stay in app-private storage. Notes are displayed as text and do not load remote images or run HTML/scripts.

An available update is shown in an in-app message and modal. The scheduled check can also post a local notification once per new version, if app notifications are allowed. Settings → Update notifications lets you enable or manage these alerts. No push notification service is involved. Tapping the notification opens release notes inside the app. Clock/timezone changes reschedule the morning/evening jobs; exact execution time remains subject to Android restrictions.

Selecting Update app downloads the official APK from GitHub and its approved asset hosts into a narrowly shared app-private cache, when GitHub provides a SHA-256 asset digest. The app checks that digest, application ID, version and installed signing identity before offering installation. Progress, cancellation, retry and a browser download alternative are available. Releases without a digest use the browser flow. Selecting Install update can open Android's installation-access settings, then the Android installer; installation always requires your action. Partial downloads are removed, cached APKs are cleared on process restart and the pending version code stays locally until the next restart checks whether installation succeeded. The app does not silently download or install updates.

## Accounts, analytics and sharing

The standard open-source build contains no Firebase SDK. Official builds can optionally include Google Analytics for Firebase. In those builds basic usage reporting is enabled by default on new installations. An existing saved opt-out is preserved across updates. Settings → Basic usage statistics → App usage → Share basic usage statistics turns reporting off or on independently of local Listening analytics. Firebase initialization follows this saved setting; the SDK auto-initialization provider remains removed.

When enabled, Firebase's automatic events measure first launches, sessions, engagement, app updates and app-version adoption. The SDK also processes an app-instance/installation identifier and standard app/device information, including OS/model, language and approximate geographic information derived by the service. Counts describe reporting app instances, not verified individual people or every APK download; reinstalls and multiple devices can affect counts. This is pseudonymous reporting, not identifier-free anonymous collection.

Aroha Music sends no custom analytics events, user ID, profile name, songs, artist names, searches, playlists, library contents, listening records, stream URLs or audio. Advertising-ID permissions/collection, ad storage, ad personalization and automatic screen tracking are disabled. The Firebase build includes Google's Analytics measurement SDK under Google's terms. See https://firebase.google.com/support/privacy and https://policies.google.com/privacy.

Turning reporting off disables further collection and calls the SDK's local analytics reset. This does not delete data already processed by Google. Clearing app storage/uninstalling removes the local preference and app data; an existing server record can remain under the Analytics project's retention settings. The maintainer controls that project's access, retention and data-sharing settings.

No account is required; Aroha Music does not import a signed-in YouTube feed. Cloud sync and push notifications are not implemented. GitHub update checks and user-initiated downloads are described above; silent installation is not implemented.

## App health and optional crash reporting

Settings → App health displays local per-version counts of successful/failed update checks, update downloads, search requests, stream resolution, lyric requests, playback starts/errors and completed/failed downloads, plus aggregate request timing. These fixed-category counts contain no media IDs, searches, names, URLs or library contents. They remain on the device, reset after a version-code change and can be cleared. Save health report writes only these counters, the app version and Android version to a document location you choose; sharing that document is your decision. These counts are separate from Listening analytics and Firebase usage reporting, and are not automatically uploaded.

Optional Firebase builds also include Firebase Crashlytics. Share crash reports is a separate control in Settings → App health, off by default. When enabled, Crashlytics processes crashes, supported Android application-not-responding diagnostics, app/device/session metadata and an installation identifier under Firebase's policies. Before forwarding Java fatal exceptions, the app replaces exception messages, causes and suppressed exceptions with an exception-class label and a bounded source stack. It sets no custom user ID and adds no song, search, library, signed-URL or audio fields to reports. Firebase's own app/device/session collection remains subject to its policies; this is not identifier-free anonymous reporting.

A foreground responsiveness watchdog can send a sanitized main-thread stack sample after a delay longer than ten seconds when reporting is enabled and no debugger is connected. These are nonfatal delay samples, not Android-confirmed ANRs. Firebase may also associate automatic Analytics event breadcrumbs when basic usage reporting is enabled; the app adds no custom Analytics events or media breadcrumbs. Crash reporting and usage reporting have independent controls.

Turning crash reporting off disables future collection and requests deletion of pending local reports. It does not remove reports already processed by Firebase. Reports and project access/retention are controlled by the maintainer's Firebase configuration. The SDK-free build contains neither Firebase Analytics nor Crashlytics. See https://firebase.google.com/support/privacy and https://firebase.google.com/docs/crashlytics/android/customize-crash-reports.

Backup exports user-selected JSON with portable favorites, playlists and saved track metadata. It excludes local document grants, audio files and listening history. Share cards/links leave through the Android sharing flow selected by the user.

## Permissions

- Internet/network state: discovery, artwork, lyrics, playback and downloads.
- Foreground media playback, wake lock and notifications: background listening and controls.
- Foreground data sync: user-requested downloads.
- Modify audio settings: equalizer/audio effects.
- Request package installation: user-selected app updates through Android's installer; access is requested when you choose Install update.

The manifest does not request contacts, GPS location, broad media-storage access or direct microphone-recording permission.

## Deletion and questions

Use the app's clear/removal controls, clear app storage in Android Settings, or uninstall. Manually exported backups and shared files remain where you saved/sent them. Ask app-specific privacy questions in the public issue tracker without personal data. Security concerns should follow SECURITY.md.
