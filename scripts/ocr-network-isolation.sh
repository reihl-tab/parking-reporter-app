#!/usr/bin/env bash
# Sourced only by the private benchmark. Probe output is private diagnostic data.
ocr_network_isolation() {
    local output=$1 attempt probe_status offline=0
    if ! adb shell 'echo | toybox nc -w 3 1.1.1.1 443' > "$output/online-control.txt" 2>&1; then
        echo 'network positive control failed - refusing OCR'
        return 1
    fi
    adb shell svc wifi disable || return 1
    adb shell svc data disable || return 1
    # Network teardown is asynchronous. Require three consecutive no-route results from
    # the same working TCP probe, with a bounded wait, before staging photos.
    for attempt in $(seq 1 10); do
        sleep 2
        if ! adb get-state > "$output/device-state-$attempt.txt" 2>&1; then
            echo 'device transport unavailable - refusing OCR'
            return 1
        fi
        if adb shell 'echo | toybox nc -w 3 1.1.1.1 443' > "$output/offline-probe-$attempt.txt" 2>&1; then
            offline=0
        else
            probe_status=$?
            # Only the known toybox ENETUNREACH result proves no route. ADB,
            # missing-tool, timeout and unknown errors must never count offline.
            # Inspect privately; never echo the captured diagnostic contents.
            if [ "$probe_status" -ne 1 ] ||
               ! grep -qxF 'nc: connect: Network is unreachable' "$output/offline-probe-$attempt.txt"; then
                echo 'network probe unrecognised - refusing OCR'
                return 1
            fi
            if grep -qvxF -e 'nc: connect: Network is unreachable' -e '' "$output/offline-probe-$attempt.txt"; then
                echo 'network probe unrecognised - refusing OCR'
                return 1
            fi
            offline=$((offline + 1))
            if [ "$offline" -ge 3 ]; then return 0; fi
        fi
    done
    adb shell dumpsys connectivity > "$output/connectivity.txt" 2>&1 || true
    echo 'network isolation check failed - refusing OCR'
    return 1
}
