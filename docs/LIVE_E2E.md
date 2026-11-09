# Live emulator submission E2E (opt-in, recorded)

The manual workflow `.github/workflows/live-emulator-e2e.yml` files **one real
municipal case through the real app UI** in a CI emulator and records the
screen while it happens. It exists so a live submission can be demonstrated
end to end - camera-roll entry, review, local approval, send - not only as an
engine-level JVM test (`m0-engine-trial.yml`, no UI).

This path is strictly opt-in. Nothing here runs on push, on pull requests, or
on a schedule.

## What runs where

- `app/src/androidTest/java/il/sidewalks/reporter/live/LiveEmulatorSubmissionTest.kt`
  is compiled into the regular test APK but **returns immediately unless the
  workflow staged `live-params.json`** into the app's private files directory.
  Regular CI (android.yml) never stages it, so the suite stays green.
- The test drives the same `FlowDriver` and the same `AppShell` as the
  synthetic stub E2E (`FullFlowE2eTest`), with the app's production defaults:
  the pinned municipal endpoint and the real on-device street directory.
- `StubE2eSafetyTest` still enforces that the synthetic E2E has no live mode;
  `LiveE2eSafetyTest` enforces this path's guards (opt-in staging, digest
  binding, no secret output, encrypted evidence, device wipe, duplicate
  guard, ambiguous-outcome handling).

## Opt-in chain (all required)

1. **Manual dispatch** of `live-emulator-e2e.yml` on `main` with
   `confirm = FILE-LIVE-REPORT`.
2. **Environment approval**: the job runs in the `m0-trial-submit`
   environment, whose required reviewer is the owner.
3. **Cross-run duplicate guard**: the run refuses an `approved_sha256` that
   any earlier live run already sent (or may have sent), checked against
   earlier runs' encrypted run records (`live-e2e-run-record-encrypted`
   artifacts, read with `actions: read` and decrypted with
   `TRIAL_DATA_KEY`). An undecryptable record fails the run closed. Runs are
   serialized by the workflow concurrency group. The burn is fail-safe: a
   run record is written unless the positively collected marker log proves
   the test started and the send was never tapped, so a run that failed
   early (emulator boot, staging, a refused gate) burns nothing and the
   same digest can be dispatched again - while a missing or empty log
   (timeout, cancel, dead adb) still burns. The records expire
   with their 14-day artifact retention: a re-dispatch of an older case
   needs the manual 106 check first, exactly like an ambiguous outcome
   (below).
4. **Digest binding**: the dispatch input `approved_sha256` must equal the
   sha256 of the staged `TRIAL_APPROVED_TEXT` secret. The runner checks this
   before staging and the test re-checks it on-device before the flow
   starts. A mismatch aborts with a constant message.
5. **Staged case data**: the run files exactly the case currently staged in
   the `TRIAL_*` secrets (photo in `trial/trial-photo.jpg.enc`, address,
   coordinates, capture time, plate, approved text) with the reporter and
   municipal credentials (`REPORTER_*`, `J106_*`). The category is resolved
   from the same `TRIAL_SUBJECT` code the engine trial uses (default
   `sidewalk_parking`), through the app's own catalog
   (`LiveCategoryResolutionTest`) - never a free-text input, so the UI run
   cannot disagree with the staged subject. Dispatch only for a case the
   owner approved for filing.

## Egress

The municipal API blocks GitHub egress for some calls. The workflow first runs
the engine's login-only probe (no case writes); only when the probe proves a
block (403 / connect failure marker) does it bring up the owner L2TP tunnel
(`trial/l2tp.py`) and re-probe. The tunnel is always torn down afterwards.

## Outcomes and the ambiguous case

- **Submitted**: marker `LIVE_E2E SUBMITTED case=present` (or `unparsed`).
  The municipal case number itself is written only into the encrypted
  bundle (`live-e2e-case-number.txt`), never the public log.
- **Marker log not collected** (timeout of the job, cancellation, dead
  emulator or adb): the verdict step treats a staged run without the
  collected log exactly like an ambiguous outcome - the same do-not-
  re-dispatch message - and the guard burns the digest.
- **Outcome unknown** (a timeout or any other failure after the send tap):
  the request may already
  have reached the city. The test logs `LIVE_E2E OUTCOME_UNKNOWN`, keeps the
  app's own submission-state record for the encrypted bundle instead of
  deleting it, and the run fails with: **do not re-dispatch and do not use
  Re-run jobs; check 106 first.** Only after 106 shows no case was created
  may the same digest be dispatched again (and see the guard's 14-day note
  above).
- **Draft-not-saved** means the send gate refused and nothing reached the
  city; only then does the test retry the tap (up to three attempts, each
  with a 120 s outcome wait).

## Privacy model

- Public run log: only the scrubbed `LIVE_E2E` markers (fixed step names,
  `case=present|unparsed`, exception class names) plus "params staged
  (values withheld)".
- Never in public output: credentials, reporter identity, plate, address,
  report text, the case number, the photo, screenshots, raw instrumentation
  output, logcat.
- The screen recording (up to six ~170-second `screenrecord` segments,
  concatenated), screenshots, raw instrumentation output, the case number
  and a preserved submission state go into a tar bundle encrypted with
  `TRIAL_DATA_KEY` (openssl AES-256-CBC PBKDF2, the same key and rotation
  process as the trial photo) and uploaded as the
  `live-e2e-private-bundle-encrypted` artifact (14-day retention). Artifacts
  on a public repo are downloadable by any signed-in GitHub user, hence the
  encryption. Note: AES-256-CBC gives confidentiality without integrity -
  a tampered bundle is not detected; treat the artifact as the only copy
  and verify it plays.
- A second small artifact, `live-e2e-run-record-encrypted`, holds the run's
  approved digest and outcome for the duplicate guard.
- The device copy of the params, photo, screenshots and recording is removed
  in the same run; runner plaintext is shredded.

Decrypt a bundle locally:

```sh
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -in live-e2e-private-bundle.tgz.enc -out bundle.tgz -pass env:TRIAL_DATA_KEY
tar xzf bundle.tgz   # recording.mp4, instrumentation.txt, screenshots, case number
```

## Running it

1. Stage the approved case in the `TRIAL_*` secrets (rotation workflow covers
   the photo; `TRIAL_SUBJECT` picks the category) and compute `sha256` of
   the exact approved Hebrew text.
2. Actions -> "Live emulator E2E (owner-approved)" -> Run workflow on `main`:
   `confirm = FILE-LIVE-REPORT`, `approved_sha256 = <digest>`.
3. Approve the environment deployment when prompted.
4. The run is green only when the marker log reaches
   `LIVE_E2E SUBMITTED case=present|unparsed`. The case number is in the
   encrypted bundle. **Never use Re-run jobs**: a re-run skips the meaning
   of an ambiguous outcome; dispatch fresh only per the rules above.

## Known limits

- Recording is segmented (screenrecord caps a segment); the workflow keeps up
  to six segments, about 17 minutes. A stalled run keeps evidence up to the
  cut only.
- One submission attempt per dispatch; a transient municipal failure fails
  the run (evidence is still uploaded, encrypted).
- The duplicate guard's memory is the run-record artifacts' 14-day
  retention; older digests need the manual 106 check before re-dispatch.
- Device-unlock is a pass-through in the test, as in the synthetic E2E; on a
  real device the user confirms with the device credential.
