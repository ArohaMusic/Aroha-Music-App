# Publication audit

Publish from the freshly prepared `dist/Aroha-Music` repository in the development checkout. Its current source must pass the checks below immediately before the initial push. The original development checkout and local recovery archive are excluded.

## Source and committed objects

```sh
python3 scripts/public_source.py
python3 -m unittest discover -s scripts -p 'test_*.py'
git status --short
python3 scripts/audit_publication.py --fresh --output dist/publication-manifest.json
```

The committed-tree audit extracts `HEAD` from Git, audits that snapshot and requires its file list to exactly match `public-source.json`. It rejects symlinks, submodules, uncommitted source changes and files outside the public allowlist. With `--fresh`, it also requires one initial `main` commit, no additional refs and the reviewed GitHub noreply author/committer identity. CI uses the same committed-tree audit without the initial-history restriction for subsequent development.

`public-assets.json` enumerates every approved binary and its SHA-256/purpose. The retained binaries are five licensed font weights, two final branding PNGs and the Gradle wrapper. New or changed binaries block export until deliberately reviewed. Production artwork uses native vectors; no demo photos, recordings, video, saved libraries or databases are bundled. Editable SVG exports remain in `assets/branding/`; redundant copies and the generated design-reference image are excluded.

The source audit rejects credentials matching its patterns, local home paths, malformed text, unreviewed formats and private/generated inputs. Text containing NUL bytes cannot bypass the scan. The allowlist excludes Git metadata, SDK configuration, Firebase client files, signing material, builds, caches, recordings, screenshots and old exports. Required licenses, real application tests and synthetic test code remain; test code is not seeded user data.

## Independent credential scan

Run a checksum-verified official Gitleaks binary with its default rules against both the source snapshot and all Git objects to be pushed. Keep reports outside tracked source, redact findings and review any result. Do not blanket-ignore tests, change the scanner's rules to hide findings, or upload secrets in audit logs.

For the initial repository, scan `git archive HEAD` extracted into a separate temporary directory before the directory scan, so local build outputs and reports are not part of the publication scan. Scan history from inside the fresh repository:

```sh
gitleaks git . --log-opts=--all --redact --no-banner --ignore-gitleaks-allow --max-archive-depth 2 --max-decode-depth 5 --report-format json --report-path dist/gitleaks-git.json
```

An audit pass is evidence for the checked snapshot, not a guarantee that every possible secret format or security issue is detectable. Rerun the checks if the commit changes.

## Destination and first push

After the owner supplies the intended GitHub URL, verify authentication, repository ownership/access and existing remote refs. Confirm the app's update URLs and release-monitor target match that destination. Connect only the reviewed fresh repository, then push the explicit ref `main:main`; do not use `--mirror`, `--all`, `--tags` or `--force`.

Keep local audit manifests and reports outside the published tree. APKs belong in GitHub Releases after production signing and phone testing. First-release signing and optional Firebase setup are documented in [SETUP.md](SETUP.md).
