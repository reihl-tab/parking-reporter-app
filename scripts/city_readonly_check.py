#!/usr/bin/env python3
"""Read-only municipal 106 checks for issue #116.

Runs only in the manually-dispatched, environment-gated
city-readonly-checks workflow. Logs in and fetches the subject-category
catalog (both documented read-only calls); NEVER submits, amends, lists
cases, or writes anything to the municipality.

Prints only pass/fail facts. Credentials, the session token and the raw
catalog are never printed; stdout must stay secret-free.

Credentials arrive via environment variables mapped from Actions
secrets in the workflow (loaded at runtime, never stored in the repo):
  PARKING_106_SUBSCRIPTION_KEY, PARKING_106_USERNAME, PARKING_106_PASSWORD
"""
import os
import sys

from parking_report.api106 import Municipality
from parking_report.subjects import catalog_fingerprint, flatten_category_tree

# Documented fingerprint of the captured catalog in parking-report-helper
# (configs/subjects.json) at the time the read-only client was verified
# against the live municipality, 2026-11-08. Secret-free: sha256 over
# (code, name, path) rows. Update intentionally when the catalog changes.
EXPECTED_CATALOG_FINGERPRINT = (
    "77613b820e657c43c7df6558a5f1d24c3a4bcd1ea7d691bb4bb55db2607c66ab"
)


def main() -> int:
    credentials = {
        "subscription_key": os.environ.get("PARKING_106_SUBSCRIPTION_KEY", ""),
        "login": {
            "UserName": os.environ.get("PARKING_106_USERNAME", ""),
            "Password": os.environ.get("PARKING_106_PASSWORD", ""),
        },
    }
    try:
        client = Municipality(credentials)
    except Exception:
        print("FAIL: credentials missing or malformed (not printed).")
        return 1
    try:
        token = client.login()
    except Exception as exc:
        # Exception messages are already identity- and secret-free.
        print(f"FAIL: login: {exc}")
        return 1
    print("PASS: login succeeded.")
    try:
        tree = client.list_subject_categories(token)
        rows = flatten_category_tree(tree)
    except Exception as exc:
        print(f"FAIL: subject catalog: {exc}")
        return 1
    fingerprint = catalog_fingerprint(rows)
    if fingerprint == EXPECTED_CATALOG_FINGERPRINT:
        print(f"PASS: subject catalog matches the documented catalog "
              f"({len(rows)} rows, fingerprint unchanged).")
        return 0
    print(f"FAIL: subject catalog changed: {len(rows)} rows, "
          f"fingerprint {fingerprint}.")
    print("Action needed: review the municipality's category changes and "
          "refresh the captured catalog in parking-report-helper.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
