#!/usr/bin/env python3
"""Offline unit tests for build_address_lookup.py (fetch stubbed, no network).

Run: python3 -m unittest tools/address-lookup/test_build_address_lookup.py
(or: python3 tools/address-lookup/test_build_address_lookup.py)
"""
import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("build_address_lookup.py")
spec = importlib.util.spec_from_file_location("build_address_lookup", MODULE_PATH)
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)

REGISTRY_PAGE = {
    "result": {
        "total": 3,
        "records": [
            {"סמל_ישוב": "3000", "שם_ישוב": "ירושלים", "סמל_רחוב": 20,
             "שם_רחוב": "רחוב ב"},
            {"סמל_ישוב": "3000", "שם_ישוב": "ירושלים", "סמל_רחוב": 10,
             "שם_רחוב": "רחוב א"},
            # Same normalized name as code 10 -> ambiguous pair dropped.
            {"סמל_ישוב": "3000", "שם_ישוב": "ירושלים", "סמל_רחוב": 11,
             "שם_רחוב": "רחוב  א"},
        ],
    }
}

OVERPASS_PAGE = {
    "elements": [
        {"type": "node", "lat": 31.78, "lon": 35.21,
         "tags": {"addr:housenumber": "5", "addr:street": "רחוב ב"}},
        {"type": "way", "center": {"lat": 31.781, "lon": 35.211},
         "tags": {"addr:housenumber": "7", "addr:street": "רחוב ב"}},
        # Exact duplicate of the node row -> dropped by dedup.
        {"type": "node", "lat": 31.78, "lon": 35.21,
         "tags": {"addr:housenumber": "5", "addr:street": "רחוב ב"}},
        # Missing housenumber -> skipped.
        {"type": "node", "lat": 31.78, "lon": 35.21,
         "tags": {"addr:street": "רחוב ב"}},
    ]
}


def fake_fetch(url, data=None, retries=3):
    if "datastore_search" in url:
        return json.dumps(REGISTRY_PAGE).encode()
    return json.dumps(OVERPASS_PAGE).encode()


class NormalizeTest(unittest.TestCase):
    def test_finals_fold(self):
        self.assertEqual(builder.normalize("שלום"), builder.normalize("שלום".replace("ם", "מ")))
        self.assertEqual(builder.normalize("אבן גבירול"), builder.normalize("אבנ גבירול"))

    def test_strips_non_alnum(self):
        self.assertEqual(builder.normalize("רחוב  א"), builder.normalize("רחוב א"))


class BuildTest(unittest.TestCase):
    def setUp(self):
        self.orig_fetch = builder.fetch
        builder.fetch = fake_fetch

    def tearDown(self):
        builder.fetch = self.orig_fetch

    def test_streets_sorted_ambiguity_dropped(self):
        streets, _ = builder.load_streets()
        # "רחוב א" maps to codes 10 and 11 -> both dropped; only 20 remains.
        self.assertEqual([s["StreetCode"] for s in streets], ["20"])

    def test_addresses_deduped_sorted(self):
        points = builder.load_address_points()
        self.assertEqual(points, [[31.78, 35.21, "5", "רחוב ב"],
                                  [31.781, 35.211, "7", "רחוב ב"]])

    def test_output_shape_and_notices(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = pathlib.Path(tmp) / "addresses.json"
            sys.argv = ["build_address_lookup.py", "--out", str(out)]
            builder.main()
            raw = out.read_text(encoding="utf-8")
        data = json.loads(raw)
        self.assertEqual(data["version"], 1)
        self.assertEqual(data["attribution"], builder.ATTRIBUTION)
        self.assertEqual(data["license"], builder.LICENSE)
        self.assertIn("OpenStreetMap contributors", data["attribution"])
        self.assertIn("ODbL-1.0", data["license"])
        # Keys sorted for byte-identical rebuilds.
        raw_keys = list(json.loads(raw, object_pairs_hook=dict).keys())
        self.assertEqual(raw_keys, sorted(raw_keys))

    def test_overpass_query_is_date_pinned(self):
        self.assertIn(f'[date:"{builder.OVERPASS_DATE}"]', builder.OVERPASS_QUERY)


class SchemaSelfTest(unittest.TestCase):
    def test_schema_problems_accepts_builder_output(self):
        validator_path = MODULE_PATH.with_name("validate_lookup.py")
        vspec = importlib.util.spec_from_file_location("validate_lookup", validator_path)
        validator = importlib.util.module_from_spec(vspec)
        vspec.loader.exec_module(validator)
        self.orig_fetch = builder.fetch
        builder.fetch = fake_fetch
        try:
            with tempfile.TemporaryDirectory() as tmp:
                out = pathlib.Path(tmp) / "addresses.json"
                sys.argv = ["build_address_lookup.py", "--out", str(out)]
                builder.main()
                data = json.loads(out.read_text(encoding="utf-8"))
        finally:
            builder.fetch = self.orig_fetch
        self.assertEqual(validator.schema_problems(data), [])


if __name__ == "__main__":
    unittest.main()
