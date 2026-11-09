#!/usr/bin/env bash
# Public logs contain constant markers only. Never print values or env dumps.
set -euo pipefail
required=(
  J106_SUBSCRIPTION_KEY J106_APP_USERNAME J106_APP_PASSWORD
  REPORTER_FIRST_NAME REPORTER_LAST_NAME REPORTER_PHONE REPORTER_ID_NUMBER REPORTER_EMAIL
  TRIAL_APPROVED_TEXT TRIAL_APPROVED_SHA256 TRIAL_SUBJECT TRIAL_ADDRESS
  TRIAL_LAT TRIAL_LON TRIAL_CAPTURED_AT TRIAL_STREET_LOOKUP_JSON TRIAL_DATA_KEY
)
for name in "${required[@]}"; do
  value=${!name-}
  if [[ -z ${value//[[:space:]]/} ]]; then
    printf '%s\n' 'TRIAL_RESULT INPUT_MISSING'
    exit 1
  fi
done
# A blank plate is intentional for plate-free categories. Category and draft
# validity, exact text digest, and the existing protected gate stay authoritative.
printf '%s\n' 'TRIAL_RESULT INPUTS_PRESENT'
