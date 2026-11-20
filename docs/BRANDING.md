# Aroha Music branding

Final spelling: **Aroha Music**. Application ID: `com.arohamusic.app`. The approved **Vinyl Headset** combines a headphone arch with two record-shaped earcups and groove cutouts. It was redrawn from the selected AI-generated concept as editable vectors.

- Red background: `#E61E1E`; white foreground: `#FFFFFF`; record labels: `#B51220`.
- [Editable icon](../assets/branding/aroha-music-icon.svg), [PNG icon](../assets/branding/aroha-music-icon.png) and [transparent red logo](../assets/branding/aroha-music-logo.svg).
- The canonical shape is `assets/branding/aroha-vinyl-headset.svg`. Final distributable SVG/PNG assets live in `assets/branding/`. Android uses the native drawable vectors; duplicate bundled raster/SVG copies and discarded concept images are excluded.
- Android adaptive launcher and themed-icon layers use `brand_background.xml`, `brand_foreground.xml` and `brand_monochrome.xml`. The foreground has padding for circular and other launcher masks.
- The in-app `brand_mark.xml` and notification `ic_aroha.xml` use the same silhouette with transparent cutouts. Playback, downloads and app-update notifications share this symbol.
- Widget artwork placeholders use `brand_icon.xml` until the current song's cover is available.
- The default Aroha theme uses the red seed, adjusted for readable light/dark surfaces. Custom accents and wallpaper colours remain available.

Regenerate native vectors and SVG exports with `python3 scripts/generate_branding.py`. To regenerate PNGs too, install `CairoSVG==2.8.2` in an isolated development environment and run `python3 scripts/generate_branding.py --png`. CairoSVG also requires its system Cairo library. It is an artwork tool only; normal app builds use the committed exports without it.

Project assets are covered by NOTICE and LICENSE. External provider artwork and brands are not included.
