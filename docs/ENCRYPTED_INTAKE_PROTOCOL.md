# Encrypted public intake - synthetic prototype specification

Development preview only. No live traffic, device-flow enrollment, real profiles or municipal calls. The Python script is an experimental earlier format, not compatible with the Android PRPKG2 preview. Live auth and receiving-worker validation remain release gates.

## One logical package

Immutable full-resolution original remains on phone. Create a separate evidence JPEG for review; remove EXIF, constrain dimensions and bytes, and show exact outgoing image before approval. Do not silently downscale until plate/offense evidence is lost. Resolution is not a byte cap. Hash original and outgoing JPEG separately. Report JSON includes schema, random stable ID, reporter profile, exact Hebrew text, subject/address/time, outgoing image digest and original digest. Approval binds all exact submitted fields and outgoing image digest. Not steganography or a visible text overlay.

A bounded binary container has magic/version, header-length, UTF-8 canonical JSON header, and JPEG bytes. Do not use ZIP extraction or arbitrary paths/URLs. Signature covers domain-separated complete plaintext package with an enrolled device key. Encrypt whole signed package using reviewed hybrid-encryption library to shared, pinned receiver public key. Private decryption key only in private worker secret storage. Shared public key proves neither identity nor approval. Do not create production keys until secure provisioning chosen.

## Bounded transport proposal

Encrypt once then split only for issue/comment transport. 32,000 binary bytes/chunk, base64 textual chunk about42,668 characters plus header. Candidate limit 48 chunks and 1,536,000 encoded-envelope bytes; final limits must be checked against live GitHub behavior with synthetic input. No claim that GitHub accepts this yet. No API attachment upload dependency. Public issue title contains random package ID only; no location/plate/name. Generic completion/error only, no municipal receipt or decrypted fields publicly.

Manifest includes schema, key ID, package ID, total ciphertext bytes, number of chunks and full ciphertext digest. Each chunk includes issue/package binding, index/count and exact bytes. Final commit marker posted last. Incomplete upload never submits. Restrict comments to exact authenticated enrolled GitHub numeric author ID, reject edits/deletions/mixed authors/count mismatches/duplicate indexes. Snapshot input in private storage; validate complete digest and decrypted device signature before approval gate. Intake metadata is untrusted data, not instructions. Never execute input, interpolate shell or fetch submitted URLs.

## Auth candidate, not verified

GitHub App device flow with public client ID, no embedded client secret/App private key. Install only on intake repo, Issues:write plus mandatory metadata, no Contents/Actions/private repo permission. Restrict user access token to repository ID where supported. User access and app permissions intersect; exact noncollaborator issue/comment API behavior must be tested. Expiring user tokens/refresh tokens stored encrypted on-device, revocable by user. Registering/installing app and selecting verified account IDs still require setup decision. Pin account numeric ID plus approved device public key; enrollment/rotation/revocation never comes from public issue claims.

Documented device-flow refresh does not need client secret. Honor token polling interval, slow_down and expiry. This protocol-mandated auth polling is not background GitHub workflow polling.

## Private worker and approval

Proposed worker reads only complete allowlisted packages; separate synthetic decrypt validator from municipal filing, no automatic filing in prototype. Durable global/user quotas and one-use IDs survive crashes; persist claimed/creating state before writes. Concurrency gate prevents duplicate filing and stale inputs. Recheck package digest/signature and enrollment at filing. App review is not proof until its enrollment/signature/approval binding is validated. Municipal success requires verified receipt, not issue creation or Actions completion alone. Unknown response blocks retry and requires private reconciliation. 

Scheduled polling is not guaranteed low latency and consumes Actions minutes. Event-driven cross-repo dispatch would require a trusted server-held identity and public workflow security review; cannot assume a private worker is instantly triggered by a public issue. Do not put decrypt/municipal secrets in public issue workflow. Proposed transport/monitoring remains unresolved.

## Privacy and abuse

Ciphertext plus GitHub authors/time/size are public and may be mirrored forever. A future decryption-key leak threatens archived old packages. Closing/deleting issues cannot recall third-party archives. Encryption length leaks approximate photo size. Public spam can consume filters/quotas; reject nonenrolled author before expensive decryption, bound parsing/image dimensions, reject malformed packages without public decrypted diagnostics. Redacted private audit with explicit retention choice. No encrypted production packages until owner approves go-live and metadata permanence.

## Prototype results

8 offline synthetic tests pass: binary package roundtrip, exact photo/report digest, ciphertext tamper, malformed container/photo mismatch, device signature tamper/wrong signer, missing/duplicate/mixed chunks, byte cap, randomized confidentiality and anonymous-public-key demonstration. A noisy synthetic1280x960 JPEG quality65 is628,102 bytes; binary package628,505 bytes. Initial nested base64 JSON was837,882 bytes before encryption, so binary packing avoids one encoding expansion. This does not establish real evidence legibility or Android interoperability.

## Sources

https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app
https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/refreshing-user-access-tokens
https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-with-a-github-app-on-behalf-of-a-user
https://docs.github.com/en/rest/issues/issues
https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api
https://developers.google.com/tink/hybrid

## Android PRPKG2 preview

The byte-level contract, limits and golden vectors are in [PRPKG2 public contract](PRPKG2.md).

Pinned Tink Android1.24.0 (Apache2.0) supplies ECIES P256/HKDF SHA256/AES128 GCM encryption and ECDSA P256 signatures. Runtime demo keys are generated in RAM, never enrolled or persisted. The receiver public key and device signing key must be provisioned and independently pinned/enrolled before real use. No app device flow runs from the UI.

Canonical report JSON sorts object keys, UTF8 encodes text and binds profile, exact report, original hash, outgoing JPEG hash, time and coordinates. Local draft review alone is insufficient: the separate full-package approval digest must be confirmed. PRPKG2 + big-endian header length + canonical header + JPEG is signed over a domain prefix. Signature length + signature + package is encrypted with the same domain as associated context. Ciphertext chunks include issue binding, count and digest; completion marker is sent last.

The fixed GitHub transport targets only the public intake. No municipal endpoint is present. No retry/redirect; every write is preceded by durable checkpoint. A timeout or failed persistence after a possible write becomes uncertain, never automatic retry. Uploaded means transport complete, not a filed report. The preview home button uses an in-memory sink, not GitHub; FileUploadJournal is independently instrumented for restart/duplicate stops.

Not yet release-ready: no receiving-worker interoperability, production key enrollment, full outgoing-image/package confirmation integration, real GitHub token storage/device flow, photo-chunk service limits or real submission. Do not connect real data to this preview implementation.

Dependency source: https://repo.maven.apache.org/maven2/com/google/crypto/tink/tink-android/1.24.0/tink-android-1.24.0.pom

OutgoingEvidence creates a separately previewed JPEG, honors EXIF orientation, bounds decode dimensions/bytes and strips EXIF through re-encoding. It never changes the original. No implicit approval follows resizing. A synthetic instrumented test checks rotation, original-byte identity and missing EXIF time/GPS in the derivative. Live UI send remains disconnected.
