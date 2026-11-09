"""Offline regression: absent input fails before any runner decrypt/network step."""
import os
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'scripts/trial_submit_preflight.sh'
REQUIRED = re.search(r'required=\((.*?)\)', SCRIPT.read_text(), re.S).group(1).split()


class TrialSubmitPreflightTest(unittest.TestCase):
    def run_script(self, overrides=None, absent=None):
        env = {'PATH': os.defpath, **{name: 'synthetic-sentinel-$-private' for name in REQUIRED}}
        env.update(overrides or {})
        if absent:
            env.pop(absent)
        result = subprocess.run(['bash', str(SCRIPT)], env=env, capture_output=True, text=True)
        self.assertEqual(result.stderr, '')
        self.assertNotIn('synthetic', result.stdout)
        return result

    def test_all_present_blank_plate_allowed(self):
        result = self.run_script({'TRIAL_PLATE': ''})
        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stdout, 'TRIAL_RESULT INPUTS_PRESENT\n')

    def test_each_required_input_absent_empty_or_whitespace_fails(self):
        for name in REQUIRED:
            for absent, overrides in [(name, {}), (None, {name: ''}), (None, {name: ' \n\t'})]:
                with self.subTest(name=name, absent=absent, overrides=bool(overrides)):
                    result = self.run_script(overrides, absent)
                    self.assertEqual(result.returncode, 1)
                    self.assertEqual(result.stdout, 'TRIAL_RESULT INPUT_MISSING\n')

    def test_workflow_preflight_precedes_decryption_and_engine(self):
        workflow = (ROOT / '.github/workflows/m0-engine-trial.yml').read_text()
        submit = workflow.split('  trial-submit:', 1)[1]
        guard = submit.index('run: bash scripts/trial_submit_preflight.sh')
        self.assertLess(guard, submit.index('- name: Decrypt trial photo'))
        self.assertLess(guard, submit.index('- name: Engine trial (single attempt, submit phase)'))
        preflight = submit[:guard].split('- name: Validate mandatory submit inputs')[1]
        for name in REQUIRED:
            self.assertIn(name + ':', preflight)
        self.assertIn('environment: m0-trial-submit', submit)
        self.assertIn('APPROVED_SHA256_INPUT', submit)


if __name__ == '__main__':
    unittest.main()
