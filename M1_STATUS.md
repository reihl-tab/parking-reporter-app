# M1 preview status

Development preview, not ready for production. No live submission path.

Implemented: CameraX capture, original hash and metadata reading, manual corrections, opt-in address suggestions, encrypted reporter settings, Room ID/hash reservations, revision-checked draft storage and digest-bound local review.

Local Android compile, lint (zero errors), 17 unit tests and APK packaging have passed. Instrumented tests compile; emulator/device results must be checked in current CI. No claim of physical-camera or authenticated Keystore validation.

Taxonomy parity landed: captured municipal 106 subject catalog (86 subjects, verified live 2026-11-08) ships as a checksummed offline asset, and every local category maps to its municipal subject code. DESIGN M4 Hebrew-text and approval-digest parity remain open.

Open: device OS coverage, authentication, camera lifecycle, schema/migration fixtures, EXIF orientation, visual inspection, M2-M4 parity, encrypted-intake authentication and synthetic full-photo transport. No real municipal calls or production enrollment.

## Offline encrypted preview

Hebrew RTL screens, separated readable evidence date/offset, Hebrew category label. Tink signed encrypted package and bounded issue-comment send state machine added with synthetic roundtrip/tamper/fault tests. A home-screen demo exercises only a local synthetic sink. No device flow, real transport or production keys. Version0.3.0 is still a test preview.
