# Municipal taxonomy parity

The app carries a captured snapshot of the Jerusalem 106 subject catalog
(`GetSubjectCategories`) so that local categories can be mapped to municipal
subjects offline, without any network call or municipal credential. This is
groundwork for the DESIGN M4 parity goal and the future M5 payload design; it
changes no submission behavior, and submission stays disabled.

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

The picker still shows only the three local categories. The mapping is data
for future payload construction; nothing is sent.

## Boundaries

- No live municipal calls, no endpoints, no credentials (unchanged).
- No automatic classification: category choice remains manual.
- The catalog is municipal public data captured from the 106 app; no user
  data is involved.
