# Changelog

## Unreleased

## 1.3.0

- Remove the optional minimal listening screen and its player entry point.
- Replace the artwork swipe's reset and second transition with a continuous slide into the next or previous cover. Keep the confirmed song centered, resist queue boundaries and respect reduced motion.
- Replace Home's bottom browse chips with larger two-column Explore more cards.
- Give Charts and Podcasts full-height, vertically scrolling collection cards with creator and item counts; give Music videos wide previews with artist details and duration.
- Move the Home greeting directly under the logo in a small, single line and shorten its daily messages.

## 1.2.0

- Add an optional minimal listening view with artwork, song details, seeking and essential playback controls. Enter from the player header and return with Exit minimal or Android Back, preserving the queue and play/pause state.
- Replace the player's lyrics settings dialog with a spacious bottom sheet, live appearance preview, style chips, separate appearance and source/timing tabs, precise timing controls and a reachable Done button.
- Make lyrics settings available directly from the embedded player lyrics view as well as full-screen lyrics.
- Expand Home Speed Dial to up to 24 real suggestions in a horizontally scrolling two-row shelf, combining pinned playlists, recent tracks, favorites and available discovery results.
- Improve artist variety in Speed Dial and preserve its visible song order during playback; manual Home refresh rebuilds the suggestions.

## 1.1.0

- Follow the renamed ArohaMusic GitHub account for verified app updates, release monitoring, setup links and code ownership.
- Add a personalized Home Speed Dial for playable pinned playlists, recent songs and favorites, with discovery results for new listeners.
- Add New releases, Charts, Podcasts and Music videos browse tabs at the bottom of Home.
- Show wide music-video cards on Home, linked artist pages and genre/mood pages; explicit video selections open the player without changing the default playback preference.
- Keep song, album and playlist card labels to one title line and one subtitle line with ellipses, and match their loading placeholders.
- Replace the player's multicolour blurred background with one restrained artwork tone, with a short colour dissolve on entry and song changes; respect reduced motion and retain the last tone while artwork loads.
- Unify typography around the bundled Plus Jakarta Sans family and a shared scale for headings, song titles, artist metadata, buttons, menus and Settings.
- Improve small-text readability, soften heavy headings, match plain and synced lyric fonts, and adapt player and spotlight layouts to Android font scaling.
- Redesign song and Glimpses action menus with a fixed song header, quick actions, grouped controls and clearer quality selection.
- Keep Glimpses playing while action, playlist and quality panels are open; preserve a manually paused preview when opening or closing settings.
- Center lyrics and their loading placeholders by default; format untimed lyrics into readable verses and simplify the provider footer.
- Keep Quick Picks stable during playback and library changes; retain loading placeholders when Home requests are interrupted.
- Match Home category chips to Search, anchor the search field while the keyboard opens, and extend the bottom navigation surface through the gesture area.
- Add a subtle animation of the original Vinyl Headset logo and soften song artwork behind Home and Settings while preserving readable theme surfaces.
- Add a full artist Songs sheet with continuation loading, duplicate filtering and retry support.
- Add bottom clearance to Settings pages so their final controls remain reachable.

## 1.0.0 — Initial Aroha Music release

- Red Vinyl Headset launcher, themed icon, in-app branding and notification symbol, with editable and PNG assets.
- Native Android music discovery and playback with background audio, queues, crossfade and offline downloads.
- Multi-provider lyrics, local library and listening insights, widgets and appearance controls.
- Verified GitHub APK updates, local app-health reports and optional Firebase usage/crash reporting.
- Release discovery checks the Android identity before displaying an update; only the official Aroha release assets are accepted.
- Application ID `com.arohamusic.app`, Aroha application/components/resources and independent on-device storage.

The initial release is prepared locally. Published availability depends on completing the setup and release checklist.
