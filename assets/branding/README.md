# Aroha Music artwork

The final identity is the red **Vinyl Headset**.

- `aroha-vinyl-headset.svg`: editable canonical shape, white symbol with deep-red record labels on transparency.
- `aroha-music-icon.svg` and `.png`: full-bleed red app icon, suitable for launcher/store artwork and repository use. Android applies its own launcher mask.
- `aroha-music-logo.svg` and `.png`: transparent red silhouette for light backgrounds.

Palette: red `#E61E1E`, white `#FFFFFF`, deep red `#B51220`.

Run `python3 scripts/generate_branding.py` to update the native Android vectors and SVG exports. Use `--png` with CairoSVG 2.8.2 and system Cairo installed to update PNG exports. Review the artwork and update its hashes in `public-assets.json` after an intentional PNG change. See [branding guidance](../../docs/BRANDING.md).

These assets are covered by the repository's LICENSE and NOTICE.
