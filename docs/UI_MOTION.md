# UI and motion polish

Implemented in the existing native Compose UI, without new runtime dependencies.

## App-wide motion system

Appearance → Motion exposes **Off**, **Subtle** and **Full**, a finite replayable preview, and a separate Visual effects switch. New installations default to Full. Existing saved motion levels, Reduce motion and effects choices are preserved; select Full to opt an existing installation into the expressive behavior.

`MotionProfile` controls timing, travel, spring damping, touch scaling and bounded entrance staggering. Subtle uses smaller distances and touch responses, shorter fades, and no continuous artwork drift. Off, Reduce motion, Android's disabled animator setting, and a background lifecycle remove custom decorative movement; native Material feedback retains its platform behavior. Changes to Android's animator setting are observed immediately. Playback, seeks, progress and accessible controls remain usable in every mode.

The shared system reaches the whole Compose app:

- **Discover and browse:** coordinated section/card entrances, scroll-driven shelf focus, responsive mood/category/artist cards, image crossfades and loading-to-content reveals.
- **Navigation and details:** directional fade/slide/scale transitions, shared artwork, animated navigation emphasis, and saved scroll positions.
- **Search and library:** responsive result rows, animated insertion/removal/placement, filter and tab entrances, playlist grid/list feedback, and chart bars growing from their baseline.
- **Player and lyrics:** queue artwork transitions for button-driven changes as well as swipes, policy-aware player expansion, spring scrubbing feedback, ambient artwork, transport morphs, favorite feedback, and policy-aware lyric focus/depth. Lyrics retain real provider timing and the existing velocity-preserving scroll.
- **Downloads, settings and overlays:** common touch physics across all eight Material button variants, action rows, expandable audio settings, sheet/dialog entrances, motion previews and download state feedback. Material ripple, enabled states and accessibility semantics are retained.

Finite entrances remember item identity. List delays are capped at 160 ms; shelf transforms read scrolling in the graphics layer, and chart growth does not animate layout size. Continuous artwork drift is restricted to Full with Visual effects enabled. Audio state remains owned by the playback service; animation does not invent beat or lyric timing.

| Step | Change | Behavior |
| --- | --- | --- |
| 1 | Interactive player | Mini-player, expanded player, cover and title use a shared seekable transition. Drag to expand/collapse, reverse to cancel, or use the existing buttons and back gesture. |
| 2 | Artwork swipes | Cover follows horizontal drags and previews available queue neighbors. Song changes still go through the playback service; queue boundaries settle back safely. |
| 3 | Connected browse transitions | Album cards, artist portraits and library playlist covers connect to their detail pages. Remote collections now open in the main screen, sharing the same transition layout. Returning restores the previous screen's scroll position. |
| 4 | Collapsing headers | Album and playlist covers shrink into a toolbar while scrolling. The same bitmap is transformed without a new decode for every size. Artist headers reveal a compact title; navigation and playback remain available. |
| 5 | Playback feedback | Play/pause polygons morph, favorite actions bounce with a brief bloom, and shuffle/repeat selection animates its tint and background. |
| 6 | Queue feedback | Reordering lifts the dragged row above its neighbors; list changes animate at the lazy-item boundary. Swipe removal progressively reveals its action. Undo remains available. |
| 7 | Navigation | Bottom navigation has one sliding selection indicator. Browse/detail transitions use consistent fades and directional movement. |
| 8 | Loading | Shelf and song placeholders match content dimensions. Quick Picks uses its own four-row placeholder, and loaded shelves fade into place. Cached discoveries stay visible during refreshes. |
| 9 | Downloads | Download buttons transition among queued, progress, paused, failed, removing and complete states. Percentages come from the real download manager. Completed download rows move into their sorted position. |
| 10 | Visual hierarchy | Larger detail artwork, readable section subtitles, consistent shelf corners, subtle card presses and fewer boxed library rows. |

## Motion and accessibility

The app Reduce Motion preference, Motion Off setting and Android animation setting control motion. Feedback uses `MotionProfile` and the shared timing vocabulary in `Motion.kt`; progress remains legible when motion is reduced. Existing buttons, touch targets, queue accessibility actions and text descriptions remain available alongside gestures.

## Verification

Validated on 20 November 2026: all **359 JVM tests** and **28 Python tests** passed; debug app, instrumentation and optimized preview APK assembly passed; lint reported **0 errors and 47 warnings**. All five `MotionSystemTest` / `MotionPolishTest` scenarios and the light/dark/AMOLED palette regression passed on an isolated API 36.1 emulator with local media fixtures. The optimized preview APK also passed a cold-launch smoke check. Screenshots, instrument output and APK checksums are under `app/build/reports/ui-motion-system/`. The cached-content warning layout was also corrected so it follows its shelf instead of overlapping the heading.

Validated on 26 October 2026: debug app and instrumentation builds succeeded; all 280 unit tests passed; Android lint passed with zero errors and 35 warnings; all three `MotionPolishTest` scenarios passed on the API 36.1 phone emulator. Light, dark and AMOLED layouts and reduced motion were exercised. Preview screenshots are saved locally in `app/build/reports/ui-motion/`.

Build and static checks:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

`MotionSystemTest` exercises all three saved motion levels, settings recreation, live Android animation changes, Reduce motion, and enabled/disabled action behavior. `MotionProfileTest` covers preference upgrades, accessibility precedence and the difference between Full and Subtle.

`MotionPolishTest` exercises artist-to-album navigation and returning to the previous scroll position, collapsing headers, cancelled/reversed/committed player drags, cover swipes, favorites, shuffle, queue reorder/removal/undo, reduced motion, and download progress/completion/retry. Its transport scenario explicitly selects the YouTube player style, which exposes shuffle and repeat. Audio and artwork fixtures are local; collection data is injected after exercising its real card action so provider availability does not determine the result.

When the Gradle device-test plugin is unavailable in the local dependency cache, install both debug APKs on an emulator and run the same tests directly:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.arohamusic.app.MotionSystemTest,com.arohamusic.app.MotionPolishTest com.arohamusic.app.test/androidx.test.runner.AndroidJUnitRunner
```

Emulator screenshots are useful for layout review. Frame pacing and power usage still require measurement on physical devices before release.
