import subprocess
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
P=Path(__file__).resolve().parents[2]/'scripts/security/scorecard_guard.py'
spec=importlib.util.spec_from_file_location('scorecard_guard',P)
g=importlib.util.module_from_spec(spec);sys.modules[spec.name]=g;spec.loader.exec_module(g)
SHA='a'*40
class GuardTests(unittest.TestCase):
    def rules(self,text,path='.github/workflows/test.yml'):
        return {x.rule for x in g.scan_text(path,text)}
    def test_invalid_baseline_cli(self):
        for content in ['{invalid', '[]', 'null', '{"version":1,"findings":[{}]}']:
            with self.subTest(content=content), tempfile.TemporaryDirectory() as d:
                root=Path(d);(root/'.github').mkdir()
                (root/'.github/scorecard-baseline.json').write_text(content)
                result=subprocess.run([sys.executable,str(P),'--root',d],capture_output=True,text=True)
                self.assertEqual(result.returncode,2)
                self.assertIn('Invalid baseline:',result.stderr)
                self.assertNotIn('Traceback',result.stderr)
    def test_unreadable_baseline_cli(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/'.github/scorecard-baseline.json').mkdir(parents=True)
            result=subprocess.run([sys.executable,str(P),'--root',d],capture_output=True,text=True)
            self.assertEqual(result.returncode,2)
            self.assertIn('Invalid baseline:',result.stderr)
            self.assertNotIn('Traceback',result.stderr)
    def test_pip_global_options(self):
        for command in ['python -m pip --disable-pip-version-check install x','pip --no-cache-dir install x']:
            self.assertIn('unhashed-pip-install',self.rules('permissions: {}\nrun: '+command))
    def test_pipx(self):self.assertIn('unhashed-pipx-install',self.rules('permissions: {}\nrun: pipx install x'))
    def test_privileged_pr_checkout(self):self.assertIn('privileged-pr-checkout',self.rules('on:\n  pull_request_target:\npermissions: {}\njobs:\n  t:\n    steps:\n      - uses: actions/checkout@'+SHA))
    def test_commented_action(self):self.assertFalse(self.rules('permissions: {}\n# uses: actions/checkout@v4'))
    def test_missing_permissions(self):self.assertIn('top-level-permissions',self.rules('jobs:\n'))
    def test_safe_permissions(self):self.assertFalse(self.rules('permissions:\n  contents: read\njobs:\n'))
    def test_empty_permissions(self):self.assertFalse(self.rules('permissions: {}\njobs:\n'))
    def test_write_all(self):self.assertIn('permissions-shape',self.rules('permissions: write-all\n'))
    def test_inline_write(self):self.assertIn('permissions-shape',self.rules('permissions: {contents: write}\n'))
    def test_top_write(self):self.assertIn('top-level-write',self.rules('permissions:\n  contents: write\n'))
    def test_job_write(self):
        fs=g.scan_text('.github/workflows/t.yml','permissions: {}\njobs:\n  publish:\n    permissions:\n      contents: write\n')
        self.assertEqual([(x.rule,x.scope) for x in fs],[('job-write','publish')])
    def test_action_tag(self):self.assertIn('unpinned-action',self.rules('permissions: {}\njobs:\n  t:\n    steps:\n      - uses: actions/checkout@v4\n'))
    def test_action_sha(self):self.assertFalse(self.rules(f'permissions: {{}}\njobs:\n  t:\n    steps:\n      - uses: actions/checkout@{SHA}\n'))
    def test_local_action(self):self.assertFalse(self.rules('permissions: {}\njobs:\n  t:\n    steps:\n      - uses: ./local\n'))
    def test_reusable_workflow(self):self.assertIn('unpinned-action',self.rules('permissions: {}\njobs:\n  t:\n    uses: org/repo/.github/workflows/x.yml@main\n'))
    def test_docker_action(self):self.assertIn('unpinned-action',self.rules('permissions: {}\njobs:\n  t:\n    steps:\n      - uses: docker://alpine:3\n'))
    def test_external_from(self):self.assertIn('unpinned-container',self.rules('FROM python:3.13-slim','Dockerfile'))
    def test_digest_from(self):self.assertFalse(self.rules('FROM python:3.13-slim@sha256:'+'a'*64,'Dockerfile'))
    def test_local_stages(self):self.assertFalse(self.rules('FROM scratch AS base\nFROM base AS api\nFROM api AS http','Dockerfile'))
    def test_future_stage_is_external(self):self.assertIn('unpinned-container',self.rules('FROM later\nFROM scratch AS later','Dockerfile'))
    def test_platform_from(self):self.assertIn('unpinned-container',self.rules('FROM --platform=$BUILDPLATFORM python:3.13','Dockerfile'))
    def test_pip_exact_version_not_hash(self):self.assertIn('unhashed-pip-install',self.rules('run: pip install ruff==0.16.9'))
    def test_local_extras_resolve(self):self.assertIn('unhashed-pip-install',self.rules('run: python -m pip install ".[api]"'))
    def test_offline_install(self):self.assertFalse(self.rules('RUN pip install --no-deps --no-build-isolation /wheels/x.whl','Dockerfile'))
    def test_external_no_deps_still_network(self):self.assertIn('unhashed-pip-install',self.rules('RUN pip install --no-deps --no-build-isolation requests','Dockerfile'))
    def test_hashed_install(self):self.assertFalse(self.rules('permissions: {}\nrun: pip install --require-hashes -r requirements-ci.txt'))
    def test_multiline_pip(self):self.assertIn('unhashed-pip-install',self.rules('RUN pip install \\\n   coverage','Dockerfile'))
    def test_changed_baseline_is_not_allowed(self):
        f=g.Finding('Dockerfile','unpinned-container','docker','FROM x:1')
        b={'version':1,'findings':[dict(g.asdict(f),issue='https://example.invalid/1',reason='existing') ]}
        self.assertEqual(g.evaluate([f],b),([],[]))
        n=g.Finding('Dockerfile','unpinned-container','docker','FROM x:2')
        new,stale=g.evaluate([n],b);self.assertEqual(new,[n]);self.assertEqual(len(stale),1)
    def test_baseline_requires_reason(self):
        f=g.Finding('x','r','s','e')
        with self.assertRaises(ValueError):g.evaluate([f],{'version':1,'findings':[g.asdict(f)]})
    def test_hashfile_and_missing_file(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);(p/'.github/workflows').mkdir(parents=True)
            (p/'.github/workflows/test.yml').write_text('permissions: {}\nrun: pip install --require-hashes -r req.txt\n')
            self.assertIn('requirements-missing',{f.rule for f in g.scan(p)})
            (p/'req.txt').write_text('x==1 --hash=sha256:'+'a'*64+'\n')
            self.assertFalse(g.scan(p))
            (p/'req.txt').write_text('x>=1\n')
            self.assertIn('unhashed-requirement',{f.rule for f in g.scan(p)})
    def test_multiple_requirement_files_all_scanned(self):
        # CodeRabbit #124: the hash check captured only the LAST -r on an
        # install line, so an unhashed first file escaped.
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);(p/'.github/workflows').mkdir(parents=True)
            (p/'.github/workflows/test.yml').write_text('permissions: {}\nrun: pip install --require-hashes -r first.txt --requirement second.txt\n')
            (p/'first.txt').write_text('unhashedpkg>=1\n')
            (p/'second.txt').write_text('x==1 --hash=sha256:'+'a'*64+'\n')
            findings=g.scan(p)
            self.assertIn('first.txt',{f.path for f in findings if f.rule=='unhashed-requirement'})
            self.assertNotIn('second.txt',{f.path for f in findings if f.rule=='unhashed-requirement'})
    def test_later_install_commands(self):
        self.assertIn('unhashed-pip-install',self.rules('permissions: {}\nrun: pip install --require-hashes -r req.txt && pip3.13 install x'))
        self.assertIn('unhashed-pipx-install',self.rules('permissions: {}\nrun: pip install --require-hashes -r req.txt && pipx install x'))
    def test_requirement_parent_symlink_confined(self):
        with tempfile.TemporaryDirectory() as d, tempfile.TemporaryDirectory() as outside:
            root=Path(d);(root/'.github/workflows').mkdir(parents=True)
            (root/'.github/workflows/test.yml').write_text('permissions: {}\nrun: pip install --require-hashes -r linked/req.txt\n')
            (Path(outside)/'req.txt').write_text('x>=1\n')
            (root/'linked').symlink_to(outside,target_is_directory=True)
            findings=g.scan(root)
            self.assertIn('requirements-path',{f.rule for f in findings})
            self.assertNotIn('unhashed-requirement',{f.rule for f in findings})
    def test_adversarial_pip_runtime_bound(self):
        # A subprocess deadline also catches catastrophic backtracking regressions.
        code='import runpy; g=runpy.run_path('+repr(str(P))+'); line="permissions: {}\\nrun: pip "+"--log --- "*20000; g["scan_text"](".github/workflows/t.yml",line)'
        subprocess.run([sys.executable,'-c',code],check=True,timeout=3,capture_output=True)
    def test_absolute_venv_pip_requirement_contents(self):
        for pip in ['/tmp/venv/bin/pip', '/tmp/venv/bin/pip3', '/tmp/venv/bin/pip3.13']:
            for command in [pip+' install --require-hashes -r requirements.txt', 'bash -c "'+pip+' install --require-hashes -r requirements.txt"']:
                with self.subTest(command=command), tempfile.TemporaryDirectory() as d:
                    root=Path(d);(root/'.github/workflows').mkdir(parents=True)
                    (root/'.github/workflows/test.yml').write_text('permissions: {}\nrun: '+command+'\n')
                    (root/'requirements.txt').write_text('x>=1\n')
                    self.assertIn('unhashed-requirement',{f.rule for f in g.scan(root)})
    def test_nested_shell_requirements(self):
        for shell in ['bash','sh']:
            for quote in ['"', "'"]:
                with self.subTest(shell=shell,quote=quote), tempfile.TemporaryDirectory() as d:
                    root=Path(d);(root/'.github/workflows').mkdir(parents=True)
                    (root/'.github/workflows/test.yml').write_text('permissions: {}\nrun: '+shell+' -c '+quote+'pip install --require-hashes -r reqs.txt'+quote+'\n')
                    (root/'reqs.txt').write_text('x>=1\n')
                    self.assertIn('unhashed-requirement',{f.rule for f in g.scan(root)})
    def test_separate_value_global_option(self):
        self.assertIn('unhashed-pip-install',self.rules('permissions: {}\nrun: pip --retries 5 install x'))
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/'.github/workflows').mkdir(parents=True)
            (root/'.github/workflows/test.yml').write_text('permissions: {}\nrun: pip --retries 5 install --require-hashes -r reqs.txt\n')
            (root/'reqs.txt').write_text('x>=1\n')
            self.assertIn('unhashed-requirement',{f.rule for f in g.scan(root)})
    def test_duplicate_if(self):
        self.assertIn('duplicate-if-key',self.rules('permissions: {}\njobs:\n  summary:\n    if: true\n    name: Summary\n    if: always()\n'))
        self.assertNotIn('duplicate-if-key',self.rules('permissions: {}\njobs:\n  a:\n    if: true\n  b:\n    if: false\n'))
    def test_separate_step_if_keys(self):
        self.assertNotIn('duplicate-if-key',self.rules('permissions: {}\njobs:\n  a:\n    steps:\n      - name: One\n        if: true\n      - name: Two\n        if: true\n'))
    def test_commented_requirement_commands(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/'.github/workflows').mkdir(parents=True)
            (root/'.github/workflows/test.yml').write_text('permissions: {}\n# pip install --require-hashes -rmissing.txt\nrun: echo ok # pip install --require-hashes -ralso-missing.txt\n')
            self.assertFalse(g.scan(root))
    def test_quoted_and_plus_requirement_paths(self):
        for flag in ['-r "req+ci.txt"', '--requirement="req+ci.txt"', '-rreq+ci.txt', '-r "req ci.txt"']:
            with self.subTest(flag=flag), tempfile.TemporaryDirectory() as d:
                root=Path(d);(root/'.github/workflows').mkdir(parents=True)
                (root/'.github/workflows/test.yml').write_text('permissions: {}\nrun: pip install --require-hashes '+flag+'\n')
                filename='req ci.txt' if 'req ci' in flag else 'req+ci.txt'
                self.assertIn('requirements-missing',{f.rule for f in g.scan(root)})
                (root/filename).write_text('x>=1\n')
                self.assertIn(filename,{f.path for f in g.scan(root) if f.rule=='unhashed-requirement'})
                (root/filename).write_text('x==1 --hash=sha256:'+'a'*64+'\n')
                self.assertFalse(g.scan(root))
    def test_requirement_hashes_with_pip_global_options(self):
        for command in ['python -m pip --disable-pip-version-check install', 'pip --no-cache-dir install', 'python3.13 -m pip --no-cache-dir install']:
            with self.subTest(command=command), tempfile.TemporaryDirectory() as d:
                root=Path(d);(root/'.github/workflows').mkdir(parents=True)
                (root/'.github/workflows/test.yml').write_text('permissions: {}\nrun: '+command+' --require-hashes -rfirst.txt --requirement=second.txt\n')
                (root/'first.txt').write_text('x>=1\n')
                (root/'second.txt').write_text('y==1 --hash=sha256:'+'a'*64+'\n')
                self.assertEqual({f.path for f in g.scan(root) if f.rule=='unhashed-requirement'}, {'first.txt'})
    def test_multiple_concatenated_requirement_files(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/'.github/workflows').mkdir(parents=True)
            (root/'.github/workflows/test.yml').write_text('permissions: {}\nrun: pip install --require-hashes -rfirst.txt --requirement=second.txt && pip3 install --require-hashes -rthird.txt\n')
            for name in ['first.txt','second.txt','third.txt']:(root/name).write_text('x>=1\n')
            self.assertEqual({f.path for f in g.scan(root) if f.rule=='unhashed-requirement'}, {'first.txt','second.txt','third.txt'})
    def test_concatenated_requirement_file(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/'.github/workflows').mkdir(parents=True)
            (root/'.github/workflows/test.yml').write_text('permissions: {}\nrun: pip install --require-hashes -rreq.txt\n')
            self.assertIn('requirements-missing',{f.rule for f in g.scan(root)})
            (root/'req.txt').write_text('x==1\n')
            self.assertIn('unhashed-requirement',{f.rule for f in g.scan(root)})
            (root/'req.txt').write_text('x==1 --hash=sha256:'+'a'*64+'\n')
            self.assertFalse(g.scan(root))
ROOT=P.parents[2]
LOCK,SOURCE,COMMAND=g.LOCKS[0]
H='--hash=sha256:'
def lockfile(entries,command=COMMAND):
    return '# This file was autogenerated by uv via the following command:\n#    '+command+'\n'+entries
GOOD=('alpha==1.0 \\\n    '+H+'a'*64+' \\\n    '+H+'b'*64+'\n    # via -r requirements.txt\n'
      'beta==2.0 ; python_version >= "3.12" \\\n    '+H+'c'*64+'\n    # via alpha\n')
class LockTests(unittest.TestCase):
    """Lock integrity for the hash-locked tooling requirements (#70); synthetic data only."""
    def lock_rules(self,source='alpha==1.0\n',lock=GOOD,command=COMMAND):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            if source is not None:(root/SOURCE).write_text(source)
            if lock is not None:(root/LOCK).write_text(lockfile(lock,command))
            return [(f.rule,f.path) for f in g.scan(root)]
    def test_lock_sample_passes(self):self.assertEqual(self.lock_rules(),[])
    def test_lock_missing(self):self.assertIn(('lock-missing',LOCK),self.lock_rules(lock=None))
    def test_lock_without_input_is_not_required(self):self.assertEqual(self.lock_rules(source=None,lock=None),[])
    def test_lock_command_header(self):
        self.assertIn(('lock-command',LOCK),self.lock_rules(command=COMMAND.replace('3.12','3.11')))
        self.assertIn(('lock-command',LOCK),self.lock_rules(command=COMMAND.replace(' --generate-hashes','')))
    def test_lock_entry_edited_without_hash(self):
        self.assertIn(('lock-unhashed',LOCK),self.lock_rules(lock=GOOD.replace(' \\\n    '+H+'c'*64,'')))
    def test_lock_malformed_hash(self):
        for bad in ['--hash=sha256:'+'c'*63,'--hash=md5:'+'c'*32,'--hash=sha256:'+'C'*64]:
            with self.subTest(bad=bad):self.assertIn(('lock-unhashed',LOCK),self.lock_rules(lock=GOOD.replace(H+'c'*64,bad)))
    def test_lock_range_entry(self):
        self.assertIn(('lock-unhashed',LOCK),self.lock_rules(lock=GOOD.replace('beta==2.0','beta>=2.0')))
    def test_lock_input_unpinned(self):
        for source in ['alpha>=1.0\n','alpha\n','alpha==1.0\n-e .\n','alpha==1.0\n--index-url https://example.invalid/simple\n']:
            with self.subTest(source=source):self.assertIn(('lock-input-unpinned',SOURCE),self.lock_rules(source=source))
    def test_lock_input_bumped_without_relock(self):
        self.assertIn(('lock-input-mismatch',LOCK),self.lock_rules(source='alpha==1.1\n'))
    def test_lock_input_added_without_relock(self):
        self.assertIn(('lock-input-mismatch',LOCK),self.lock_rules(source='alpha==1.0\ngamma==3.0\n'))
    def test_lock_top_level_not_in_input(self):
        self.assertIn(('lock-input-mismatch',LOCK),self.lock_rules(lock=GOOD.replace('# via alpha','# via -r requirements.txt')))
    def test_lock_multiline_via(self):
        lock=GOOD.replace('# via -r requirements.txt','# via\n    #   -r requirements.txt\n    #   beta')
        self.assertEqual(self.lock_rules(lock=lock),[])
        self.assertIn(('lock-input-mismatch',LOCK),self.lock_rules(source='alpha==1.1\n',lock=lock))
    def test_lock_name_normalised(self):self.assertEqual(self.lock_rules(source='Alpha==1.0\n'),[])
    def test_repository_lock_passes(self):self.assertEqual(g.check_lock(ROOT,LOCK,SOURCE,COMMAND),[])
    def test_pending_dependency_baseline_names_exact_workflow_lines(self):
        # The dependency entries of the baseline are the pending allowlist for the
        # workflow install not yet on the lock (#70). They must equal the current
        # findings, and each workflow entry names exactly one install line. The
        # maintainer's workflow change deletes them.
        import json,shlex
        base=json.loads((ROOT/'.github/scorecard-baseline.json').read_text())
        pending={(e['path'],e['rule'],e['evidence']) for e in base['findings'] if e['scope']=='dependencies'}
        found={(f.path,f.rule,f.evidence) for f in g.scan(ROOT) if f.scope=='dependencies'}
        self.assertEqual(pending,found)
        for path,rule,evidence in pending:
            if not path.startswith('.github/workflows/'):continue
            lines=[n for n,l in enumerate((ROOT/path).read_text().splitlines(),1)
                   if any(shlex.join(c)==evidence for _,_,c in g.install_commands(l))]
            self.assertEqual(len(lines),1,(path,evidence,lines))
class DocsAndScriptsTests(unittest.TestCase):
    def scan_file(self,name,text,extra=None):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/name).parent.mkdir(parents=True,exist_ok=True);(root/name).write_text(text)
            for k,v in (extra or {}).items():(root/k).write_text(v)
            return {(f.rule,f.path) for f in g.scan(root)}
    def test_markdown_fenced_install(self):
        for fence in ['```','```bash','~~~']:
            with self.subTest(fence=fence):
                doc='# Setup\n'+fence+'\npython -m pip -q install pillow\n'+fence[:3]+'\n'
                self.assertIn(('unhashed-pip-install','docs/SETUP.md'),self.scan_file('docs/SETUP.md',doc))
    def test_markdown_inline_install(self):
        self.assertIn(('unhashed-pip-install','CONTRIBUTING.md'),self.scan_file('CONTRIBUTING.md','Run `pip3 install cryptography==50.0.2` first.\n'))
        self.assertIn(('unhashed-pipx-install','CONTRIBUTING.md'),self.scan_file('CONTRIBUTING.md','Run `pipx install ruff`.\n'))
    def test_markdown_prose_ignored(self):
        self.assertEqual(self.scan_file('docs/X.md','No pip install or paid service is involved.\n'),set())
    def test_markdown_hashed_requirement_contents(self):
        doc='```\npip install --require-hashes -r req.txt\n```\n'
        self.assertIn(('unhashed-requirement','req.txt'),self.scan_file('docs/X.md',doc,{'req.txt':'x>=1\n'}))
        self.assertEqual(self.scan_file('docs/X.md',doc,{'req.txt':'x==1 '+H+'a'*64+'\n'}),set())
    def test_python_script_install(self):
        self.assertIn(('unhashed-pip-install','scripts/setup_tools.py'),self.scan_file('scripts/setup_tools.py','import os\nos.system("pip install pillow")\n'))
        self.assertIn(('unhashed-pip-install','tools/bootstrap.py'),self.scan_file('tools/bootstrap.py','CMD = "python3 -m pip install -r requirements.txt"\n'))
    def test_short_global_option(self):
        for command in ['pip -q install x','python3 -m pip -qq install x','pip -v --no-cache-dir install x']:
            with self.subTest(command=command):self.assertIn('unhashed-pip-install',{f.rule for f in g.scan_text('.github/workflows/t.yml','permissions: {}\nrun: '+command)})
    def test_python_argv_install(self):
        self.assertIn(('unhashed-pip-install','scripts/t.py'),self.scan_file('scripts/t.py','import subprocess,sys\nsubprocess.run([sys.executable,"-m","pip","install","pillow"])\n'))
        self.assertEqual(self.scan_file('scripts/t.py','import subprocess,sys\nsubprocess.run([sys.executable,"-m","pip","install","--require-hashes","-r","req.txt"])\n',{'req.txt':'x==1 '+H+'a'*64+'\n'}),set())
if __name__=='__main__':unittest.main()
