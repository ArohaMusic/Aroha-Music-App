# Aroha Music

<img src="assets/branding/aroha-music-icon.svg" width="96" alt="Aroha Music icon">

An unofficial YouTube Music client for Android, built with Kotlin, Jetpack Compose and Media3.

Application ID: **`com.arohamusic.app`**. See [first setup](docs/SETUP.md) for installation, optional Firebase and release configuration.

**Android 8.0+ · GPL-3.0-or-later · No account required**

[![Download latest APK](docs/branding/download-apk.svg)](https://github.com/ArohaMusic/Aroha-Music-App/releases/latest/download/Aroha-Music-latest.apk)

[Release notes and all downloads](https://github.com/ArohaMusic/Aroha-Music-App/releases/latest)

## Features

- Search songs, artists, albums and playlists through public YouTube Music discovery.
- Personalized Quick Picks, artist pages and region-aware discovery.
- Horizontally scrollable Home Speed Dial with up to 24 real playlist/song suggestions and stable ordering during playback.
- [Glimpses](docs/GLIMPSES.md): personalized swipeable previews, adaptive video up to the highest supported source quality, collection previews, likes, playlist saves and full-song discovery.
- Background audio, queues, shuffle, repeat, crossfade and a sleep timer.
- Six lyrics providers with learned automatic fallback, real word timing when available, and four cinematic lyric styles.
- Apple Music player/lyrics defaults, 5-second crossfade, and subtle song-based browsing and Settings backgrounds.
- Optional video playback and full-screen controls, including a fixed Apple Music player with an inline queue.
- Lyrics settings include a live preview and separate appearance/source/timing controls.
- Compact Home greetings, larger Explore more tiles and vertically scrolling Charts, Podcasts and Music videos pages.
- Clear update availability and product highlights, neutral Settings controls and an option to hide the Glimpses tab and shortcuts.
- Favorites, playlists, local audio import, backups and supported offline downloads.
- On-device listening history and Library Insights, with recording controls.
- Light, dark and AMOLED appearance, widgets and Android Auto browsing.
- Morning/evening GitHub update checks, update alerts and in-app release notes.
- Verified update downloads with Android installation confirmation, local app-health reports and optional default-off crash reporting in Firebase builds.

Provider availability can change. Playback, lyrics and downloads depend on the source, network and content restrictions. Cloud sync, account login, casting and push notifications are not implemented. Update checks use morning/evening windows; Android may defer background work. New releases appear in an in-app message and dialog, with notifications when allowed. Supported releases download in the app, with integrity/signing checks and Android installation confirmation; browser download remains available.

## Build

Use **JDK 17**, Android SDK Platform **35**, Build Tools **36.0.0**, and Android Studio with AGP 9.0.1 support. The included wrapper runs Gradle 9.8.2.

1. Clone or extract the project and open it in Android Studio.
2. Select JDK 17 for Gradle.
3. Copy `local.properties.example` to `local.properties` and set your SDK path, or let Android Studio create it.
4. Build and check:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

On Windows, use `gradlew.bat`. The APK is `app/build/outputs/apk/debug/app-debug.apk`. Install with Android Studio or:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The first build needs access to Google Maven, Maven Central, the Gradle distribution service and the scoped JitPack repository. No API keys, Firebase files, backend credentials or private repositories are required. Cached dependencies permit later offline builds. Optional official Firebase reporting builds are described in [Firebase setup](docs/FIREBASE.md); standard builds contain no Firebase SDK.

## Contribute

Read [CONTRIBUTING.md](CONTRIBUTING.md), [architecture](docs/ARCHITECTURE.md), [SECURITY.md](SECURITY.md) and the [code of conduct](CODE_OF_CONDUCT.md). Use the GitHub issue templates for bugs and features. Remove personal data and signed stream URLs from reports.

The Android application ID and source namespace are **`com.arohamusic.app`**. Register this exact ID when setting up Firebase. The optional benchmark module is enabled with `-PperformanceTools=true`.

See the [app-wide motion system](docs/UI_MOTION.md) for animation behavior, accessibility controls and verification. Motion levels and a replayable preview are available in Settings → Appearance → Motion.

## Publish on GitHub

Follow the [first-push guide](docs/OPEN_SOURCE_GUIDE.md). Use the clean source export for the first public repository so old development history is excluded:

The audit/export tools require Python 3.10 or newer and use only its standard library.

```sh
python3 scripts/public_source.py
python3 scripts/export_source.py
```

The ZIP is `dist/aroha-music-source.zip`. SDK settings, build outputs, APKs, keys and `.git` are excluded. See [release/signing guidance](docs/RELEASING.md) before distributing production builds. APKs belong in GitHub Releases, not source commits.

See the [preparation and validation record](docs/VALIDATION.md) for completed checks and their scope.

Security, monitoring and update-delivery work is tracked in the [app safety and update backlog](docs/APP_SAFETY_AND_UPDATES.md). See [operations and production setup](docs/OPERATIONS.md) for the implemented safeguards, workflows and remaining account/device checks.

## Privacy and license

Listening Insights and app-health counts stay on the device. Optional Firebase builds enable basic usage reporting by default and offer a separate default-off crash-reporting switch; standard builds contain no Firebase SDK. Music, lyric and artwork providers receive network requests. See [PRIVACY.md](PRIVACY.md).

Original source is **GPL-3.0-or-later**. See [LICENSE](LICENSE), [NOTICE](NOTICE) and [third-party notices](THIRD_PARTY_NOTICES.md). NewPipeExtractor uses GPL-3.0-or-later; the font retains its SIL Open Font License. Bundled legal/privacy documents are available offline in **Settings → About Aroha Music**.

External recordings, lyrics, artwork and provider brands are not licensed by this repository. Aroha Music is independent of Google/YouTube. Use media only with the necessary permission.

Signed APK releases can be prepared automatically from version tags using GitHub Actions. See [one-time release setup and usage](docs/RELEASING.md#automatic-signed-github-releases).
