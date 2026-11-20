# Optional usage and crash reporting

Standard builds exclude Firebase entirely. The official build can include Google Analytics for Firebase to report first launches, sessions, engagement and app-version adoption, when reporting is enabled, plus separately controlled Firebase Crashlytics. This is separate from local listening Insights and local app-health counts. Remote Config and push notifications are not included. The verified APK updater uses GitHub, independently of Firebase.

## Configure and build

1. Register Android package **`com.arohamusic.app`** in your Firebase project and enable/link Google Analytics under Project settings → Integrations.
2. Download `google-services.json` to `app/google-services.json`. This is a client configuration, not an Admin key. It is ignored by Git and excluded from public source exports. Never add service-account credentials to the Android app.
3. Build explicitly with reporting support:

```sh
./gradlew -PfirebaseAnalytics=true :app:assembleDebug
./gradlew -PfirebaseAnalytics=true :app:assemblePreview
./gradlew -PfirebaseAnalytics=true :app:assembleRelease
```

Release remains unsigned; use RELEASING.md for production signing. Without the property, the SDKs and config-processing plugins are omitted even when the file exists. A fresh public-source checkout therefore needs no Firebase file. The build uses Google Services plugin 4.5.0, Crashlytics plugin 3.0.8 and Firebase BoM 34.4.0; versions are pinned, not dynamically resolved.

Enable Crashlytics in the Firebase console for this registered Android app. Settings → App health → Share crash reports defaults to off and persists independently of basic usage statistics. Java fatal exceptions are sanitized before the Crashlytics handler; supported OS ANRs use SDK/platform diagnostics, and the optional foreground watchdog reports long main-thread delays as nonfatal samples. The app adds no media fields, custom user IDs or custom Analytics events. Review the current privacy disclosure and SDK-collected metadata. See OPERATIONS.md for production validation and retention/alert setup.

## Local Crashlytics verification APK

Register `com.arohamusic.app` and download its own client configuration before building. A Firebase file registered for a different package is not valid. Build a debug APK with the matching Firebase client configuration:

```sh
./gradlew -PfirebaseAnalytics=true :app:assembleDebug
```

Use `app/build/outputs/apk/debug/app-debug.apk` on an emulator or test device. This APK uses the debug signing key and the application ID `com.arohamusic.app`; it cannot replace a production-signed installation. Use a separate test device/emulator instead of uninstalling your production app and losing its local data. The prepared copy is `dist/local-testing/Aroha-Music-1.0.0-Crashlytics-debug.apk` when generated for handoff.

1. Complete Add SDK for Android app `com.arohamusic.app`, nickname Aroha Music, in the Firebase console. The SDK/plugin are already configured in this build; no duplicate Gradle changes are needed.
2. Install and open the Firebase-enabled debug APK without an attached debugger, and stop playback.
3. Open Settings → App health and turn on Share crash reports. This choice is required; the debug test never enables collection automatically.
4. Tap Test Crashlytics in the debug-build test section, then confirm Crash app now. The app closes deliberately once; the test does not repeat on launch.
5. Reopen Aroha Music from its launcher icon, keep the device online and refresh Firebase → Crashlytics for the same registered Android app. Allow about five minutes for the first report. The sanitized fatal report retains a source frame in `CrashlyticsVerificationCrash`/`CrashTestControls.kt` and an exception-class label identifying the fixed test, rather than library/song data.
6. Turn Share crash reports off afterward if you do not want further test diagnostics collected.

The test control and intentional-crash class are compiled only from `src/debug`. Release, preview and benchmark builds use a no-op control from `src/nonDebug` and contain no intentional test-crash trigger. SDK-free debug builds hide the control because no reporting backend is available.

If the report is missing, use Android platform tools while repeating the test:

```sh
adb shell setprop log.tag.FirebaseCrashlytics DEBUG
adb logcat -s FirebaseCrashlytics
```

Look for `Crashlytics report upload complete` or a successful `204` upload response. Restore normal logging afterward with `adb shell setprop log.tag.FirebaseCrashlytics INFO`. See [Firebase's test instructions](https://firebase.google.com/docs/crashlytics/android/test-implementation). The local fake-backend UI tests do not verify delivery to the real Firebase project; the intentional crash/relaunch above completes that check.

Local assembly skips mapping uploads. Official release automation adds `-PuploadCrashMappings=true` to upload the R8 mapping for that build, and retains a separate mapping artifact for 90 days. Preserve mappings privately for longer-supported builds. Configure Crashlytics alerts and verify a deliberate crash only on a disposable opted-in test installation before considering production crash visibility verified.

## Reporting controls and reports

Basic usage statistics are enabled by default on new Firebase installations. Existing saved opt-outs are preserved. Users can turn reporting off under **Settings → Basic usage statistics → App usage**. The separate **Listening** tab contains only local listening statistics and history controls. The App usage tab clearly explains both what is shared and what stays private. There is no first-launch consent dialog.

The auto-initialization provider remains removed so the saved preference is applied before manual Firebase initialization. Turning reporting off disables collection and resets local SDK analytics data; it does not remove already-uploaded data from Google.

Firebase uses installation/app-instance identifiers and device/app information. Do not describe the feature as completely anonymous. The app sends no custom events, user ID, name, song/search/library data or listening history. Ad consent, advertising-ID collection/permissions and automatic screen tracking are disabled. Automatic first-open reporting measures first use, not every download, and only reporting users appear.

View the linked Google Analytics property/Firebase Analytics dashboard for active/new users and app versions. Normal reports are delayed. DebugView can check a development build:

```sh
adb shell setprop debug.firebase.analytics.app com.arohamusic.app
```

Launch the Firebase-enabled build with reporting enabled on the test device, and inspect Analytics → DebugView. Debug-mode events are for validation, not production usage estimates. Disable afterward:

```sh
adb shell setprop debug.firebase.analytics.app .none.
```

Review Analytics retention and data-sharing settings and limit project access. Keep the published privacy notice consistent with your actual configuration. No public user database or Firestore rules are needed for this feature.

## Validation and dependency review

```sh
./gradlew -PfirebaseAnalytics=true :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assembleDebugAndroidTest
./gradlew -PfirebaseAnalytics=true -I scripts/runtime_inventory.init.gradle :app:writeRuntimeInventory
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.arohamusic.app.UsageReportingTest
```

The five reporting-preference tests use isolated preferences and a fake backend. Run them against the default SDK-free build so application startup also has no real Firebase backend. They cover default-on behavior, preserved opt-outs, explicit choices, withdrawal and SDK-free behavior. Verify builds in both modes. Public CI uses the default mode without project configuration. Exact Firebase-enabled modules are recorded in LICENSES/runtime-dependencies-firebase.json. Review Google SDK terms and preserve notices before distributing official binaries; the standard app source license does not relicense Google binaries.

References: [Android setup](https://firebase.google.com/docs/android/setup), [collection controls](https://firebase.google.com/docs/analytics/android/configure-data-collection), [automatic events](https://support.google.com/analytics/answer/9234069), [Firebase privacy](https://firebase.google.com/support/privacy).
