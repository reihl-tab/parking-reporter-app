#!/usr/bin/env python3
"""Build the app's version-1 address lookup from open, attributed sources.

Sources and licences: docs/address-lookup.md (the authoritative write-up).
In short:
  * Street codes/names: the national street registry (רשות האוכלוסין
    וההגירה) on data.gov.il, locality 3000 = Jerusalem. No
    dataset-specific licence, so the data.gov.il default licence
    (data.gov.il/terms-of-use) applies: use permitted with source
    attribution.
  * Address points (house numbers): OpenStreetMap via the Overpass API,
    (c) OpenStreetMap contributors, ODbL 1.0.

The addresses section of the output is a Derivative Database under ODbL
1.0 (section 4.4(b)); the combined file is a Collective Database
(section 4.5(a)). The output carries top-level "attribution" and
"license" keys so the notice travels with the data; both known loaders
(the helper pipeline and the app's StreetDirectory) read only the
"version"/"streets"/"addresses" keys and ignore the rest.

Output: {"version": 1, "attribution": ..., "license": ...,
"streets": [...], "addresses": [...]} - streets sorted by numeric street
code, addresses sorted by (street, house, lat, lon), duplicates dropped,
keys sorted: identical inputs give a byte-identical file.

Usage:
  python3 build_address_lookup.py --out addresses.json
Deps: python3 only. Network: Overpass + data.gov.il.
"""
import argparse
import hashlib
import json
import sys
import time
import urllib.parse
import urllib.request

OVERPASS_URL = "https://overpass-api.de/api/interpreter"
# Inputs are pinned so a re-run reproduces the same database
# (docs/address-lookup.md records the pin of each shipped build).
OVERPASS_DATE = "2026-11-08T00:00:00Z"
# Jerusalem municipality boundary (OSM relation 1381350).
OVERPASS_QUERY = (
    '[out:json][timeout:180][date:"' + OVERPASS_DATE + '"];\n'
    "area(3601381350)->.jerusalem;\n"
    "(\n"
    '  node["addr:housenumber"]["addr:street"](area.jerusalem);\n'
    '  way["addr:housenumber"]["addr:street"](area.jerusalem);\n'
    ");\n"
    "out center tags;\n"
)
# National street registry (רשות האוכלוסין וההגירה) on data.gov.il:
# locality 3000 = Jerusalem; סמל_רחוב is the municipal street code the
# 106 API uses (verified: אגריפס -> 613, matching the pipeline lookup).
DATASTORE_URL = "https://data.gov.il/api/3/action/datastore_search"
STREETS_RESOURCE_ID = "9ad3862c-8391-4b2f-84a4-2d4c68625f4b"
JERUSALEM_LOCALITY = "3000"

ATTRIBUTION = "© OpenStreetMap contributors"
LICENSE = "ODbL-1.0 https://opendatacommons.org/licenses/odbl/1-0/ (addresses section)"

HEBREW_FINALS = str.maketrans({"ך": "כ", "ם": "מ", "ן": "נ", "ף": "פ", "ץ": "צ"})


def normalize(text):
    import unicodedata
    folded = unicodedata.normalize("NFKC", text).translate(HEBREW_FINALS)
    return "".join(c for c in folded if c.isalnum())


def fetch(url, data=None, retries=3):
    for attempt in range(retries):
        try:
            req = urllib.request.Request(
                url, data=data,
                headers={"User-Agent": "parking-reporter-app address-lookup builder (ODbL attribution kept)"})
            with urllib.request.urlopen(req, timeout=200) as resp:
                return resp.read()
        except Exception as exc:  # noqa: BLE001 - retry boundary
            if attempt == retries - 1:
                raise
            wait = 20 * (attempt + 1)
            print(f"fetch failed ({exc}); retrying in {wait}s", file=sys.stderr)
            time.sleep(wait)


def load_streets():
    """Street name -> municipal street code, from the national registry."""
    streets = {}
    offset = 0
    while True:
        query = urllib.parse.urlencode({
            "resource_id": STREETS_RESOURCE_ID,
            "filters": json.dumps({"סמל_ישוב": JERUSALEM_LOCALITY}),
            "limit": 5000,
            "offset": offset,
        })
        data = json.loads(fetch(f"{DATASTORE_URL}?{query}"))
        records = data["result"]["records"]
        for row in records:
            name = str(row.get("שם_רחוב") or "").strip()
            code = row.get("סמל_רחוב")
            if not name or code is None:
                continue
            streets[str(code)] = {
                "StreetName": name,
                "StreetCode": str(code),
                "CityName": str(row.get("שם_ישוב") or "").strip(),
                "CityCode": str(row.get("סמל_ישוב") or "").strip(),
            }
        offset += len(records)
        if offset >= data["result"]["total"] or not records:
            break
    # One street name must map to ONE code, like the pipeline's lookup.
    by_name = {}
    for code, entry in streets.items():
        by_name.setdefault(normalize(entry["StreetName"]), set()).add(code)
    ambiguous = {n: c for n, c in by_name.items() if len(c) > 1}
    for name in ambiguous:
        for code in ambiguous[name]:
            del streets[code]
    ordered = [streets[code] for code in sorted(streets, key=int)]
    print(f"streets: {len(ordered)} rows ({len(ambiguous)} ambiguous names dropped)",
          file=sys.stderr)
    return ordered, by_name


def load_address_points():
    """OSM addr:housenumber + addr:street points inside Jerusalem."""
    payload = urllib.parse.urlencode({"data": OVERPASS_QUERY}).encode()
    raw = fetch(OVERPASS_URL, data=payload)
    data = json.loads(raw)
    points = set()
    for el in data.get("elements", []):
        tags = el.get("tags", {})
        house = (tags.get("addr:housenumber") or "").strip()
        street = (tags.get("addr:street") or "").strip()
        if not house or not street:
            continue
        if el.get("type") == "node":
            lat, lon = el.get("lat"), el.get("lon")
        else:
            center = el.get("center") or {}
            lat, lon = center.get("lat"), center.get("lon")
        if lat is None or lon is None:
            continue
        points.add((round(float(lat), 6), round(float(lon), 6), house, street))
    # Deterministic order: (street, house, lat, lon); tuples store
    # (lat, lon, house, street), so re-key for the sort.
    ordered = sorted(points, key=lambda p: (p[3], p[2], p[0], p[1]))
    print(f"addresses: {len(ordered)} OSM points", file=sys.stderr)
    return [list(p) for p in ordered]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="addresses.json")
    args = parser.parse_args()
    streets, _ = load_streets()
    addresses = load_address_points()
    out = {
        "version": 1,
        "attribution": ATTRIBUTION,
        "license": LICENSE,
        "streets": streets,
        "addresses": addresses,
    }
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1, sort_keys=True)
        fh.write("\n")
    digest = hashlib.sha256(open(args.out, "rb").read()).hexdigest()
    print(f"wrote {args.out}: {len(streets)} streets, {len(addresses)} addresses",
          file=sys.stderr)
    print(f"inputs: overpass date={OVERPASS_DATE} relation=1381350; "
          f"registry resource={STREETS_RESOURCE_ID} locality={JERUSALEM_LOCALITY}",
          file=sys.stderr)
    print(f"sha256: {digest}", file=sys.stderr)


if __name__ == "__main__":
    main()
