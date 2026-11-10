# Private real-photo OCR comparison (#210)

The manual comparison workflow never files reports, reads municipal credentials or
changes live staged inputs. Ordinary CI stages no manifest and skips private work.

- Select a recent, bounded private photo sample with expected plates grounded in the
  private report record and visual check. Record selection and duplicates privately.
- Package originals and a manifest (cases: file `sample-N.jpg`, expected canonical
  digits) in a tar archive. No original paths, plates or photos in this public repo.
- Encrypt locally with a dedicated random OCR_BENCH_KEY. Host only the encrypted
  archive; set dedicated OCR_BENCH_INPUT_URL, OCR_BENCH_INPUT_SHA256 and
  OCR_BENCH_KEY secrets. Never reuse or modify TRIAL_* / J106_* inputs.
- After exact-head green/review, dispatch main with COMPARE-ONLY. Both engines use
  the same production OutgoingEvidence-prepared bitmap, alternating engine order.
  The emulator network is off; models are bundled. No screenshots or video.
- Upload only encrypted results/logs with three-day retention. Decrypt privately,
  verify every original hash and sample count, then compare exact top-1, expected
  anywhere in candidates, empty result, errors and median/mean time. Multiple wrong
  candidates do not count as a clean extraction. Include sample size and limitations.
- Remove temporary input hosting and dedicated secrets after the comparison. Private
  encrypted evidence is not a user-facing deliverable or permission to publish photos.

After measurement, pick the better default and expose the alternative only in
Settings, with the existing telemetry disclosure. No choice in first-run setup and
no OCR result automatically approves or sends a report.

## Encrypted raw-text diagnostics

The manual benchmark records bounded raw OCR text (64,000 characters per engine/sample) alongside candidates in its private JSON output. The existing compare-only script encrypts this file and all collected output before uploading; normal CI has no staged private input and exports no OCR output. Raw text is never printed by the test. Production recognition still calls the exact same PlateCandidates filter. This diagnostic does not join numeric fragments, loosen plate matching, approve a plate or submit a report. Compare empty results against raw text privately before choosing an engine.
