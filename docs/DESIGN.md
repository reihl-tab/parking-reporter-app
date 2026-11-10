# Parking Reporter - Android Design

Development preview. Local review is not authorization for network submission.

## Architecture

Kotlin, Jetpack Compose, CameraX and Room. Preserve original photo bytes under noBackupFilesDir and compute SHA-256. Record manual time/location corrections separately from original metadata. Geocoding is opt-in; address results are suggestions, not proof.

Reporter settings contain first name, last name, phone, ID and email. Keystore AES-GCM, recent device credential authentication and AtomicFile protect storage. No plaintext fallback or backup; optional ML Kit sends disclosed API usage/performance metrics to Google. Municipal API credentials are not part of the build: they are entered manually on the settings screen and stored only inside the same encrypted envelope (never logged, never backed up, omitted when blank, masked with password keyboard options). Live submission stays disabled until separately approved. Sensitive production screens use FLAG_SECURE.

A revision-checked Room ledger reserves stable report IDs and original hashes. Manual review binds an exact content digest; editing clears review. This is a local review gate, not a submission receipt. Recovery checks original hashes.

## Planned stages

M0: synthetic transport diagnostics, with no configured production endpoint.
M1: capture, metadata, encrypted settings, local ledger and manual review.
M2: smaller on-device OCR/model, explicit uncertainty and manual correction. No cloud photo recognition.
M3: public vehicle registry validation; mismatches stop review.
M4: Hebrew report text and approval-digest parity with synthetic golden fixtures.
M5: separately approved submission through a secure intake, bounded quotas, durable duplicate guards and receipt reconciliation. No automatic retry after an uncertain write.
M6: optional separate social-export review with every plate masked. Not implemented.

## Network boundary

The public client contains no municipal endpoint or secret, and no private repository identifier. Proposed encrypted intake is described separately. Profile, photo and location transfer requires reviewed exact payloads and a validated enrollment/approval path. No live transport is implemented by this preview.

## Release gates

Camera lifecycle, authenticated Keystore behavior, device storage recovery, schema migrations, EXIF orientation, physical-device UI, model terms and target-device support require validation. Emulator checks use synthetic data only and do not prove physical-device or municipal behavior. minSdk 26 is a build target, not universal-device validation.

## Photo-first review

The home screen asks for a photo; Settings and saved drafts are secondary. Local OCR runs after capture and suggests plate candidates, never approval. The compact review exposes a small Edit action and category dropdown. Automatic category classification is not implemented: a new report has no selected category and cannot be approved until the user chooses one. These are local preview categories, not validated municipal category identifiers.

Broken-sign and faded-crosswalk categories require an empty plate; changing from a vehicle category clears plate and approval. Returning to vehicle parking requires a reviewed plate again. Reporter details remain in Settings. Missing address, time offset, location or description still blocks approval. Geocoding remains opt-in with provider disclosure. Send is visibly disabled; production enrollment and transport remain outside this preview.

## Review address presentation (#204)

The normal review and Edit screen show street and house number, never raw coordinate fields. When a new draft has GPS and lacks an address, a disclosure asks permission before the device Geocoder receives coordinates. Accepting runs the lookup without typing coordinates; declining performs no lookup. The existing Edit lookup button remains available. Results use provider street/house components, not the freeform city/postcode label, and stay suggestions until selected and confirmed. No house number is invented if missing. Lookup failure leaves manual address entry.

Coordinate correction remains under an Advanced location correction disclosure, closed by default when location exists and open when it is missing for missing/wrong metadata. Original Evidence/EXIF, hashes, outgoing photo path and digest algorithms are unchanged. Selecting a suggestion clears approval and adds a confirmation warning; it never approves or submits. The shared synthetic/live FlowDriver dismisses optional provider consent (its fixture already supplies a reviewed address) and explicitly opens Advanced to type its known fixture coordinates. No live input, encrypted blob or workflow dispatch is part of this change.

### First-run basic setup (#209)

Welcome now leads to basic setup before home. The compact language picker uses
ui-prefs without authentication. Only reporter personal fields appear here; no
OCR/model choice, API credentials or diagnostics. Later (including system Back)
finishes onboarding without writing a profile, so capture/import stays available.
Missing municipal credentials still block Send through the existing gate.

Personal fields are transient Compose state, not saved instance state or plaintext
prefs. Save asks for the existing device unlock and uses the same fresh-read merge,
encrypted write and missing-field index/rollback as Settings, preserving untouched
API credentials. Success clears/disposes the form; cancellation or failure stays
on setup. Only the nonsensitive setup stage survives rotation. Pending shared-photo
and tile entry is deferred until setup finishes. No live workflow or evidence changes.
