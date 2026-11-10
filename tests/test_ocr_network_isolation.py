import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

class NetworkIsolationTest(unittest.TestCase):
    def probe(self, codes, transport=0, error='nc: connect: Network is unreachable', count=None):
        with tempfile.TemporaryDirectory() as directory:
            error_file = pathlib.Path(directory, "probe-error.txt")
            error_file.write_text(error)
            script = '''set -euo pipefail
source "$1"
codes=($3)
transport=$4
error_file=$5
index=0
sleeps=0
trap 'printf "%s %s" "$index" "$sleeps" > "$2/counts"' EXIT
adb() {
    if [[ "$*" == "get-state" ]]; then return "$transport"; fi
    if [[ "$*" == *toybox* ]]; then
        result=${codes[$index]:-0}
        index=$((index + 1))
        if [[ "$result" != 0 ]]; then cat "$error_file" >&2; fi
        return "$result"
    fi
    return 0
}
sleep() { [[ "$*" == 2 ]] || exit 98; sleeps=$((sleeps + 1)); }
ocr_network_isolation "$2"
status=$?
printf '%s %s' "$index" "$sleeps" > "$2/counts"
exit "$status"
'''
            result = subprocess.run(['bash', '-c', script, 'test', str(ROOT/'scripts/ocr-network-isolation.sh'), directory, ' '.join(map(str, codes)), str(transport), str(error_file)], capture_output=True)
            if error.strip():
                self.assertNotIn(error.encode(), result.stdout + result.stderr)
            if count is not None:
                self.assertEqual(f'{count + 1} {count}', pathlib.Path(directory, 'counts').read_text())
            return result.returncode
    def test_transport_failure_is_not_offline(self):
        self.assertNotEqual(0, self.probe([0, 1, 1, 1], transport=1))
    def test_positive_probe_must_work(self):
        for code in (1, 127):
            self.assertNotEqual(0, self.probe([code]))
    def test_transition_can_settle_to_three_negative_probes(self):
        self.assertEqual(0, self.probe([0, 0, 0, 1, 1, 1], count=5))
    def test_permanently_online_fails_closed_at_ten_probes(self):
        self.assertNotEqual(0, self.probe([0]*20, count=10))
    def test_one_negative_does_not_pass(self):
        self.assertNotEqual(0, self.probe([0, 1]+[0]*9))
    def test_online_probe_resets_negative_streak(self):
        self.assertNotEqual(0, self.probe([0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1]))
        self.assertEqual(0, self.probe([0, 1, 1, 0, 1, 1, 1], count=6))
    def test_non_connect_exit_codes_refuse(self):
        for code in (2, 127, 255):
            with self.subTest(code=code):
                self.assertNotEqual(0, self.probe([0, code, code, code]))
    def test_exit_one_requires_only_known_no_route_output(self):
        for error in ('error: device offline', 'nc: Timeout', '', 'unknown', 'nc: connect: Network is unreachable\nerror: closed'):
            with self.subTest(error=error):
                self.assertNotEqual(0, self.probe([0, 1, 1, 1], error=error))
    def test_large_extra_output_refuses_under_pipefail(self):
        error = 'nc: connect: Network is unreachable\n' + ('unexpected diagnostic line\n' * 2100000)
        self.assertNotEqual(0, self.probe([0, 1, 1, 1], error=error))
    def test_cap_boundary(self):
        self.assertEqual(0, self.probe([0]+[0]*7+[1]*3, count=10))
        self.assertNotEqual(0, self.probe([0]+[0]*8+[1]*3, count=10))

if __name__ == '__main__': unittest.main()
