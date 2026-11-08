"""Offline tests for scripts/city_readonly_check.py (issue #125).

The read-only check normally runs only in the manually dispatched
city-readonly-checks workflow, against the live municipality. These tests
run it with the municipal client replaced by a local fake, so they need no
network, no credentials and no parking-report-helper checkout:

  - the parking_report modules the script imports are synthetic stand-ins
    installed in sys.modules for each test only;
  - every socket connect and DNS lookup raises, so a request that slipped
    past the fake would fail the test instead of reaching a server;
  - all credentials and tokens are synthetic sentinels, and every test
    checks that none of them appears in the script's output.

The fake client follows the helper's documented error contract
(InputError for unusable credentials, APIError "<endpoint>: HTTP <code>."
for HTTP failures, ValueError from flatten_category_tree for a malformed
tree); it does not exercise the helper's own code.
"""

from __future__ import annotations

import ast
import contextlib
import hashlib
import importlib.util
import io
import json
import os
import runpy
import socket
import sys
import types
import unittest
import urllib.request
from pathlib import Path
from typing import ClassVar
from unittest import mock

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "city_readonly_check.py"

# Synthetic sentinels only; never real credentials.
SUBSCRIPTION_KEY = "synthetic-subscription-key-0f1e2d"
USERNAME = "synthetic-user-4c3b2a"
PASSWORD = "synthetic-password-9a8b7c"
TOKEN = "synthetic-session-token-6d5e4f"
SECRETS = (SUBSCRIPTION_KEY, USERNAME, PASSWORD, TOKEN)
ENV_NAMES = (
    "PARKING_106_SUBSCRIPTION_KEY",
    "PARKING_106_USERNAME",
    "PARKING_106_PASSWORD",
)
SYNTHETIC_ENV = dict(
    zip(ENV_NAMES, (SUBSCRIPTION_KEY, USERNAME, PASSWORD), strict=True)
)

# A small synthetic category tree; the codes and names are invented.
CAPTURED_TREE = [
    {
        "categoryName": "Synthetic A",
        "children": [
            {"categoryName": "Synthetic A1", "subjectCode": "9001"},
            {"categoryName": "Synthetic A2", "subjectCode": "9002"},
        ],
    },
    {"categoryName": "Synthetic B", "subjectCode": "9003"},
]
CHANGED_TREE = CAPTURED_TREE + [{"categoryName": "Synthetic C", "subjectCode": "9004"}]


class NetworkBlocked(AssertionError):
    """Raised by every socket connect or lookup while a test runs."""


class InputError(Exception):
    pass


class APIError(Exception):
    pass


class FakeMunicipality:
    """Stand-in for parking_report.api106.Municipality; no I/O at all."""

    login_error: Exception | None = None
    login_token: str = TOKEN
    catalog_error: Exception | None = None
    tree: object = CAPTURED_TREE
    instances: ClassVar[list[FakeMunicipality]] = []

    def __init__(self, credentials):
        type(self).instances.append(self)
        self.calls: list[str] = []
        login = credentials.get("login") if isinstance(credentials, dict) else None
        if (
            not isinstance(login, dict)
            or not credentials.get("subscription_key")
            or not login.get("UserName")
            or not login.get("Password")
        ):
            raise InputError(
                "Credentials must contain subscription_key and login.UserName/login.Password."
            )
        self.credentials = credentials

    def login(self):
        self.calls.append("Login")
        if self.login_error is not None:
            raise self.login_error
        return self.login_token

    def list_subject_categories(self, token):
        self.calls.append("GetSubjectCategories")
        if token != TOKEN:
            raise InputError("Invalid session token.")
        if self.catalog_error is not None:
            raise self.catalog_error
        return self.tree


def fake_flatten_category_tree(nodes):
    """Rows of (code, name, path) like the helper's; ValueError if malformed."""
    if not isinstance(nodes, list):
        raise ValueError("Category tree must be a list of nodes")  # noqa: TRY004 - helper contract
    rows = []

    def walk(node, path):
        if not isinstance(node, dict):
            raise ValueError("Category node must be an object")  # noqa: TRY004 - helper contract
        name = node.get("categoryName")
        if not isinstance(name, str) or not name.strip():
            raise ValueError("Category node needs a categoryName")
        children = node.get("children", [])
        if not isinstance(children, list):
            raise ValueError("Category children must be a list")  # noqa: TRY004 - helper contract
        here = [*path, name]
        if node.get("subjectCode") is not None:
            rows.append({"code": node["subjectCode"], "name": name, "path": here})
        for child in children:
            walk(child, here)

    for node in nodes:
        walk(node, [])
    return rows


def synthetic_fingerprint(rows):
    items = sorted(json.dumps([r["code"], r["name"], r["path"]]) for r in rows)
    return hashlib.sha256("\n".join(items).encode("utf-8")).hexdigest()


class CityReadonlyCheckTest(unittest.TestCase):
    def setUp(self):
        self.connections: list[object] = []

        def blocked(*args, **kwargs):
            self.connections.append(args)
            raise NetworkBlocked("network access attempted in an offline test")

        for target, name in (
            (socket, "create_connection"),
            (socket, "getaddrinfo"),
            (socket.socket, "connect"),
            (socket.socket, "connect_ex"),
        ):
            patcher = mock.patch.object(target, name, blocked)
            patcher.start()
            self.addCleanup(patcher.stop)

        FakeMunicipality.instances = []
        for attr, value in (
            ("login_error", None),
            ("login_token", TOKEN),
            ("catalog_error", None),
            ("tree", CAPTURED_TREE),
        ):
            patcher = mock.patch.object(FakeMunicipality, attr, value)
            patcher.start()
            self.addCleanup(patcher.stop)

        # The synthetic "captured" tree stands in for the real catalog: its
        # fingerprint maps to the script's documented constant, and any other
        # tree keeps its own synthetic fingerprint.
        self.expected = self.read_expected_fingerprint()
        captured = synthetic_fingerprint(fake_flatten_category_tree(CAPTURED_TREE))

        def fingerprint(rows):
            value = synthetic_fingerprint(rows)
            return self.expected if value == captured else value

        package = types.ModuleType("parking_report")
        package.__path__ = []
        api106 = types.ModuleType("parking_report.api106")
        api106.Municipality = FakeMunicipality
        api106.InputError = InputError
        api106.APIError = APIError
        subjects = types.ModuleType("parking_report.subjects")
        subjects.flatten_category_tree = fake_flatten_category_tree
        subjects.catalog_fingerprint = fingerprint
        modules = mock.patch.dict(
            sys.modules,
            {
                "parking_report": package,
                "parking_report.api106": api106,
                "parking_report.subjects": subjects,
            },
        )
        modules.start()
        self.addCleanup(modules.stop)

        environ = mock.patch.dict(os.environ, SYNTHETIC_ENV)
        environ.start()
        self.addCleanup(environ.stop)

    def tearDown(self):
        self.assertEqual(self.connections, [], "a test tried to open a socket")

    @staticmethod
    def read_expected_fingerprint():
        tree = ast.parse(SCRIPT.read_text(encoding="utf-8"))
        for node in tree.body:
            if isinstance(node, ast.Assign) and [
                getattr(t, "id", None) for t in node.targets
            ] == ["EXPECTED_CATALOG_FINGERPRINT"]:
                value = ast.literal_eval(node.value)
                if isinstance(value, str) and len(value) == 64:
                    return value
        raise AssertionError("EXPECTED_CATALOG_FINGERPRINT not found in the script")

    def load_script(self):
        spec = importlib.util.spec_from_file_location("city_readonly_check", SCRIPT)
        module = importlib.util.module_from_spec(spec)
        with mock.patch.object(sys, "dont_write_bytecode", True):
            spec.loader.exec_module(module)
        return module

    def run_main(self):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            rc = self.load_script().main()
        text = out.getvalue() + err.getvalue()
        for secret in SECRETS:
            self.assertNotIn(secret, text)
        return rc, out.getvalue().splitlines()

    def requests(self):
        return [call for client in FakeMunicipality.instances for call in client.calls]

    def test_network_guard_is_active(self):
        with self.assertRaises(NetworkBlocked):
            urllib.request.urlopen("https://example.invalid/", timeout=1)
        with self.assertRaises(NetworkBlocked):
            socket.create_connection(("192.0.2.1", 443), timeout=1)
        self.connections.clear()

    def test_missing_credentials_fail_with_zero_requests(self):
        for name in ENV_NAMES:
            with self.subTest(missing=name):
                FakeMunicipality.instances = []
                with mock.patch.dict(os.environ):
                    del os.environ[name]
                    rc, lines = self.run_main()
                self.assertEqual(rc, 1)
                self.assertEqual(
                    lines, ["FAIL: credentials missing or malformed (not printed)."]
                )
                self.assertEqual(self.requests(), [])

    def test_login_http_401_fails(self):
        FakeMunicipality.login_error = APIError("Login: HTTP 401.")
        rc, lines = self.run_main()
        self.assertEqual(rc, 1)
        self.assertEqual(lines, ["FAIL: login: Login: HTTP 401."])
        self.assertEqual(self.requests(), ["Login"])

    def test_catalog_http_error_fails(self):
        FakeMunicipality.catalog_error = APIError("Subject categories: HTTP 503.")
        rc, lines = self.run_main()
        self.assertEqual(rc, 1)
        self.assertEqual(
            lines,
            [
                "PASS: login succeeded.",
                "FAIL: subject catalog: Subject categories: HTTP 503.",
            ],
        )

    def test_malformed_catalog_fails(self):
        for tree in ({"categoryName": "not a list"}, [{"subjectCode": "9001"}], ["x"]):
            with self.subTest(tree=tree):
                FakeMunicipality.tree = tree
                rc, lines = self.run_main()
                self.assertEqual(rc, 1)
                self.assertEqual(lines[0], "PASS: login succeeded.")
                self.assertTrue(lines[1].startswith("FAIL: subject catalog: "), lines)
                self.assertNotIn(self.expected, "\n".join(lines))

    def test_changed_catalog_fails_and_prints_new_fingerprint(self):
        FakeMunicipality.tree = CHANGED_TREE
        new = synthetic_fingerprint(fake_flatten_category_tree(CHANGED_TREE))
        self.assertNotEqual(new, self.expected)
        rc, lines = self.run_main()
        self.assertEqual(rc, 1)
        self.assertEqual(lines[0], "PASS: login succeeded.")
        self.assertEqual(
            lines[1], f"FAIL: subject catalog changed: 4 rows, fingerprint {new}."
        )
        self.assertTrue(lines[2].startswith("Action needed: "))
        self.assertFalse(
            any("matches the documented catalog" in line for line in lines)
        )

    def test_captured_catalog_passes(self):
        rc, lines = self.run_main()
        self.assertEqual(rc, 0)
        self.assertEqual(
            lines,
            [
                "PASS: login succeeded.",
                (
                    "PASS: subject catalog matches the documented catalog "
                    "(3 rows, fingerprint unchanged)."
                ),
            ],
        )
        self.assertEqual(self.requests(), ["Login", "GetSubjectCategories"])

    def test_exit_code_reaches_the_process(self):
        for tree, code in ((CAPTURED_TREE, 0), (CHANGED_TREE, 1)):
            with self.subTest(code=code):
                FakeMunicipality.tree = tree
                with (
                    contextlib.redirect_stdout(io.StringIO()) as out,
                    self.assertRaises(SystemExit) as caught,
                ):
                    runpy.run_path(str(SCRIPT), run_name="__main__")
                self.assertEqual(caught.exception.code, code)
                for secret in SECRETS:
                    self.assertNotIn(secret, out.getvalue())

    def test_no_secret_value_in_output_on_any_path(self):
        # run_main() already checks every run; this sweeps all paths in one
        # place so the guarantee is named and survives edits to other tests.
        scenarios = (
            ("login 401", "login_error", APIError("Login: HTTP 401.")),
            (
                "catalog error",
                "catalog_error",
                APIError("Subject categories: HTTP 503."),
            ),
            ("malformed", "tree", {"categoryName": "not a list"}),
            ("changed", "tree", CHANGED_TREE),
            ("captured", "tree", CAPTURED_TREE),
        )
        printed = []
        for label, attr, value in scenarios:
            with (
                self.subTest(path=label),
                mock.patch.object(FakeMunicipality, attr, value),
            ):
                out = io.StringIO()
                with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
                    self.load_script().main()
                printed.append(out.getvalue())
        with mock.patch.dict(os.environ, {ENV_NAMES[0]: ""}):
            out = io.StringIO()
            with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
                self.load_script().main()
            printed.append(out.getvalue())
        self.assertTrue(all(printed))
        for secret in SECRETS:
            self.assertFalse(
                any(secret in text for text in printed), "a secret was printed"
            )


if __name__ == "__main__":
    unittest.main()
