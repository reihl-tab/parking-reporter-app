# Address lookup builder (open data, attributed)

`tools/address-lookup/build_address_lookup.py` regenerates the app's
address lookup (`{"version": 1, "streets": [...], "addresses": [...]}`)
from open, attributed sources. It replaces the municipality-supplied
`addresses.json`, whose licence for publication was never established
and which is therefore kept out of this repository.

## Sources and licences

1. **Street codes and names** - the national street registry "רשימת
   רחובות ישראל" (רשות האוכלוסין וההגירה) on **data.gov.il**
   (resource `9ad3862c-8391-4b2f-84a4-2d4c68625f4b`, locality 3000 =
   Jerusalem). Its סמל_רחוב is the municipal street code the 106 API
   uses (spot-checked: אגריפס -> 613). The dataset page carries **no
   dataset-specific licence**, so the site's default licence applies by
   its own terms: "תנאי השימוש באתר מאגרי המידע הממשלתיים"
   (<https://data.gov.il/terms-of-use>, updated 2025-08-30) - a
   worldwide, royalty-free, non-exclusive licence to use, copy,
   distribute and create derivative works, subject to **citing the
   source**, no misleading presentation or distortion, and no unlawful
   or privacy-violating use. Per those terms, the licence in force at
   retrieval time governs; this build was retrieved 2026-11-08. The app
   cites the source on the Settings -> Licenses screen.
2. **Address points (house numbers)** - **© OpenStreetMap contributors**,
   <https://www.openstreetmap.org/copyright>, licensed **ODbL 1.0**
   (<https://opendatacommons.org/licenses/odbl/1-0/>), fetched from the
   Overpass API inside the Jerusalem municipality boundary (OSM relation
   1381350).

## Licence status of the output

Under ODbL 1.0 section 1 ("Derivative Database": a database based on the
ODbL-licensed database) and section 4.4(b):

- The **addresses section** of the generated file is a **Derivative
  Database** of OpenStreetMap. It is itself available under ODbL 1.0,
  and the generated file carries top-level `"attribution"`
  ("© OpenStreetMap contributors") and `"license"` (ODbL-1.0 URL)
  keys so the notice travels with the data. Both loaders (the helper
  pipeline's `Addresses` and the app's `StreetDirectory`) read only the
  `version`/`streets`/`addresses` keys and ignore the notices.
- The **combined file** (streets section beside the addresses section)
  is a **Collective Database** under section 4.5(a): the streets section
  keeps its own status (section 1 above) and is not re-licensed.
- The **individual address lookups** the app produces at runtime are
  **Produced Works**; the attribution shown on the Settings -> Licenses
  screen covers section 4.3's notice requirement for them.

Section 4.6 (making the Derivative Database available): recipients can
obtain the addresses section either

- as a file published under ODbL 1.0 with each app release that ships
  it (release asset `addresses.json`), or
- by running the generator in this repository, which reproduces it
  byte-identically from the pinned inputs recorded below.

The same notice appears on the app's Settings -> Licenses screen.

## Reproduce

```bash
python3 tools/address-lookup/build_address_lookup.py --out addresses.json
python3 tools/address-lookup/validate_lookup.py --candidate addresses.json --sha256 <printed sha>
```

The run prints row counts, the pinned inputs, and the output sha256 to
stderr. Inputs are pinned so a re-run is byte-identical:

- Overpass query: `[date:"2026-11-08T00:00:00Z"]`, relation 1381350
  (`addr:housenumber` + `addr:street` nodes and ways, `out center tags`).
- National registry resource `9ad3862c-8391-4b2f-84a4-2d4c68625f4b`,
  filter סמל_ישוב=3000, retrieved 2026-11-08.

Reference build of 2026-11-08: 4,371 streets, 22,524 address points,
sha256 `bd1d8f5d49549cfdd920a15f6218690630f09f7e48199037b31d96e5c36546cf`.

`validate_lookup.py` checks schema, ordering, duplicates and (optionally)
the exact sha256 without needing any private file. With `--reference` and
`--helper` it also resolves a golden set of known Jerusalem addresses
with both an old and the regenerated file through
`parking-report-helper`'s lookup path and compares street codes, house
numbers and ITM coordinates (rows where both files refuse are reported
as DIFF so the refusal reasons stay visible; an identical refusal on
both sides is expected for addresses outside OSM/registry coverage).

## Notes

- OSM has no Jerusalem municipal street codes, so the code list comes
  from the national street registry on data.gov.il (under the site's
  default licence, source cited - section 1 above); address points come
  from OSM. The two are joined
  inside the app by normalized street name, exactly like the pipeline's
  version-1 file behaves.
- Street names mapping to more than one municipal code are dropped from
  the streets section (the resolver refuses ambiguous names anyway).
- This document travels with the generator; the in-app attribution lives
  in `app/src/main/assets/i18n/strings.json` (`licenses.*` keys).
