# Parking Reporter for Android

[![Android CI](https://github.com/reihlinstinct/parking-reporter-app/actions/workflows/android.yml/badge.svg)](https://github.com/reihlinstinct/parking-reporter-app/actions/workflows/android.yml) [![repository checks](https://github.com/reihlinstinct/parking-reporter-app/actions/workflows/repository-checks.yml/badge.svg)](https://github.com/reihlinstinct/parking-reporter-app/actions/workflows/repository-checks.yml)

Open-source Android development preview for local review of sidewalk-parking evidence. Kotlin and English documentation; Hebrew UI and report text. MIT licensed. Not a production reporting app.

Hebrew user guide: the [wiki](https://github.com/reihlinstinct/parking-reporter-app/wiki) (source: [docs/wiki/](docs/wiki/), PR-reviewed).

## Current capabilities

CameraX original-byte capture, SHA-256, EXIF metadata, manual corrections, optional geocoder suggestions, encrypted reporter settings, local Room ledger and digest-bound manual review. The captured municipal 106 subject catalog ships as a checksummed offline asset with local-category mapping ([taxonomy parity](docs/MUNICIPAL_TAXONOMY.md)). No live report submission, bundled municipal secrets or cloud photo recognition. The offline encrypted-package demo uses generated synthetic data and an in-memory sink, never the network.

## Build and tests

Java 17 and Android SDK 35. The checked-in wrapper properties select Gradle
8.13 and verify its distribution checksum. The preparation script separately
bootstraps a checksum-verified wrapper JAR from Gradle 8.11.1; that does not
select the Gradle runtime version. Accept Android SDK terms before installing
its packages.

```sh
sh scripts/prepare_wrapper.sh
sh gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug
sh gradlew --no-daemon connectedDebugAndroidTest
python3 scripts/check_repository.py
```

CI builds, lints and runs synthetic unit/emulator tests. Check the current run before claiming success. Debug APKs are test artifacts, not signed production releases. Physical camera, device authentication and target-device behavior still need validation.

Every workflow ends with a `Run summary` job that writes a summary to the top of the run page: which jobs ran, their result and duration, what was skipped and why, the commits and files the run changed, and which checks cover those changes (`.github/ci-coverage-map.json` declares the coverage; files matching no mapping are flagged). The script is `scripts/ci/run_summary.py`.

## Milestones

| Stage | Goal |
| --- | --- |
| M0 | Synthetic transport diagnostic, no configured live endpoint |
| M1 | Capture, encrypted settings, ledger and manual review |
| M2 | On-device recognition with uncertainty and manual correction |
| M3 | Vehicle registry checks and mismatch stops |
| M4 | Hebrew text and approval-digest parity |
| M5 | Approved submission, receipt and uncertain-state reconciliation |
| M6 | Optional separate plate-masked social export |

See [design](docs/DESIGN.md), [preview status](M1_STATUS.md), [taxonomy parity](docs/MUNICIPAL_TAXONOMY.md), [security](SECURITY.md), [contributing](CONTRIBUTING.md) and [release checklist](docs/RELEASE_CHECKLIST.md).

## Safety

Never commit real plates, evidence, profiles, keys or tokens. Fixtures are explicitly synthetic. Local review is not a submission or permission to post. Edits invalidate review. Original hash and stable ID guard against duplicates. Uncertain writes must not be retried automatically. Photos remain local for recognition. No municipal credentials or endpoints belong in public code or APKs.

## Offline recognition preview

Explicit local Tesseract OCR offers plate candidates, with manual fallback and required review. Optional bundled Google ML Kit recognition sends API usage/performance metrics to Google; photos and plate results stay on-device. See [OCR limits, model provenance and licensing](docs/OFFLINE_OCR.md). This does not complete field validation or enable live reporting.
