# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog and the project uses Semantic Versioning: bug fixes bump PATCH, backward-compatible features bump MINOR, and breaking changes bump MAJOR.

Every release gets a `v<version>` git tag, a matching entry here, and release notes in Hebrew and English. Every bug fix ships with a regression test.

## [Unreleased]

## [0.14.3] - 2026-11-09

### Other changes

- docs/builder: ODbL s4.6 = generator route only (owner 2026-11-09) (#167) (9008bb5)

## [0.14.2] - 2026-11-08

### Other changes

- docs/licenses: match the \u00a74.6 wording to reality, fix ML Kit terms link (#166) (6c41e7b)

## [0.14.1] - 2026-11-08

### Other changes

- ODbL address-lookup builder + Settings Licenses screen (#163) (60c7fa5)

## [0.14.0] - 2026-11-08

### Features

- feat(i18n): language picker and shell/settings string extraction (PR-2a of #115) (#154) (ca4e64a)

## [0.13.2] - 2026-11-08

### Other changes

- M-B: gated municipal submit on the review screen (#153) (#160) (4c1c18a)

## [0.13.1] - 2026-11-08

### Other changes

- M-A: municipal 106 submission client port + contract tests (#155) (a05c50f)

## [0.13.0] - 2026-11-08

### Features

- feat: deep link, share-sheet target and Quick Settings tile (#149 phase 1) (#151) (fb02b17)

## [0.12.2] - 2026-11-08

### Other changes

- data: refresh expected catalog fingerprint after live check 2026-11-08 (subject 1291 whitespace) (#156) (f876ef9)

## [0.12.1] - 2026-11-08

### Other changes

- docs: platform roadmap note (Android first, iOS port later) (#150) (8f6a98b)

## [0.12.0] - 2026-11-08

### Features

- feat: two-button capture, gallery EXIF import, my-reports screen (#138) (d329673)

## [0.11.0] - 2026-11-08

### Features

- feat(i18n): string catalog, plural rules and bidi isolates (infrastructure) (#148) (6af42b9)

## [0.10.0] - 2026-11-08

### Features

- feat: manually entered municipal API credentials in encrypted settings (#128) (54661e5)

## [0.9.7] - 2026-11-08

### Other changes

- ci: add bounded privacy guard (advisory mode) (#146) (c31da31)

## [0.9.6] - 2026-11-08

### Other changes

- test: stubbed offline test for scripts/city_readonly_check.py (#142) (4d691de)

## [0.9.5] - 2026-11-08

### Other changes

- ci: baseline issue/reason fields, wiki-sync persist-credentials false (#137) (#145) (8494d62)

## [0.9.4] - 2026-11-08

### Other changes

- chore: re-pin vulnerability scan to v1.0.9 (#139) (c186839)

## [0.9.3] - 2026-11-08

### Other changes

- build(deps): bump certifi from 2025.11.12 to 2026.10.18 (#123) (6d11b5f)

## [0.9.2] - 2026-11-08

### Other changes

- ci: pipefail + --check on the python smoke gate; checklist pipe/GITHUB_TOKEN/atomicity clarifications (Tab #131 finding) (#135) (fa25fd3)

## [0.9.1] - 2026-11-08

### Other changes

- build(deps): bump filelock from 3.20.0 to 4.2.1 (#122) (5df3bc4)

## [0.9.0] - 2026-11-08

### Features

- feat: municipal taxonomy parity - captured catalog asset, fingerprint loader, category mapping (#101) (d7ec942)

## [0.8.4] - 2026-11-08

### Other changes

- chore: add weekly OSV vulnerability scan via dependency-inventory (#120) (1402f44)

## [0.8.3] - 2026-11-08

### Other changes

- ci: test-evidence artifacts (JUnit+JaCoCo, emulator screen recording, per-test screenshots) (#118) (8d8a39b)

## [0.8.2] - 2026-11-08

### Other changes

- docs: review-lessons checklist in CONTRIBUTING (#131) (602096e)

## [0.8.1] - 2026-11-08

### Other changes

- build: hash-locked Python tooling requirements (#130) (9945c8d)

### Security

- Python tooling requirements are hash-locked: `requirements-lock.txt` pins every package, transitive ones included, with sha256 hashes, generated from the unchanged `requirements.txt` pins. The Scorecard guard now checks the lock (exact pins, hashes, generating command, input pins) and install commands in Markdown code and Python scripts; regeneration in docs/DEPENDENCY-LOCKS.md. The workflow install moves to the lock in a separate maintainer change. (#70)

## [0.8.0] - 2026-11-08

### Features

- feat(ui): bottom navigation with Settings, debug-only demo gate, consistent RTL alignment (#112) (ee8384a)

## [0.7.2] - 2026-11-08

### Other changes

- ci: maintainer-approved read-only city checks (#119) (0468368)

## [0.7.1] - 2026-11-08

### Other changes

- registry: require https baseUrl when an API key is set (#117) (692f433)

### Security

- `VehicleRegistryConfig` now requires an https `baseUrl` when an `apiKey` is set, so the key header is never sent in plaintext; the rejection message carries neither the key nor the URL. A config without a key is unchanged. (#114)

## [0.7.0] - 2026-11-08

### Features

- feat(registry): read-only data.gov.il vehicle lookup client (#113) (6ad6ad5)

### Added

- Read-only data.gov.il vehicle registry lookup client (`registry/VehicleRegistryClient.kt`): plate validation, typed found / not-found / error result, rate limit and in-memory TTL cache, optional API key header. Not wired into the UI yet. Dataset and fields in docs/VEHICLE_REGISTRY.md. (#102)

## [0.6.4] - 2026-11-07

### Other changes

- Sync layout kit: NUL-safe ls-files, tracked-set required checks, no persisted credentials (#95) (3709e2a)

## [0.6.3] - 2026-11-07

### Other changes

- Add repo layout contract and CI enforcement (#93) (9eff2f4)

## [0.6.2] - 2026-11-06

### Other changes

- build(deps): bump com.google.devtools.ksp from 2.3.12 to 2.3.13 (#85) (831afd5)

## [0.6.1] - 2026-11-04

### Other changes

- docs: refine ledger migration proof and crash recovery phases (#91) (c9cfb90)

## [0.6.0] - 2026-11-04

### Features

- feat: add ledger key envelope and Keystore custody (not wired yet) (#90) (9dc0563)

## [0.5.1] - 2026-11-04

### Other changes

- docs: design encrypted report ledger and safe migration (#89) (d5365e8)

## [0.5.0] - 2026-11-04

### Features

- feat: add approved public PayPal Sponsor button (#87) (849ef89)

## [0.4.27] - 2026-11-01

### Other changes

- Toolchain migration: AGP 9.5.0 + Gradle 9.8.2 + compileSdk 37 + okhttp/mockwebserver 5.5.1 (#81) (#82) (49d2e66)

## [0.4.26] - 2026-10-30

### Other changes

- ci: skip heavy jobs on docs-only PRs, add concurrency, caching and weekly full run (#80) (9f15747)

## [0.4.25] - 2026-10-30

### Other changes

- Add regression guard to existing OpenSSF Scorecard workflow (#74) (a6ecef0)

## [0.4.24] - 2026-10-29

### Other changes

- docs: add CI status badge(s) to README (#78) (b6833b7)

## [0.4.23] - 2026-10-29

### Other changes

- Add CODEOWNERS (#76) (d496db9)

## [0.4.22] - 2026-10-29

### Other changes

- Add code of conduct and accessibility statement (#69) (f1afdaf)

## [0.4.21] - 2026-10-29

### Other changes

- Merge pull request #67 from reihlinstinct/ci/run-summary-v12 (4191a32)
- ci: step summary v19 - coverage map checks filtered to nonempty strings (96d61c8)
- ci: step summary v18 - docstring states the self-test stop condition (8ea4bee)
- ci: step summary v17 - edge-case audit: deletion verdict needs after, empty log row, payload shape guards (6af8c6e)
- ci: step summary v16 - accurate no-range reason for deletion pushes (946b92c)
- ci: step summary v15 - push range uses payload after SHA, ignore deletions (6470ed7)
- ci: step summary v14 - recognize quoted job ids (08860cd)
- ci: step summary v13 - job headers with trailing comments, clear current on unknown job keys (d12d639)
- ci: step summary v12 - log-row helper, jobs-block parser fix (0a2dffa)

## [0.4.20] - 2026-10-29

### Other changes

- Merge pull request #65 from reihlinstinct/ci/64-run-summaries (99cb783)
- ci: parse git output as bytes and order name-scalar checks (#64) (c9f59ce)
- ci: keep the forged-row self-test free of literal hosts (#64) (bd9cecf)
- ci: parse plain-scalar YAML comments out of job display names (#64) (1ba47aa)
- ci: close U+2028/U+2029 log-split injection in summaries (#64) (e3c6c05)
- ci: escape HTML in summary text and diff PR base...head (#64) (412fb42)
- ci: harden summary rendering, pin job permissions, fix maps (#64) (f8c0d27)
- ci: align run_summary.py and coverage maps with review findings (#64) (d8212fc)
- chore: update run_summary.py to canonical v3 (60d62a6)
- ci: add a run-summary job to every workflow (#64) (9d0c036)

## [0.4.19] - 2026-10-29

### Other changes

- Pin actions/checkout to full SHA in wiki-sync, fix design doc reference (#62 follow-up) (#63) (25ce9ae)

## [0.4.18] - 2026-10-29

### Other changes

- Wiki pilot: Hebrew user guide in docs/wiki/ + sync workflow (#61) (#62) (95e2404)

## [0.4.17] - 2026-10-28

### Other changes

- ci: pin runs-on to ubuntu-24.04 (#59) (58401d2)

## [0.4.16] - 2026-10-28

### Other changes

- ci: rebase release commit onto latest main before tagging and pushing (#55) (9261068)

## [0.4.15] - 2026-10-28

### Other changes

- docs: distinguish Gradle runtime and wrapper bootstrap versions (#52) (#53) (2bc15e3)

## [0.4.14] - 2026-10-28

### Other changes

- build(deps): bump com.google.crypto.tink:tink-android (#48) (c79b131)

## [0.4.13] - 2026-10-28

### Other changes

- build(deps): bump org.jetbrains.kotlinx:kotlinx-coroutines-android (#47) (4041114)

## [0.4.12] - 2026-10-28

### Other changes

- build(deps): bump org.json:json from 20240303 to 20260814 (#50) (987f1ca)
- build(deps): bump github/codeql-action/upload-sarif (#49) (f47a3db)

## [0.4.11] - 2026-10-22

### Other changes

- docs: label, milestone and issue-order scheme (#43) (d0a67c0)

## [0.4.10] - 2026-10-22

### Other changes

- ci: fix unit-test coverage report (deviceless JacocoReport task) (#42) (6dd2f94)

## [0.4.9] - 2026-10-22

### Other changes

- ci: test-run summaries with per-test detail in every CI job (#41) (f07e5fc)

## [0.4.8] - 2026-10-22

### Other changes

- test: regression check - release.yml must push the tag explicitly (#39) (83b35ed)

## [0.4.7] - 2026-10-22

### Other changes

- build(deps): bump pillow from 12.3.0 to 13.0.0 (#30) (1564de0)

## [0.4.6] - 2026-10-22

### Other changes

- ci: add python tooling smoke test (venv + synthetic intake suite) (#37) (69faeff)

## [0.4.5] - 2026-10-22

### Other changes

- build(deps): bump ossf/scorecard-action from 2.4.4 to 2.5.0 (#31) (21d44c9)

## [0.4.4] - 2026-10-22

### Other changes

- build(deps): bump kotlin to 2.4.21, ksp to 2.3.12, room to 2.8.5 (curated compatible set) (#35) (772e831)

## [0.4.3] - 2026-10-22

### Other changes

- build(deps): bump androidx.fragment:fragment-ktx from 1.8.6 to 1.9.1 (#24) (837a875)

## [0.4.2] - 2026-10-22

### Other changes

- Update release.yml (#34) (9d60ffe)

## [0.4.1] - 2026-10-22

### Other changes

- build(deps): bump github/codeql-action/upload-sarif (#16) (cf45bae)

## [0.4.0] - 2026-10-22

### Added

- Offline OCR development preview: on-device plate recognition candidates with manual review; no cloud photo analysis.
- PRPKG2 encrypted intake prototype (Tink ECDSA/ECIES), synthetic transport demo and offline receiver reference.
- Keystore AES-GCM encrypted reporter settings (first name, last name, phone, ID, email), no plaintext fallback or backup.
- Room ledger with stable report IDs, original hashes and review binding.
- Documentation set: DESIGN, ENCRYPTED_INTAKE_PROTOCOL, OFFLINE_OCR, OFFLINE_RECEIVER, RELAY_OPTIONS, RELEASE_CHECKLIST, ARCHITECTURE with Mermaid diagrams.
- Repository security baseline: Dependabot (gradle, github-actions, pip), CodeQL default setup, OpenSSF Scorecard, secret scanning with push protection, private vulnerability reporting, SHA-pinned workflows.

### Notes

- Development preview only: no live transport, no municipal endpoint or credentials in the client, no production keys.
