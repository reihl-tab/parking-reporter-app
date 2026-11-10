#!/usr/bin/env bash
# Regression test for scripts/stage-live-files.sh: reproduces the first-
# install device state (the app's files/ dir does not exist yet - the exact
# condition that killed live run 42755730613) using a mock `adb`, and asserts
# staging succeeds and the inputs land on the mock device.
set -euo pipefail

cd "$(dirname "$0")/.."

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# Mock device: two package data dirs, NEITHER has files/ (fresh install).
mkdir -p "$tmp/device/il.sidewalks.reporter" "$tmp/device/il.sidewalks.reporter.test"

cat > "$tmp/adb" <<'SH'
#!/usr/bin/env bash
# Mock adb: handles `adb shell "run-as PKG sh -c 'CMD'"` with stdin
# passthrough, executing CMD inside the mock package dir. Any other call
# fails loudly so the staging script cannot silently pass on a stub.
set -u
[ "${1:-}" = "shell" ] || { echo "mock adb: unsupported call: $*" >&2; exit 2; }
quoted=$2
pkg=$(printf '%s' "$quoted" | sed -n "s/^run-as \([^ ]*\) .*/\1/p")
inner=$(printf '%s' "$quoted" | sed -n "s/^run-as [^ ]* sh -c '\(.*\)'\$/\1/p")
[ -n "$pkg" ] && [ -n "$inner" ] || { echo "mock adb: cannot parse: $quoted" >&2; exit 2; }
cd "$MOCK_DEVICE/$pkg" || exit 1
exec sh -c "$inner"
SH
chmod +x "$tmp/adb"

export PATH="$tmp:$PATH"
export MOCK_DEVICE="$tmp/device"

printf 'fake photo bytes\n' > "$tmp/photo.jpg"
printf '{"plate":"00000000"}\n' > "$tmp/params.json"

scripts/stage-live-files.sh "$tmp/photo.jpg" "$tmp/params.json" | grep -q 'preflight: staged photo and params verified on device'
test -s "$tmp/device/il.sidewalks.reporter.test/files/live-photo.jpg"
test -s "$tmp/device/il.sidewalks.reporter/files/live-params.json"
cmp "$tmp/photo.jpg" "$tmp/device/il.sidewalks.reporter.test/files/live-photo.jpg"

echo 'staging regression test passed: first-install (no files/ dir) handled'
