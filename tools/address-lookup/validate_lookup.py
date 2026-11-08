#!/usr/bin/env python3
"""Validate a generated address lookup file.

Always runs a self-contained schema + determinism check on the candidate
(no private reference needed):
  * top-level version == 1, attribution/license notices present;
  * streets: dicts with StreetName/StreetCode/CityName/CityCode strings,
    unique codes, sorted by numeric street code;
  * addresses: [lat, lon, house, street] rows, sorted by
    (street, house, lat, lon), no duplicates.
--sha256 EXPECTED additionally pins the file's exact bytes.

Optionally, with --reference OLD.json --helper /path/to/parking-report-helper,
also resolves a golden set of Jerusalem "street house" addresses through the
pipeline's own resolver with BOTH files and reports every difference in
street code, house, or ITM coordinate. Exit 1 on any failure.

Usage:
  python3 validate_lookup.py --candidate NEW.json [--sha256 HEX]
  python3 validate_lookup.py --candidate NEW.json \
      --reference OLD.json --helper /path/to/parking-report-helper
"""
import argparse
import hashlib
import importlib.util
import json
import pathlib
import sys

GOLDEN = [
    ("אגריפס", "12"),
    ("הנביאים", "12"),
    ("יפו", "100"),
    ("הלל", "23"),
    ("שמאי", "10"),
    ("קינג ג'ורג'", "21"),
    ("עזה", "35"),
    ("בצלאל", "7"),
    ("הלני המלכה", "12"),
    ("דוד ילין", "4"),
]

STREET_KEYS = ("StreetName", "StreetCode", "CityName", "CityCode")


def schema_problems(data):
    problems = []
    if not isinstance(data, dict):
        return ["top level is not an object"]
    if data.get("version") != 1:
        problems.append("version is not 1")
    for key in ("attribution", "license"):
        if not isinstance(data.get(key), str) or not data[key].strip():
            problems.append(f"missing non-empty top-level {key!r} notice")
    streets = data.get("streets")
    if not isinstance(streets, list):
        problems.append("streets is not a list")
    else:
        codes = []
        for i, row in enumerate(streets):
            if not isinstance(row, dict):
                problems.append(f"streets[{i}] is not an object")
                continue
            for key in STREET_KEYS:
                if not isinstance(row.get(key), str) or not row[key].strip():
                    problems.append(f"streets[{i}].{key} missing/not a string")
            code = row.get("StreetCode")
            if isinstance(code, str) and code.isdigit():
                codes.append(int(code))
            else:
                problems.append(f"streets[{i}].StreetCode not numeric: {code!r}")
        if len(codes) != len(set(codes)):
            problems.append("duplicate street codes")
        if codes != sorted(codes):
            problems.append("streets not sorted by numeric code")
    addresses = data.get("addresses")
    if not isinstance(addresses, list):
        problems.append("addresses is not a list")
    else:
        seen = set()
        keys = []
        for i, row in enumerate(addresses):
            if not (isinstance(row, list) and len(row) == 4):
                problems.append(f"addresses[{i}] is not a 4-element row")
                continue
            lat, lon, house, street = row
            if not all(isinstance(v, (int, float)) for v in (lat, lon)):
                problems.append(f"addresses[{i}] lat/lon not numeric")
            if not all(isinstance(v, str) and v.strip() for v in (house, street)):
                problems.append(f"addresses[{i}] house/street missing")
            t = tuple(row)
            if t in seen:
                problems.append(f"addresses[{i}] duplicate row")
            seen.add(t)
            keys.append((street, house, lat, lon))
        if keys != sorted(keys):
            problems.append("addresses not sorted by (street, house, lat, lon)")
    return problems


def load_resolver(helper_root):
    spec = importlib.util.spec_from_file_location(
        "api106", f"{helper_root}/src/parking_report/api106.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--sha256", help="expected sha256 of the candidate file")
    parser.add_argument("--reference", help="optional reference file for golden diff")
    parser.add_argument("--helper", help="helper repo root (required with --reference)")
    args = parser.parse_args()

    failures = 0
    raw = pathlib.Path(args.candidate).read_bytes()
    if args.sha256:
        digest = hashlib.sha256(raw).hexdigest()
        if digest != args.sha256:
            print(f"FAIL sha256: {digest} != {args.sha256}")
            failures += 1
        else:
            print(f"OK   sha256: {digest}")
    data = json.loads(raw.decode("utf-8"))
    problems = schema_problems(data)
    for problem in problems:
        print(f"FAIL schema: {problem}")
    failures += len(problems)
    if not problems:
        print(f"OK   schema: {len(data['streets'])} streets, "
              f"{len(data['addresses'])} addresses")

    if args.reference:
        if not args.helper:
            print("FAIL --reference requires --helper", file=sys.stderr)
            sys.exit(2)
        module = load_resolver(args.helper)
        diffs = 0
        for street, house in GOLDEN:
            row = []
            for label, path in (("ref", args.reference), ("new", args.candidate)):
                try:
                    resolved = module.Addresses(pathlib.Path(path)).resolve(street, house, None)
                    row.append((label, resolved))
                except Exception as exc:  # noqa: BLE001 - comparison harness
                    row.append((label, f"ERR: {exc}"))
            both_err = all(str(v).startswith("ERR:") for _, v in row)
            same = row[0][1] == row[1][1] and not both_err
            if not same:
                diffs += 1
            print(("OK   " if same else "DIFF ")
                  + f"{street} {house}: {row[0][1]} vs {row[1][1]}")
        if diffs:
            print(f"{diffs} differences", file=sys.stderr)
            failures += diffs
        else:
            print("all golden addresses identical")

    if failures:
        sys.exit(1)
    print("validation passed")


if __name__ == "__main__":
    main()
