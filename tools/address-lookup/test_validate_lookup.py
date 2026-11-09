#!/usr/bin/env python3
"""Offline unit tests for validate_lookup.py (synthetic data, no network).

Run: python3 -m unittest tools/address-lookup/test_validate_lookup.py
(or: python3 tools/address-lookup/test_validate_lookup.py)
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

HERE = pathlib.Path(__file__).parent


def _load(name):
    spec = importlib.util.spec_from_file_location(name, HERE / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


validator = _load("validate_lookup")
builder = _load("build_address_lookup")

# Synthetic data only (no registry or OSM content).
SAMPLE = {
    "version": 1,
    "attribution": "© OpenStreetMap contributors",
    "license": "ODbL-1.0 https://opendatacommons.org/licenses/odbl/1-0/ (addresses section)",
    "streets": [
        {"StreetName": "רחוב א", "StreetCode": "10", "CityName": "ירושלים", "CityCode": "3000"},
        {"StreetName": "רחוב ב", "StreetCode": "20", "CityName": "ירושלים", "CityCode": "3000"},
    ],
    "addresses": [
        [31.78, 35.21, "5", "רחוב א"],
        [31.781, 35.211, "7א", "רחוב ב"],
    ],
}


def section_sha(addresses):
    """The builder's documented canonical form, written out independently."""
    return hashlib.sha256(
        json.dumps(addresses, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    ).hexdigest()


class ValidatorCliTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = pathlib.Path(self.tmp.name) / "candidate.json"
        self.write(SAMPLE)

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, data):
        # Same serialization as the builder's output file.
        with open(self.path, "w", encoding="utf-8") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=1, sort_keys=True)
            fh.write("\n")

    def run_cli(self, *extra):
        sys.argv = ["validate_lookup.py", "--candidate", str(self.path), *extra]
        out = io.StringIO()
        code = 0
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(io.StringIO()):
            try:
                validator.main()
            except SystemExit as exc:
                code = exc.code
        return code or 0, out.getvalue()

    def file_sha(self):
        return hashlib.sha256(self.path.read_bytes()).hexdigest()

    # --- addresses-section hash ---

    def test_right_addresses_hash_passes(self):
        code, out = self.run_cli("--addresses-sha256", section_sha(SAMPLE["addresses"]))
        self.assertEqual(code, 0, out)
        self.assertIn(f"OK   addresses sha256: {section_sha(SAMPLE['addresses'])}", out)
        self.assertIn("validation passed", out)

    def test_wrong_addresses_hash_fails(self):
        code, out = self.run_cli("--addresses-sha256", "0" * 64)
        self.assertEqual(code, 1)
        self.assertIn("FAIL addresses sha256", out)
        self.assertNotIn("validation passed", out)

    def test_addresses_hash_ignores_case_of_hex(self):
        code, out = self.run_cli("--addresses-sha256", section_sha(SAMPLE["addresses"]).upper())
        self.assertEqual(code, 0, out)

    def test_addresses_hash_independent_of_streets_and_notices(self):
        changed = json.loads(json.dumps(SAMPLE))
        changed["streets"].append(
            {"StreetName": "רחוב ג", "StreetCode": "30", "CityName": "ירושלים", "CityCode": "3000"})
        changed["attribution"] = "© OpenStreetMap contributors (rebuilt)"
        self.write(changed)
        code, out = self.run_cli("--addresses-sha256", section_sha(SAMPLE["addresses"]))
        self.assertEqual(code, 0, out)

    def test_addresses_hash_catches_one_changed_point(self):
        changed = json.loads(json.dumps(SAMPLE))
        changed["addresses"][1][2] = "7ב"
        self.write(changed)
        code, out = self.run_cli("--addresses-sha256", section_sha(SAMPLE["addresses"]))
        self.assertEqual(code, 1)
        self.assertIn("FAIL addresses sha256", out)

    def test_addresses_hash_when_section_missing_fails_cleanly(self):
        broken = {k: v for k, v in SAMPLE.items() if k != "addresses"}
        self.write(broken)
        code, out = self.run_cli("--addresses-sha256", section_sha(SAMPLE["addresses"]))
        self.assertEqual(code, 1)
        self.assertIn("FAIL addresses sha256: no addresses list", out)

    def test_validator_and_builder_use_the_same_expression(self):
        self.assertEqual(validator.addresses_section_sha256(SAMPLE["addresses"]),
                         builder.addresses_section_sha256(SAMPLE["addresses"]))
        self.assertEqual(validator.addresses_section_sha256(SAMPLE["addresses"]),
                         section_sha(SAMPLE["addresses"]))

    def test_both_hashes_together(self):
        code, out = self.run_cli("--sha256", self.file_sha(),
                                 "--addresses-sha256", section_sha(SAMPLE["addresses"]))
        self.assertEqual(code, 0, out)
        self.assertIn("OK   sha256:", out)
        self.assertIn("OK   addresses sha256:", out)

    # --- whole-file hash, unchanged behaviour ---

    def test_whole_file_right_hash_passes(self):
        code, out = self.run_cli("--sha256", self.file_sha())
        self.assertEqual(code, 0, out)
        self.assertIn(f"OK   sha256: {self.file_sha()}", out)
        self.assertNotIn("addresses sha256", out)

    def test_whole_file_wrong_hash_fails(self):
        code, out = self.run_cli("--sha256", "f" * 64)
        self.assertEqual(code, 1)
        self.assertIn(f"FAIL sha256: {self.file_sha()} != {'f' * 64}", out)

    def test_whole_file_hash_drifts_with_streets_but_section_does_not(self):
        before = self.file_sha()
        changed = json.loads(json.dumps(SAMPLE))
        changed["streets"].append(
            {"StreetName": "רחוב ג", "StreetCode": "30", "CityName": "ירושלים", "CityCode": "3000"})
        self.write(changed)
        code, out = self.run_cli("--sha256", before,
                                 "--addresses-sha256", section_sha(SAMPLE["addresses"]))
        self.assertEqual(code, 1)
        self.assertIn("FAIL sha256:", out)
        self.assertIn("OK   addresses sha256:", out)

    def test_no_hash_flags_schema_only(self):
        code, out = self.run_cli()
        self.assertEqual(code, 0, out)
        self.assertIn("OK   schema: 2 streets, 2 addresses", out)
        self.assertNotIn("sha256", out)


if __name__ == "__main__":
    unittest.main()
