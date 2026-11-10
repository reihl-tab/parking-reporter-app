#!/usr/bin/env bash
# No public stdout/stderr containing sample contents, paths, OCR predictions or logs.
set -euo pipefail
umask 077
work=/tmp/ocr-benchmark
mkdir -p "$work/input" "$work/output"
# URL and key are dedicated OCR_BENCH_* secrets. Never reads any TRIAL_* or J106_* secret.
curl --fail --silent --show-error --location "$OCR_BENCH_INPUT_URL" -o "$work/input.enc"
test "$(sha256sum "$work/input.enc" | cut -d' ' -f1)" = "$OCR_BENCH_INPUT_SHA256" || { echo 'input integrity check failed'; exit 1; }
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in "$work/input.enc" -out "$work/input.tar" -pass env:OCR_BENCH_KEY
# Archive contents are owner-built but validate paths before extraction regardless.
python3 - <<'PY'
import tarfile,re
with tarfile.open('/tmp/ocr-benchmark/input.tar') as archive:
    entries=archive.getmembers()
    assert 2 <= len(entries) <= 31
    assert all(x.isfile() and (x.name=='manifest.json' or re.fullmatch(r'sample-[0-9]+\.jpg',x.name)) for x in entries)
    archive.extractall('/tmp/ocr-benchmark/input',members=entries,filter='data')
PY
adb install -r app/build/outputs/apk/debug/app-debug.apk > "$work/output/install.txt" 2>&1
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >> "$work/output/install.txt" 2>&1
# Do not allow ML Kit telemetry or image transport during the benchmark.
adb shell svc wifi disable
adb shell svc data disable
adb shell run-as il.sidewalks.reporter mkdir -p files/ocr-benchmark
for f in "$work/input"/*; do
    adb shell "run-as il.sidewalks.reporter sh -c 'cat > files/ocr-benchmark/$(basename "$f")'" < "$f"
done
adb logcat -c
set +e
adb shell am instrument -w -r -e class il.sidewalks.reporter.PrivateRealPhotoOcrTest il.sidewalks.reporter.test/androidx.test.runner.AndroidJUnitRunner > "$work/output/instrumentation.txt" 2>&1
rc=$?
adb shell run-as il.sidewalks.reporter cat files/ocr-private-results.json > "$work/output/results.json" 2> "$work/output/results.stderr"
adb logcat -d -v threadtime > "$work/output/logcat.txt" 2>&1
set -e
# Guard skipped/missing input, partial output or test failure. Do not print details.
python3 - <<'PY' || rc=1
import json
from pathlib import Path
expected=json.loads(Path('/tmp/ocr-benchmark/input/manifest.json').read_text())['cases']
rows=json.loads(Path('/tmp/ocr-benchmark/output/results.json').read_text())
assert len(rows)==len(expected) and rows
text=Path('/tmp/ocr-benchmark/output/instrumentation.txt').read_text()
assert 'OK (1 test)' in text and 'FAILURES' not in text
PY
# Encrypt EVERYTHING collected before upload, including failures/logcat. No screenshots.
tar -C "$work/output" -cf "$work/output.tar" .
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -in "$work/output.tar" -out "$work/results.tar.enc" -pass env:OCR_BENCH_KEY
adb shell pm clear il.sidewalks.reporter > /dev/null 2>&1 || true
rm -rf "$work/input" "$work/output" "$work/input.tar" "$work/output.tar"
exit "$rc"
