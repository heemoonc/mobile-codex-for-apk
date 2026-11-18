from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / 'tools/run_linux_checks.py'

class LinuxChecksTest(unittest.TestCase):
    def invoke(self, suite, fail_at=0):
        # Process execution is an external boundary; inspect the real launcher
        # command and status handling without requiring PRoot on every test host.
        import contextlib
        import importlib.util
        import io
        from types import SimpleNamespace
        from unittest.mock import patch
        spec=importlib.util.spec_from_file_location('linux_checks',RUNNER)
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        calls=[]
        def execute(argv, **kwargs):
            self.assertEqual(kwargs.get('cwd'), ROOT)
            self.assertNotIn('shell',kwargs)
            calls.append(argv[1:])
            return SimpleNamespace(returncode=7 if len(calls)==fail_at else 0)
        error=io.StringIO()
        with patch.object(sys,'argv',[str(RUNNER),'--suite',suite]), patch.object(module.subprocess,'run',execute), contextlib.redirect_stderr(error):
            try:code=module.main()
            except SystemExit as e:code=e.code
        return SimpleNamespace(returncode=code,stderr=error.getvalue()),calls

    def test_unit_launch_uses_isolated_linux_runtime_without_shell(self):
        r,calls=self.invoke('unit')
        self.assertEqual(r.returncode,0,r.stderr)
        self.assertEqual(len(calls),2)
        self.assertEqual(calls[0][:2],['--','/usr/bin/env'])
        self.assertIn('NODE_PATH=/workspace/.runtime-cache/linux-checks/node_modules',calls[0])
        self.assertIn('/workspace/.runtime-cache/linux-node/node-v24.22.3-linux-arm64/bin/node',calls[1])
        self.assertIn('--test',calls[1])
        self.assertIn('tests/browser-runner.test.cjs',calls[1])
        self.assertNotIn('/bin/bash',calls[1])

    def test_layout_runs_real_suite_entrypoint(self):
        r,calls=self.invoke('layout')
        self.assertEqual(r.returncode,0,r.stderr)
        self.assertEqual(calls[1][-1],'tools/verify_browser_suites.cjs')
        self.assertIn('PLAYWRIGHT_BROWSERS_PATH=/workspace/.runtime-cache/playwright',calls[0])

    def test_preflight_failure_never_runs_suite(self):
        r,calls=self.invoke('unit',1)
        self.assertEqual(r.returncode,7)
        self.assertEqual(len(calls),1)
        self.assertIn('docs/android-development-checks.ko.md',r.stderr)

    def test_suite_failure_propagates(self):
        r,calls=self.invoke('layout',2)
        self.assertEqual(r.returncode,7)
        self.assertEqual(len(calls),2)

    def test_unknown_suite_is_rejected_before_process_launch(self):
        r,calls=self.invoke('unit;echo unsafe')
        self.assertEqual(r.returncode,2)
        self.assertIn('invalid choice',r.stderr)
        self.assertEqual(calls,[])

if __name__=='__main__':unittest.main()
