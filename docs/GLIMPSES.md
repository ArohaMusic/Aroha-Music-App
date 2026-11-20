# Glimpses

Glimpses is Aroha's full-screen discovery feed with compact controls, vertical swipes, native action sheets and animated entry, exit and full-player handoff. The original visual reference is [YouTube Music Samples](https://support.google.com/youtubemusic/answer/13468882?hl=en-GB).

## Personalization without content-type restrictions

There is **no title-based exclusion or official-video priority**. Audio uploads, lyrics, covers, live recordings, visualizers and music videos remain eligible. Collection previews play the recording selected by the listener, without a second search that substitutes a different upload.

Candidate retrieval uses radios from positive listening/preview evidence, preferred artists and periodic regional discovery. On-device ranking uses explicit likes, follows, decayed full-song listening/completions, preview listening/completions/skips, genre affinity, novelty and artist diversity. A small discovery component prevents repeatedly recommending only one artist. Explicit Not interested feedback and recently seen IDs are respected. Taste changes rerank only the upcoming tail; the visible clip stays anchored.

Preview evidence measures actual foreground playback time, not seeks or elapsed time while loading, paused or inside a sheet. Leaving an excerpt after less than five heard seconds can count as a skip; hearing at least 80% can count as a completion. Playback errors do not generate negative feedback. At most 300 preview records with stable metadata stay on-device. Listening analytics controls collection, and Clear history removes the preview evidence. Preview activity never becomes full-song Listening Insights plays. Artist credits are enriched asynchronously after playback starts rather than blocking the first frame.

## Startup and swipes

The ViewModel retains six short-lived resolved-source leases across screen visits. Duplicate lookups share an in-flight request, at most three are retained, and the visible/current-next pair cancels obsolete work. Native player responses have a separate bounded three-entry, 90-second memory lease so changing quality need not perform another native player request.

Up to 80 stable candidate records are cached for a day, ranked again against current local taste at startup. Foreground browsing can prepare the first two stream references before entering the tab. If music is already playing, this warmup waits for buffering to finish and prepares metadata only. Otherwise it also warms bounded header/seek byte ranges. Active preview entry cancels unrelated warmup; no background decoder is retained by the ViewModel.

The screen uses two players for current/successor clips, one audible at a time. A prepared successor reuses its decoder and cached media. The pager has no nested vertical scroll or inter-card gap, and supports a quarter-viewport snap threshold. The outgoing player remains available while a gesture can still reverse. Cached byte ranges share Aroha's existing 256 MB LRU streaming cache, separately from saved downloads, and use stable format keys rather than signed URLs. Cold network requests and unavailable providers can still delay first playback; startup is not guaranteed to be instantaneous.

## Highest available quality

Glimpses now supports **separate video-only and original-audio streams**, merged and clipped together by Media3. The previous combined-file-only path could be limited to 240p/360p even when HD video existed.

Auto selects up to 1080p on an unmetered connection and 720p on a metered connection. Highest available removes that preview ceiling and selects the best source resolution the device decoder supports. Explicit quality above HD also checks a bounded, cached full format catalog beyond the fast native response. Source-dependent choices include 4K, 1440p, 1080p, 720p, 480p, 360p and 240p. The sheet shows actual decoded resolution, not a promise that every upload provides HD. Unsupported device codecs are excluded for playback compatibility, not music taste. Normal full-player video preferences remain independent.

## Memory and transitions

- Per-player compressed buffers target 16 MB, or 8 MB on devices with a small memory class, with a five-second buffer window and no retained back buffer. Native decoder/surface/image allocations are additional to this compressed-data budget.
- A successor above 1080p warms cached data without retaining a second UHD decoder. Only the selected UHD clip enables video decoding.
- Memory-pressure callbacks release the successor and unused source leases. Resuming checks current memory pressure before enabling preloading again. Callback registration is removed on disposal.
- Backgrounding immediately pauses/stops previews and cancels foreground warmup. Player disposal releases both decoders. Visible exit transitions briefly retain the paused final frame before cleanup, avoiding an abrupt blank surface.
- Button press feedback, native sheet enter/exit, collection fade/slide, and shared artwork/title transitions into the full player follow app motion settings. Artist pages use the shared browse transition with the preview frozen during exit.

## Actions and provider limits

Likes, save/create playlist, sharing with excerpt timestamps, full song/video, radio, play next, queue, artist/album browsing and Not interested remain available. Comments and Shorts creation open YouTube, which handles its own authentication and sound permissions. Native unreleased-song alerts remain unavailable through Aroha's public provider. Artwork-only recordings can remain still; they are intentionally not removed by a content-type rule.

## Verification

Coverage includes high-resolution adaptive selection with an original-audio companion, resolution/device limits, bounded response leases, local personalization without title restrictions, signal migration/round-trip, heard-time measurement, repeated swipes, quality changes, animated sheets, service-queue preservation, separate video/audio synchronization and full-player handoff. Live provider checks are separate from deterministic tests and do not establish a universal latency or frame-rate guarantee.

Validated on 19 November 2026: 356 unit tests, five deterministic Android integration tests and one opt-in live adaptive-video test passed. Debug assembly and lint passed. The Android checks exercised twelve successive back-and-forth swipes, a prepared successor, quality changes and separate video/audio playback. The live source rendered at 1080p with its original audio; its cold Highest catalog lookup took 2,797 ms and a repeated catalog lookup took 1 ms. Those figures measure source resolution rather than end-to-end time to the first frame, and depend on provider/network conditions. UHD selection is covered with fixtures; the live test source offered at most 1080p.

The minified, debug-signed Preview variant also built successfully and passed a fresh-install smoke check: opening Glimpses rendered live 1080p video, and switching to Highest available resumed video playback. The installable artifact is `dist/Aroha-Music-Glimpses-Adaptive-preview.apk`.
