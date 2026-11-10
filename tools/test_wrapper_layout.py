import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('layout', Path(__file__).with_name('check_repo_layout.py'))
layout = importlib.util.module_from_spec(spec)
spec.loader.exec_module(layout)

class WrapperLayoutTest(unittest.TestCase):
    def test_only_exact_wrapper_path_is_allowed(self):
        contract = {'required': [], 'root': {'allow': []}, 'directories': {'gradle': {'allow': ['*.properties'], 'allow_paths': ['gradle/wrapper/gradle-wrapper.jar']}}}
        paths = ['gradle/wrapper/gradle-wrapper.jar', 'gradle/other/gradle-wrapper.jar', 'gradle/wrapper/untrusted.jar']
        with patch.object(layout, 'load_contract', return_value=contract), patch.object(layout, 'tracked_files', return_value=paths):
            findings = layout.check(Path('.'))
        self.assertEqual(2, len(findings))
        self.assertIn('gradle/other/gradle-wrapper.jar', findings[0])
        self.assertIn('gradle/wrapper/untrusted.jar', findings[1])

if __name__ == '__main__': unittest.main()
