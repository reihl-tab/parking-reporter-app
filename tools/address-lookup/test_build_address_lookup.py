#!/usr/bin/env python3
"""Offline unit tests for build_address_lookup.py (fetch stubbed, no network).

Run: python3 -m unittest tools/address-lookup/test_build_address_lookup.py
(or: python3 tools/address-lookup/test_build_address_lookup.py)
"""
import contextlib
import hashlib
import importlib.util
import io
import json
import pathlib
import sys
import tempfile
import unittest
import urllib.parse

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
    "osm3s": {
        "timestamp_osm_base": "2026-11-08T00:00:00Z",
        "timestamp_areas_base": "2026-11-08T00:00:00Z",
        "copyright": "The data included in this document is from www.openstreetmap.org.",
    },
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


def fake_fetch_no_osm3s(url, data=None, retries=3):
    if "datastore_search" in url:
        return json.dumps(REGISTRY_PAGE).encode()
    page = {k: v for k, v in OVERPASS_PAGE.items() if k != "osm3s"}
    return json.dumps(page).encode()


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

    def test_overpass_area_is_built_from_the_dated_relation(self):
        query = builder.OVERPASS_QUERY
        lines = query.splitlines()
        # The [date:] setting is the query's global first statement, so the
        # relation lookup below runs under it (one query, no second request).
        self.assertTrue(lines[0].startswith("[out:json]"))
        self.assertIn(f'[date:"{builder.OVERPASS_DATE}"]', lines[0])
        self.assertEqual(builder.OVERPASS_RELATION, 1381350)
        self.assertIn("rel(1381350);", lines)
        self.assertIn("map_to_area->.jerusalem;", lines)
        self.assertLess(lines.index("rel(1381350);"),
                        lines.index("map_to_area->.jerusalem;"))
        # No hard-coded derived area id any more.
        self.assertNotIn("area(3601381350)", query)
        self.assertNotIn("3601381350", query)
        self.assertEqual(query.count("(area.jerusalem)"), 2)

    def test_query_is_what_is_posted(self):
        sent = []

        def recording_fetch(url, data=None, retries=3):
            sent.append((url, data))
            return fake_fetch(url, data, retries)

        builder.fetch = recording_fetch
        builder.load_address_points()
        self.assertEqual(len(sent), 1)
        url, data = sent[0]
        self.assertEqual(url, builder.OVERPASS_URL)
        self.assertEqual(urllib.parse.parse_qs(data.decode())["data"], [builder.OVERPASS_QUERY])

    def test_prints_overpass_timestamps_when_present(self):
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            builder.load_address_points()
        out = err.getvalue()
        self.assertIn("overpass timestamp_osm_base: 2026-11-08T00:00:00Z", out)
        self.assertIn("overpass timestamp_areas_base: 2026-11-08T00:00:00Z", out)

    def test_no_timestamp_line_when_absent(self):
        builder.fetch = fake_fetch_no_osm3s
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            points = builder.load_address_points()
        self.assertNotIn("timestamp_osm_base", err.getvalue())
        self.assertNotIn("timestamp_areas_base", err.getvalue())
        self.assertEqual(len(points), 2)

    def test_addresses_section_sha256_is_the_documented_expression(self):
        points = builder.load_address_points()
        expected = hashlib.sha256(
            json.dumps(points, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        ).hexdigest()
        self.assertEqual(builder.addresses_section_sha256(points), expected)

    def test_main_prints_the_section_sha256(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = pathlib.Path(tmp) / "addresses.json"
            sys.argv = ["build_address_lookup.py", "--out", str(out)]
            err = io.StringIO()
            with contextlib.redirect_stderr(err):
                builder.main()
            data = json.loads(out.read_text(encoding="utf-8"))
        digest = builder.addresses_section_sha256(data["addresses"])
        self.assertIn(f"sha256(addresses section, compact JSON): {digest}", err.getvalue())


class DocsAgreementTest(unittest.TestCase):
    """docs/address-lookup.md describes the query and checks the code runs."""

    DOCS = MODULE_PATH.parents[2] / "docs" / "address-lookup.md"

    def setUp(self):
        self.docs = self.DOCS.read_text(encoding="utf-8")
        self.flat = " ".join(self.docs.split())

    def test_docs_name_the_date_pin_and_the_dated_relation(self):
        self.assertIn(f'[date:"{builder.OVERPASS_DATE}"]', self.docs)
        self.assertIn(f"rel({builder.OVERPASS_RELATION}); map_to_area->.jerusalem;", self.docs)

    def test_docs_state_the_boundary_condition_instead_of_exactly(self):
        self.assertNotIn("exactly reproducible", self.docs)
        self.assertIn("while the boundary relation 1381350", self.flat)

    def test_docs_document_the_section_hash_check(self):
        self.assertIn("--addresses-sha256", self.docs)
        self.assertIn('json.dumps(addresses, ensure_ascii=False, separators=(",", ":"))', self.docs)

    def test_docs_claim_no_release_asset(self):
        # Section 4.6 names one route; no text may claim a release asset exists.
        self.assertNotIn("release asset with", self.flat)
        self.assertIn("the generator route, no release asset.", self.flat)


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
