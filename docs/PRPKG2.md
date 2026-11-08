# PRPKG2 public contract

Contract document version: 1 (draft). It describes the PRPKG2 wire format exactly as implemented on `main` at `51d6d88` (release v0.9.0). It does not change the format. If the code and this page disagree, the code wins and this page is the bug.

Status: development preview. Everything here is synthetic. No live transport, receiver deployment or municipal filing exists (see [encrypted intake](ENCRYPTED_INTAKE_PROTOCOL.md) and [offline receiver](OFFLINE_RECEIVER.md)).

Sources, all on `main`:

- Sender: `app/src/main/java/il/sidewalks/reporter/intake/EncryptedPackage.kt` (`EncryptedPackage`, `SealedUpload`).
- Receiver reference: `app/src/main/java/il/sidewalks/reporter/intake/OfflineReceiver.kt` (`OfflineReceiver.open`).
- Review digest: `app/src/main/java/il/sidewalks/reporter/core/ReviewDraft.kt`.
- Tests: `app/src/test/java/il/sidewalks/reporter/core/EncryptedPackageTest.kt` and `OfflineReceiverTest.kt`.

## Version markers

There are three separate numbers. None of them is a semver.

| Marker | Value | Where |
|---|---|---|
| Container magic | ASCII `PRPKG2` (6 bytes) | `EncryptedPackage.kt:55`, checked at `OfflineReceiver.kt:84-85` |
| Report `schema` | `1` | `EncryptedPackage.kt:36`, checked at `OfflineReceiver.kt:93` |
| Transport `schema` (manifest, chunk, completion) | `2` | `EncryptedPackage.kt:77,82,85`, checked at `OfflineReceiver.kt:18` |
| Crypto context string | `parking-intake-tink-v1` | `EncryptedPackage.kt:18` |

## Canonical JSON

Every JSON text in PRPKG2 (report, header, manifest, chunk, completion) is canonical JSON, produced by `EncryptedPackage.canonical` (`EncryptedPackage.kt:21-30`):

- Object keys sorted with Kotlin `sorted()` on `String` (UTF-16 code-unit order; all current keys are ASCII). No whitespace. `,` between members, `:` after keys.
- Values allowed: object, string, boolean, number, `null`. Arrays are not allowed (`error("Unsupported canonical value")`, line 28).
- Strings and keys go through `org.json` `JSONObject.quote`. With the version the unit tests use (`org.json:json:20260814`, `app/build.gradle.kts:70`), that escapes `"` and `\`, `/` only right after `<`, `\b \t \n \f \r`, and as lowercase `\uXXXX`: U+0000-U+001F, U+0080-U+009F and U+2000-U+20FF. Everything else (Hebrew included) is raw UTF-8.
- Numbers and booleans are the JVM `toString()` (line 26): `7`, `31.0`, `0.1`, `1.0E-7`. A port in another language must reproduce Java `Double.toString`, not its own float format.
- The receiver accepts a text only if re-canonicalising the parsed value gives the same string (`OfflineReceiver.kt:19-23`). That rejects whitespace, duplicate keys and any other encoding.

## Report (schema 1)

Built by `EncryptedPackage.reviewedReport` (`EncryptedPackage.kt:31-43`). The receiver requires exactly these 13 keys (`OfflineReceiver.kt:92`) and these value rules (`OfflineReceiver.kt:24-40`):

| Key | Type and rule |
|---|---|
| `schema` | number, `1` |
| `id` | string, 1-4000 chars, and it must equal the manifest `package` (so in practice `[a-zA-Z0-9_-]{1,80}`) |
| `original_sha256` | 64 lowercase hex: SHA-256 of the untouched original photo |
| `outgoing_sha256` | 64 lowercase hex: SHA-256 of the JPEG bytes in this package (checked, line 98) |
| `review_digest` | 64 lowercase hex: `ReviewDraft.digest()` (below) |
| `plate` | string, at most 8 chars; 7-8 digits when the category needs a plate, otherwise empty (`ReviewDraft.kt:33`) |
| `address`, `subject`, `exact_text`, `time` | string, 1-4000 chars; `subject` must be a known category code (`ReportCategory`), `time` must parse as an ISO-8601 offset date-time |
| `latitude`, `longitude` | number, finite, in range |
| `reporter` | object with exactly `first`, `last`, `phone`, `id`, `email`; each a non-blank string of at most 256 chars |

The review digest (`ReviewDraft.kt:37-45`) is SHA-256 (lowercase hex) over the concatenation, for each field in this order, of `<UTF-8 byte length>:<value>`: evidence id, original SHA-256, evidence capture time (or empty), evidence latitude, evidence longitude, plate, address, subject, exact text, confirmed time (or empty), confirmed latitude, confirmed longitude, then the sorted unresolved items. Doubles use Kotlin `toString()` (`31.0`); a null coordinate is the text `null`. The code calls this a local review digest only (line 38).

The approval digest is SHA-256 (lowercase hex) of the canonical report's UTF-8 bytes (`EncryptedPackage.kt:45`). `seal` refuses any other value (line 49), and the receiver recomputes it (`OfflineReceiver.kt:94`).

## Container layout

All integers are 4-byte big-endian signed (`DataOutputStream.writeInt`, `ByteBuffer.getInt`).

```text
header    = canonical({"approval_digest": <approval digest>, "report": <report>})   UTF-8, 1..16000 bytes
plain     = "PRPKG2" | int32 len(header) | header | jpeg
signature = Sign(device key, "parking-intake-tink-v1" | plain)
signed    = int32 len(signature) | signature | plain
cipher    = HybridEncrypt(receiver public key, signed, context = "parking-intake-tink-v1")
```

Sources: `EncryptedPackage.kt:51-63`; parsing at `OfflineReceiver.kt:75-100`.

- The header has exactly the keys `report` and `approval_digest` (`OfflineReceiver.kt:90`) and must be valid UTF-8 (line 88).
- The JPEG is the rest of `plain`: 4 to 1,100,000 bytes, starting `FF D8` and ending `FF D9` (`EncryptedPackage.kt:33-34`, `OfflineReceiver.kt:96-97`). Nothing else is checked about the image.
- Signature: Tink `ECDSA_P256` (`SignatureKeyTemplates.ECDSA_P256` in the tests). The receiver accepts a signature length of 1-256 (`OfflineReceiver.kt:79`). Measured with the test template: 75-77 bytes, which is Tink's 5-byte key prefix (first byte `0x01`) plus a 70-72 byte DER signature.
- Encryption: Tink `ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM` with the context string as `contextInfo`. Measured overhead: 98 bytes over `signed` (consistent with Tink's 5-byte prefix, a 65-byte uncompressed P-256 point, a 12-byte nonce and a 16-byte tag; that breakdown is inferred, not read from Tink source).
- A non-Tink implementation must handle the Tink output prefixes. Keys are generated per test in RAM; `main` has no constant test keys.
- Size caps: ciphertext at most 1,536,000 bytes (`EncryptedPackage.kt:15,64`); `signed` 16..1,536,000 bytes on receipt (`OfflineReceiver.kt:77`).

## Transport pages (schema 2)

The ciphertext is split into pages of 32,000 bytes (`EncryptedPackage.kt:16`); only the last page may be shorter. `count = ceil(bytes / 32000)`, 1 to 48. Each message is canonical JSON with exactly these keys:

| Message | Keys | Source |
|---|---|---|
| Manifest (issue body) | `bytes`, `count`, `device`, `key`, `package`, `schema`, `sha256` | `EncryptedPackage.kt:77-78`, `OfflineReceiver.kt:47-54` |
| Chunk (one comment per page) | `count`, `data`, `index`, `issue`, `package`, `schema`, `sha256` | `EncryptedPackage.kt:79-84`, `OfflineReceiver.kt:60-71` |
| Completion (last comment) | `complete`, `count`, `issue`, `package`, `schema`, `sha256` | `EncryptedPackage.kt:85-86`, `OfflineReceiver.kt:55-58` |

Rules the receiver enforces:

- `sha256` is the SHA-256 of the whole ciphertext. It is the same in all three messages and is checked again after reassembly (`OfflineReceiver.kt:74`).
- `package`, `key` and `device` match `[a-zA-Z0-9_-]{1,80}`. `key` and `device` must equal the receiver key ID and the enrolled device key ID the caller expects (lines 46, 50). Labels in the manifest prove nothing about enrolment.
- `issue` must equal the issue number the caller expects, in every chunk and in the completion.
- `index` is 0-based. Each index appears exactly once, in any order (the round-trip test sends them reversed). The number of chunks must equal `count`.
- `data` is standard Base64 with padding, at most 42,668 chars, and decodes to exactly 32,000 bytes (or the remainder on the last page).
- Text limits: manifest and completion at most 2,000 chars, each chunk at most 48,000 chars (`OfflineReceiver.kt:45,61`; the GitHub port has the same 48,000 cap, `IntakeTransport.kt:74`).
- `complete` must be `true`. The sender posts it only after every chunk (`IntakeTransport.kt:36-42`).

## Golden vectors

All inputs are the synthetic fixture in `EncryptedPackageTest.kt:17-20`: evidence `synthetic-id`, original hash `"a" x 64`, time `2026-01-01T12:00:00Z`, position `31.0`/`35.0`, plate `1234567`, address `רחוב בדיקה 1`, subject `sidewalk_parking`, text `דיווח בדיקה`, no unresolved items; reporter `synthetic-first`, `synthetic-last`, `0000000`, `000000000`, `synthetic@example.invalid`; photo = `FF D8`, 68,000 bytes of `0x04`, `FF D9`.

| Value | Expected |
|---|---|
| photo length / `outgoing_sha256` | 68,004 / `b20448614e74a31a87213a9b4894f5cdf20280457f20e08ad865557959e9c64d` |
| `review_digest` | `030ecd1036917a3b8291bd49d2d2fae556bd239e511aa0ad787ea8bbf54b7e69` |
| canonical report | 600 bytes UTF-8 (text below) |
| approval digest | `ccf6f0f7655c212ae128b06d1774072c0e6e84e747037355bafa835398eb797c` |
| header | 696 bytes, SHA-256 `9b7b93cbaaee4ff6c9cf755d1f6dae139ffd550530a82137de5c1d46684346f8` |
| `plain` | 68,710 bytes, SHA-256 `a499832fd7ca80123d47142a4b812f5c24a460294add20cf0543c5bc9ec5154a` |
| signature / ciphertext / pages | 75-77 / 68,887-68,889 bytes / 3 pages (randomised; ranges measured over 40 seals) |
| canonical `{"b":"שלום","a":1}` | `{"a":1,"b":"שלום"}` (`EncryptedPackageTest.kt:56-59`) |

Canonical report:

```json
{"address":"רחוב בדיקה 1","exact_text":"דיווח בדיקה","id":"synthetic-id","latitude":31.0,"longitude":35.0,"original_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","outgoing_sha256":"b20448614e74a31a87213a9b4894f5cdf20280457f20e08ad865557959e9c64d","plate":"1234567","reporter":{"email":"synthetic@example.invalid","first":"synthetic-first","id":"000000000","last":"synthetic-last","phone":"0000000"},"review_digest":"030ecd1036917a3b8291bd49d2d2fae556bd239e511aa0ad787ea8bbf54b7e69","schema":1,"subject":"sidewalk_parking","time":"2026-01-01T12:00:00Z"}
```

Escaping vector (org.json 20260814): the string `a/b</c"d\e`, newline, U+0001, U+2028, U+0085, `é` canonicalises to `"a/b<\/c\"d\\e\n\u0001\u2028\u0085é"`.

How these were checked (2026-11-08, `main` at `51d6d88`): recomputed independently in Python from the rules above, and asserted from Kotlin in a local unit test against the real `EncryptedPackage` and `ReviewDraft`; both agree. That test is not in the repository, so CI does not yet guard these values. The ciphertext and signature cannot be golden: ECIES and ECDSA are randomised, and `main` has no constant test keys.

## Known gaps and caveats

- On a phone, `org.json` is Android's built-in implementation, not `org.json:json`. Whether its `quote()` is byte-identical (for example for `/`) was not checked. All vectors above come from JVM unit tests.
- The Python `scripts/synthetic_intake_prototype.py` is an earlier, incompatible format, not PRPKG2.
- Passing validation authenticates an enrolled key and an exact package. It does not prove the photo, the report or the reporter (see [offline receiver](OFFLINE_RECEIVER.md)).

## Not yet done for issue #10

This page is the prose contract only. Still open: JSON Schema files and the malformed cases they reject, golden vectors with constant synthetic keys committed as tests, a CI check that the spec and the Kotlin code stay in sync, publication at a stable public URL (GitHub Pages is not enabled on this repository), and the version policy (when PRPKG3 is cut, and how it maps to a document version).
