"""Generate the golden municipal request bodies in app/src/test/resources/municipal/.

One-time local tool, NOT run in CI: it requires parking-report-helper 0.6.0
(pinned 51dd5c02916b1ebbe62fdd2390132f9f58a39eb8) installed locally. The
committed fixtures are what CI tests compare against. All values are
synthetic: no real credentials, people, plates, addresses, or photos.

Re-run only when the helper's payload shape changes, then review the fixture
diff and the Kotlin port in the same PR.
"""

from __future__ import annotations

import argparse
import base64
import json
from pathlib import Path

from parking_report.api106 import make_contact

OUT = Path(__file__).resolve().parent.parent / "app/src/test/resources/municipal"

TOKEN = "synthetic-token"
SYNTHETIC_JPEG = b"synthetic-jpeg-bytes-not-a-photo"


def main() -> None:
    reporter = argparse.Namespace(
        first_name="ישראל",
        last_name="ישראלי",
        phone="+972501234567",
        national_id="000000018",
        email="synthetic@example.com",
    )
    contact = make_contact(reporter)
    contact_with_token = dict(contact, Token=TOKEN)
    login_body = {"UserName": "synthetic-user", "Password": "synthetic-password"}
    contact_body = dict(contact_with_token, SignUpByApp=True)
    report = {
        "CaseDescription": "דיווח סינתטי לבדיקת התאמת מטען בלבד.",
        "CaseSubjectCode": "1145",
        "CaseStreetCode": "1630",
        "CaseStreetName": "הפלמח",
        "CaseHouseNumber": "900",
        "CaseAddressText": "הפלמח 900",
        "CoordinateX": 220000,
        "CoordinateY": 630500,
        "Language": 1,
        "ApplicationCaseNumber": "ApplicationCaseNumber_VAL",
    }
    contact_for_create = dict(contact_with_token, ContactIdInCRM="synthetic-contact-id")
    create_body = dict(report, Token=TOKEN, contact=contact_for_create)
    attach_body = {
        "token": TOKEN,
        "caseIdInCRM": "synthetic-case-id",
        "webCaseIdInCRM": "synthetic-webcase-id",
        "fileName": "synthetic.jpg",
        "fileContent": base64.b64encode(SYNTHETIC_JPEG).decode("ascii"),
    }
    for name, body in [
        ("golden-login.json", login_body),
        ("golden-contact.json", contact_body),
        ("golden-createcase.json", create_body),
        ("golden-attach.json", attach_body),
    ]:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        (OUT / name).write_bytes(data)
        print(name, len(data))
    inputs = {
        "reporter": {
            "first_name": reporter.first_name,
            "last_name": reporter.last_name,
            "phone": reporter.phone,
            "national_id": reporter.national_id,
            "email": reporter.email,
        },
        "login": login_body,
        "token": TOKEN,
        "contact_id": "synthetic-contact-id",
        "report": report,
        "case_id": "synthetic-case-id",
        "webcase_id": "synthetic-webcase-id",
        "file_name": "synthetic.jpg",
        "jpeg_bytes_utf8": SYNTHETIC_JPEG.decode("ascii"),
    }
    (OUT / "synthetic-inputs.json").write_text(
        json.dumps(inputs, ensure_ascii=False, indent=1), encoding="utf-8"
    )
    print("synthetic-inputs.json written")


if __name__ == "__main__":
    main()
