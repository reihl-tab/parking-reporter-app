#!/usr/bin/env python3
"""Submission dry-run for the municipal 106 path: everything except CreateCase.

Exercises the full pre-submission chain a real filing performs, and stops
one step before a case would be created:

  1. Login (documented read-write session call).
  2. GetSubjectCategories (read-only) + fingerprint check against the
     documented catalog, so a silently changed catalog fails the dry-run
     instead of letting a real filing pick a stale code.
  3. createupdatecontact with the exact reporter record the pipeline
     sends (parking_report.api106.make_contact + Municipality.contact).
     This is the same write every real filing performs; it upserts the
     reporter's own contact card and creates nothing case-like.
  4. STOP. CreateCase and AddFileToCase are never called.

Run it manually from a maintainer network, not from GitHub-hosted
runners: the municipal API answers runner IP ranges with 403. It is NOT
wired into any workflow and carries no schedule.

Prints only pass/fail facts. Credentials, the session token, the
reporter's national ID and the returned ContactIdInCRM are never
printed; stdout must stay secret-free.

Environment variables:
  PARKING_106_SUBSCRIPTION_KEY, PARKING_106_USERNAME, PARKING_106_PASSWORD
    - municipal API credentials (as in city_readonly_check.py).
  PARKING_REPORTER_FIRST_NAME, PARKING_REPORTER_LAST_NAME,
  PARKING_REPORTER_PHONE, PARKING_REPORTER_ID_NUMBER, PARKING_REPORTER_EMAIL
    - the reporter record, exactly as real filings supply it. Phone may be
      05XXXXXXXX or +9725XXXXXXXX; the ID number is checksum-validated.
"""
import os
import sys
from types import SimpleNamespace

from parking_report.api106 import Municipality, make_contact
from parking_report.subjects import catalog_fingerprint, flatten_category_tree

# Same documented fingerprint as city_readonly_check.py (live-verified
# 2026-11-08). Keep the two in sync; update intentionally on catalog change.
EXPECTED_CATALOG_FINGERPRINT = (
    "47e2f39cd4e347e449627c7a9fa2bd4b9d1e096cdf33c531f0683b493b594438"
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
    if fingerprint != EXPECTED_CATALOG_FINGERPRINT:
        print(f"FAIL: subject catalog changed: {len(rows)} rows, "
              f"fingerprint {fingerprint}.")
        print("A submission path built on the documented catalog is no "
              "longer accurate; review the municipality's changes first.")
        return 1
    print(f"PASS: subject catalog matches the documented catalog "
          f"({len(rows)} rows, fingerprint unchanged).")

    try:
        reporter = make_contact(SimpleNamespace(
            national_id=os.environ.get("PARKING_REPORTER_ID_NUMBER", ""),
            first_name=os.environ.get("PARKING_REPORTER_FIRST_NAME", ""),
            last_name=os.environ.get("PARKING_REPORTER_LAST_NAME", ""),
            phone=os.environ.get("PARKING_REPORTER_PHONE", ""),
            email=os.environ.get("PARKING_REPORTER_EMAIL", ""),
        ))
    except Exception as exc:
        print(f"FAIL: reporter record invalid: {exc}")
        return 1
    try:
        contact = client.contact(token, reporter)
    except Exception as exc:
        print(f"FAIL: createupdatecontact: {exc}")
        return 1
    contact_id = contact["ContactIdInCRM"]
    if not isinstance(contact_id, str) or not contact_id.strip():
        print("FAIL: municipality returned an empty ContactIdInCRM.")
        return 1
    print(f"PASS: createupdatecontact returned a contact ID "
          f"(length {len(contact_id)}, value not printed).")

    print("DRY-RUN COMPLETE: login, catalog and contact upsert all work. "
          "Stopped before CreateCase; no case was created.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
