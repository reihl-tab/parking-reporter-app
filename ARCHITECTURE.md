# Architecture

Development preview. This document gives the high-level system context and data flow. Details and release gates live in [docs/DESIGN.md](docs/DESIGN.md); the encrypted intake proposal lives in [docs/ENCRYPTED_INTAKE_PROTOCOL.md](docs/ENCRYPTED_INTAKE_PROTOCOL.md). Diagrams use synthetic entities only: no real plates, locations or living-person data.

## System context

```mermaid
flowchart TB
    user["Reporter<br/>(device owner)"]
    subgraph device["Android device"]
        ui["Kotlin + Jetpack Compose UI<br/>(photo-first review)"]
        cam["CameraX capture"]
        ocr["On-device OCR<br/>(small local model, optional ML Kit;<br/>no cloud photo analysis)"]
        store[("Room ledger + Keystore AES-GCM<br/>encrypted reporter settings<br/>(noBackupFilesDir)")]
        pack["PRPKG2 packager<br/>(Tink: ECDSA sign, ECIES encrypt)"]
    end
    intake["Encrypted intake<br/>(proposed, not live)"]
    worker["Private receiver worker<br/>(validation only in prototype)"]
    muni["Municipal system<br/>(no endpoint or credentials in client)"]
    user --> ui
    ui --> cam
    cam --> ocr
    cam --> store
    ocr --> store
    ui --> pack
    store --> pack
    pack -. "approved encrypted packages only" .-> intake
    intake -.-> worker
    worker -. "manual, separately approved filing" .-> muni
```

## Data flow (report lifecycle)

```mermaid
flowchart LR
    photo["Photo captured<br/>(original bytes preserved, SHA-256)"]
    meta["Metadata recorded<br/>(manual corrections kept separate<br/>from original metadata)"]
    ocr2["On-device OCR<br/>(plate candidates are suggestions,<br/>never approval)"]
    review["Manual review<br/>(binds exact content digest;<br/>editing clears review)"]
    approved["Approved report<br/>(canonical JSON + outgoing JPEG,<br/>digests bound)"]
    signed["PRPKG2 package<br/>(signed + encrypted)"]
    transport["Transport<br/>(proposed; no live submission<br/>in this preview)"]
    photo --> meta --> ocr2 --> review --> approved --> signed --> transport
```

## Security boundary

- The public client holds no municipal endpoint, credential or secret, and no private repository identifier.
- Reporter settings (first name, last name, phone, ID, email) stay encrypted on-device with Keystore AES-GCM; no plaintext fallback or backup. Municipal API credentials are entered manually, never bundled, and live only inside that same encrypted envelope; live submission remains disabled pending separate approval.
- Approval is local and binds an exact content digest; it is not a submission receipt.
- Anything leaving the device is an encrypted package to a pinned receiver public key; the private decryption key never lives in the app.
- Transport, enrollment and municipal filing are proposed stages (M5) and remain outside this preview.

## Platform roadmap

Android first, then port everything to iOS (owner decision 2026-11-08). This supersedes #45: the earlier plan of one shared UI codebase with simultaneous iPhone and Android development is replaced. The iOS app is a later port of the Android app, re-implementing the same design and screens; cross-platform parity planning lives in issue #149. No iOS work now. Design choices should stay portable: shared feature names/phrases per locale, a shared deep-link contract (open capture/report screen, optionally with a photo attached), and report-flow logic kept separable from the Compose UI.
