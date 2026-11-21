# Aroha Music first setup

Aroha Music starts at version **1.0.0 (1)** with Android application ID **`com.arohamusic.app`**. The standard app needs no custom backend, database, Firebase file or API key.

## Local installation

Build with JDK 17 and the Android SDK as described in README.md. Install the debug APK on a test phone or emulator. This application has its own storage. Other applications' private libraries, settings, downloads and document grants are not transferred automatically.

Use a stable production signing certificate for APKs distributed to friends. Future updates must retain this certificate, application ID and increasing version code. Debug builds and production builds use different signing identities.

## Fresh local source repository

Use the clean source export or the prepared `dist/Aroha-Music` repository. It contains one initial source commit, the red Vinyl Headset branding and the current app source, with no earlier development history, client configuration, signing keys, APKs or build output. The development checkout is kept separately for recovery. Do not copy a different project's `.git` directory into the new repository.

The canonical GitHub target in the app and scripts is `ArohaMusic/Aroha-Music-App`. No remote repository is created or reset by local preparation. For an existing checkout, fetch the current default branch, push changes on a feature branch and open a pull request; preserve the existing history. Empty-repository checks in the first-publication guide apply only to a new repository. If another repository is selected, update the app's release constants/identity-asset URL, release monitor, README and tests together before distribution. Only releases with a matching `release-identity.json` are offered as updates; another Android application's release at the same address is ignored.

## GitHub Actions and signing

1. Create or choose an empty repository, then push the fresh source's `main` branch. Follow OPEN_SOURCE_GUIDE.md.
2. Configure signing with `bash scripts/setup_release_secrets.sh ArohaMusic/Aroha-Music-App`. The helper uses `AROHA_KEYSTORE_BASE64`, `AROHA_KEYSTORE_PASSWORD`, `AROHA_KEY_ALIAS` and `AROHA_KEY_PASSWORD`, and sets `RELEASE_CERT_SHA256` from the certificate. Create an Aroha signing key or deliberately select a privately backed-up key. Never commit keys or passwords.
3. For the first release only, set Actions variable `ALLOW_FIRST_RELEASE` to `true`. After the first stable release is published, remove it or set it to `false` so later releases require the previous signed baseline.
4. Firebase is optional and disabled for tag builds unless Actions variable `RELEASE_FIREBASE` is `true`. Configure a matching Firebase file before enabling it.
5. Require relevant successful checks on `main`, enable supported secret scanning/push protection and configure Actions failure notifications. Review OPERATIONS.md.
6. After checks pass on `main`, create tag `v1.0.0`. The signing workflow creates a draft release. Test the production APK on a phone, then publish deliberately. No tag or release is created by this local preparation.

## Optional Firebase and Crashlytics

1. In Firebase, create/select the intended project and register Android app **`com.arohamusic.app`**, nickname **Aroha Music**. A configuration registered for a different package cannot be edited into a valid replacement.
2. Download the new `google-services.json` to `app/google-services.json` in your development checkout. It remains ignored and excluded from source exports.
3. Build with `./gradlew -PfirebaseAnalytics=true :app:assembleDebug`. Verify Crashlytics using Settings → App health → Share crash reports and the debug-only Test Crashlytics control. Reopen the app to send the report. Earlier tests of another Firebase Android registration do not verify this registration.
4. Upload the matching configuration through the setup helper as `GOOGLE_SERVICES_JSON_BASE64`. Set `RELEASE_FIREBASE=true` for tag builds only when ready. Configure Firebase fatal/regression alerts.

See FIREBASE.md for full verification and RELEASING.md for signing and release commands.
