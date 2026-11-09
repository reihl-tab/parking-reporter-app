# Municipal taxonomy parity

The app carries a captured snapshot of the Jerusalem 106 subject catalog
(`GetSubjectCategories`) so that local categories can be mapped to municipal
subjects offline, without any network call or municipal credential. The submission path sends the chosen code plus the approved Hebrew description.
The snapshot itself makes no network calls.

## Asset

`app/src/main/assets/taxonomy/municipal_subjects.json`:

- 86 subjects (code, Hebrew name, hierarchy path), ported from the public
  parking-report-helper `configs/subjects.json`, itself a captured
  `GetSubjectCategories` response.
- `captured_at: 2026-10-05`, `verified_live_at: 2026-11-08`: on 2026-11-08 the
  captured catalog was compared against the live municipal response and found
  identical (parking-report-helper issue #52 / PR #305 smoke check).
- `fingerprint_sha256` records the canonical fingerprint, and
  `fingerprint_rule` documents how it is computed.

The catalog is a snapshot, not a live feed. Refreshing it is a manual,
reviewed step: re-capture via the helper, compare fingerprints, review the
diff, then update the asset and the expected fingerprint together.

## Fingerprint parity

`MunicipalSubjects.canonicalFingerprint` replicates the helper's
`subjects.catalog_fingerprint` exactly:

1. Encode each row as `json.dumps([code, name, path], ensure_ascii=False)`
   (Python escaping rules; `MunicipalSubjects.pythonJsonString` reproduces
   them, including backslash and control-character handling).
2. Sort the encoded rows, join with `\n`, sha256 hex.

Unit tests compare the recomputed fingerprint against the embedded value with
cross-platform golden vectors, so an accidental asset edit fails the build on
any platform. Expected fingerprint:
`77613b820e657c43c7df6558a5f1d24c3a4bcd1ea7d691bb4bb55db2607c66ab`.

## Local category mapping

| Local category | Municipal subject | Name |
| --- | --- | --- |
| `sidewalk_parking` | 1145 | חנייה אסורה - חנייה על מדרכה |
| `broken_sign` | 17841 | תמרור תנועה נפל / דהוי / שבור / נוטה ליפול |
| `faded_crosswalk` | 1137 | חידוש סימון מעבר חצייה |
| `bollard_request` | 1280 | עמוד מחסום ברזל נוטה ליפול / נפל |

### Bollard installation requests use the established bollard channel

The catalog's `1280` label describes a leaning or fallen iron barrier post.
The municipal opening-receipt label is shorter: an uprooted/fallen barrier
post. That repair-style label does not by itself establish an installation
service. However, historical filed payloads and their matching city opening
receipts show that new-installation requests have repeatedly used `1280`.
Some also have owner-confirmed installation outcomes. This is the operational
basis for the mapping, not a claim that the catalog says "new installation".

The approved Hebrew description must explicitly request installation. It
must not falsely say an existing post was uprooted or fell. The API sends only
`CaseSubjectCode` and the approved description; the local picker label does
not alter the city's catalog. Tests pin the code, exact catalog label and path.

`5164` is generic Other repeated under 12 catalog paths. It is not the chosen
bollard mapping. Offline inspection of the official 106+ v8.6.4 app found only
`CaseSubjectCode` assigned on selection and `WebCaseSubjectCode` left null, so
sending a local hierarchy path would not establish a specific destination.

The historical reports, receipts and outcome evidence remain private. No
report-specific address, case number, photo or approval message belongs here.
A prepared code mapping does not authorize a live filing or a change to any
already-approved filing parameters. Keep the live run held until that choice
is confirmed. Do not probe routing by creating a test case.

## Boundaries

- The catalog loader makes no live municipal calls and uses no credentials.
- No automatic classification: category choice remains manual.
- The catalog is municipal public data captured from the 106 app; no user
  data is involved.
