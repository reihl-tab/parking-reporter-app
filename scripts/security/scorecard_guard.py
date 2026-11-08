#!/usr/bin/env python3
"""Offline checks for selected Scorecard regressions. Not a YAML/SAST replacement."""
from __future__ import annotations
import argparse
from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path
import re
import shlex
import sys

@dataclass(frozen=True)
class Finding:
    path: str
    rule: str
    scope: str
    evidence: str
    @property
    def fingerprint(self):
        return hashlib.sha256(json.dumps(asdict(self), sort_keys=True).encode()).hexdigest()

# pip global options with separate values accepted by both scan passes.
PIP_VALUE_OPTIONS={'--retries','--timeout','--proxy','--cert','--client-cert','--cache-dir','--log','--python','--exists-action','--keyring-provider','--trusted-host'}
def pip_segments(line, depth=0):
    if depth>4:return []
    try:
        lexer=shlex.shlex(line,posix=True,punctuation_chars=';&|')
        lexer.whitespace_split=True
        tokens=list(lexer)
    except ValueError:return []
    segments=[[]]
    nested=[]
    for token in tokens:
        if token and all(c in ';&|' for c in token):segments.append([])
        else:
            segments[-1].append(token)
            if any(c.isspace() for c in token) and re.search(r'\bpip(?:3)?\b',token):nested.extend(pip_segments(token,depth+1))
    return segments+nested

def install_commands(line):
    for segment in pip_segments(line):
        for idx,token in enumerate(segment):
            name=Path(token).name
            if not (re.fullmatch(r'pip(?:3(?:\.\d+)?)?',name) or name=='pipx'):continue
            pos=idx+1
            # Skip global options before the subcommand, long (--isolated) and
            # short (-q, -vv) alike, so `pip -q install x` is still an install.
            while pos<len(segment) and segment[pos].startswith('-') and segment[pos]!='-':
                opt=segment[pos];pos+=1
                if opt in PIP_VALUE_OPTIONS and pos<len(segment):pos+=1
            if pos<len(segment) and segment[pos]=='install':
                yield name,segment[pos+1:],segment[idx:]

def refs_from_args(args):
    refs=[]
    for n,arg in enumerate(args):
        if arg in ('-r','--requirement') and n+1<len(args):refs.append(args[n+1])
        elif arg.startswith('--requirement='):refs.append(arg.split('=',1)[1])
        elif arg.startswith('-r') and arg!='-r':refs.append(arg[2:])
    return refs

def requirement_refs(line):
    return [ref for name,args,_ in install_commands(line) if name!='pipx' for ref in refs_from_args(args)]

def duplicate_if_keys(text):
    # Scope-aware check for duplicate job/step if keys, not a full YAML parser.
    scopes={};duplicates=[]
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith('#'):continue
        m=re.match(r'^(\s*)(?:-\s+)?([\w-]+):',line)
        if not m:continue
        indent=len(m[1]);key=m[2]
        if line.lstrip().startswith('- '):
            for level in list(scopes):
                if level>=indent:del scopes[level]
        for level in list(scopes):
            if level>indent:del scopes[level]
        if key=='if' and key in scopes.get(indent,set()):duplicates.append(line.strip())
        if line.rstrip().endswith(':') and key!='if':
            for level in list(scopes):
                if level>indent:del scopes[level]
        scopes.setdefault(indent,set()).add(key)
    return duplicates

def scan_text(path: str, text: str) -> list[Finding]:
    result = []
    if str(path).endswith(('.yml','.yaml')):
        result.extend(Finding(path,'duplicate-if-key','workflow',line) for line in duplicate_if_keys(text))
    def add(rule, scope, evidence):
        result.append(Finding(path, rule, scope, evidence.strip()))
    workflow = path.startswith('.github/workflows/') and path.endswith(('.yml', '.yaml'))
    lines = text.splitlines()
    if workflow:
        # The conservative supported shape uses block mappings with two-space jobs.
        # Unsupported permissions syntax fails, rather than silently passing.
        tops = [(i, line) for i, line in enumerate(lines) if re.match(r'^permissions\s*:', line)]
        if len(tops) != 1:
            add('top-level-permissions', 'workflow', 'expected exactly one top-level permissions mapping')
        job = 'workflow'
        if re.search(r'(?m)^\s*(?:on:.*)?\bpull_request_target\s*:', text) and re.search(r'(?m)^\s*-?\s*uses:\s*actions/checkout@', text):
            add('privileged-pr-checkout', 'workflow', 'pull_request_target combined with actions/checkout')
        for i, line in enumerate(lines):
            if line.lstrip().startswith('#'):continue
            m = re.match(r'^  ([A-Za-z_][\w-]*):\s*(?:#.*)?$', line)
            if m:
                # Only treat keys under jobs: as job identifiers.
                prior = next((s for s in reversed(lines[:i]) if s and not s.startswith((' ', '#'))), '')
                if prior.strip() == 'jobs:': job = m[1]
            if '\t' in line[:len(line) - len(line.lstrip())]:
                add('unsupported-yaml', job, 'tab indentation: ' + line)
            if re.match(r'^\s*permissions\s*:', line):
                indent = len(line) - len(line.lstrip())
                scope = 'workflow' if indent == 0 else job
                value = line.split(':', 1)[1].split('#', 1)[0].strip()
                if value not in ('', '{}', 'read-all'):
                    add('permissions-shape', scope, line)
                if indent not in (0, 4):
                    add('permissions-location', scope, line)
                for child in lines[i+1:]:
                    if not child.strip() or child.lstrip().startswith('#'):continue
                    child_indent = len(child) - len(child.lstrip())
                    if child_indent <= indent:break
                    p = re.fullmatch(r'\s*([\w-]+):\s*(read|write|none)\s*(?:#.*)?', child)
                    if not p:
                        add('permissions-shape', scope, child);continue
                    if p[2] == 'write':
                        add('top-level-write' if indent == 0 else 'job-write', scope, child)
            # Any action/reusable workflow reference, including docker actions.
            m = re.search(r'(?:^|\s)uses:\s*(\S+)', line)
            if m:
                ref = m[1].strip('\"\'')
                if ref.startswith('./'):continue
                if ref.startswith('docker://'):
                    good = re.search(r'@sha256:[a-fA-F0-9]{64}$', ref)
                else:good = re.search(r'@[a-fA-F0-9]{40}$', ref)
                if not good:add('unpinned-action', job, ref)
    if Path(path).name.startswith('Dockerfile') or Path(path).suffix == '.dockerfile':
        stages = set()
        logical = re.sub(r'\\\s*\n\s*', ' ', text)
        for line in logical.splitlines():
            m = re.match(r'^\s*FROM\s+(?:--platform=\S+\s+)?(\S+)(?:\s+AS\s+(\S+))?', line, re.I)
            if not m:continue
            image = m[1]
            if image.lower() not in stages and image != 'scratch' and not re.search(r'@sha256:[a-fA-F0-9]{64}$', image):
                add('unpinned-container', 'docker', line)
            if m[2]:stages.add(m[2].lower())
    # Tokenized command discovery avoids ambiguous repeated-option regexes.
    logical = re.sub(r'\\\s*\n\s*', ' ', text)
    for line in logical.splitlines():
        if line.lstrip().startswith('#'):continue
        for name,args,command in install_commands(line):
            evidence=shlex.join(command)
            if name=='pipx':
                add('unhashed-pipx-install','dependencies',evidence)
                continue
            offline=('--no-deps' in args and '--no-build-isolation' in args
                     and not any('http://' in t or 'https://' in t or 'git+' in t for t in args)
                     and any(t.startswith(('./','/wheels/')) for t in args))
            reqs=refs_from_args(args)
            hashed='--require-hashes' in args and bool(reqs)
            if not (offline or hashed):add('unhashed-pip-install','dependencies',evidence)
            for req in reqs:
                if '$' in req or Path(req).is_absolute() or '..' in Path(req).parts:
                    add('requirements-path','dependencies',req)
    return list(dict.fromkeys(result))

# Hash-locked requirement files (#70). Each lock is generated from a human-edited
# input of exact pins; the header command is part of the evidence.
LOCKS=(
    ('requirements-lock.txt','requirements.txt',
     'uv pip compile --universal --generate-hashes --python-version 3.12 requirements.txt -o requirements-lock.txt'),
)
PIN=re.compile(r'^([A-Za-z0-9][A-Za-z0-9_.-]*)(?:\[[\w,.-]+\])?==([A-Za-z0-9_.!+-]+)\s*(?:;[^#]*)?$')
HASH=re.compile(r'--hash=sha256:[a-f0-9]{64}')

def canonical(name):
    return re.sub(r'[-_.]+','-',name).lower()

def lock_entries(text):
    """(requirement, hashes, via) per entry of a uv/pip-compile style lock."""
    entries=[];via=None
    for line in re.sub(r'\\\s*\n\s*',' ',text).splitlines():
        stripped=line.strip()
        if not stripped:continue
        if stripped.startswith('#'):
            if entries and line.startswith((' ','\t')):
                note=stripped.lstrip('#').strip()
                if note.startswith('via'):via=entries[-1][2];note=note[3:].strip()
                if via is not None and note:via.append(note)
            continue
        via=None
        tokens=stripped.split(' --hash=')
        hashes=['--hash='+t.split()[0] for t in tokens[1:] if t.split()]
        entries.append((tokens[0].strip(),hashes,[]))
    return entries

def check_lock(root, lock, source, command):
    findings=[]
    def add(path,rule,evidence):findings.append(Finding(path,rule,'dependencies',evidence.strip()))
    src=root/source;target=root/lock
    if not src.is_file():return findings
    if src.is_symlink() or target.is_symlink():
        add(lock,'symlink-input','lock or input is a symlink');return findings
    if not target.is_file():
        add(lock,'lock-missing',source+' has no hash-locked '+lock);return findings
    inputs={}
    for line in src.read_text(encoding='utf-8').splitlines():
        line=line.split('#',1)[0].strip()
        if not line:continue
        m=PIN.match(line)
        if not m:add(source,'lock-input-unpinned',line);continue
        inputs[canonical(m[1])]=m[2]
    text=target.read_text(encoding='utf-8')
    header=[l[1:].strip() for l in text.splitlines() if l.startswith('#')]
    if command not in header:add(lock,'lock-command','header lacks: '+command)
    top={}
    for req,hashes,via in lock_entries(text):
        m=PIN.match(req)
        good=bool(m) and bool(hashes) and all(HASH.fullmatch(h) for h in hashes)
        if not good:add(lock,'lock-unhashed',req);continue
        if '-r '+source in via:top[canonical(m[1])]=m[2]
    for name in sorted(set(inputs)|set(top)):
        if inputs.get(name)!=top.get(name):
            add(lock,'lock-input-mismatch',f'{name}: {source} {inputs.get(name)} != lock {top.get(name)}')
    return findings

def markdown_code(text):
    """Code a reader copies from Markdown: fenced blocks and inline code spans."""
    out=[];fence=None
    for line in text.splitlines():
        m=re.match(r'^\s*(`{3,}|~{3,})',line)
        if fence:
            if m and m[1][0]==fence[0] and len(m[1])>=len(fence):fence=None
            else:out.append(line)
            continue
        if m:fence=m[1];continue
        out.extend(span for _,span in re.findall(r'(`+)(.+?)\1',line))
    return '\n'.join(out)

def python_strings(text):
    """String literals of a Python script, one per line; a list or tuple holding
    strings (an argv) is also joined, any other element read as one argument."""
    import ast
    try:tree=ast.parse(text)
    except (SyntaxError,ValueError):return text
    out=[]
    for node in ast.walk(tree):
        if isinstance(node,ast.Constant) and isinstance(node.value,str):out.extend(node.value.splitlines())
        elif isinstance(node,(ast.List,ast.Tuple)) and any(isinstance(e,ast.Constant) and isinstance(e.value,str) for e in node.elts):
            out.append(shlex.join(e.value if isinstance(e,ast.Constant) and isinstance(e.value,str) else '_' for e in node.elts))
    return '\n'.join(out)

def input_text(p):
    text=p.read_text(encoding='utf-8')
    if p.suffix=='.md':return markdown_code(text)
    if p.suffix=='.py':return python_strings(text)
    return text

def scan(root: Path) -> list[Finding]:
    files = set()
    for p in (root/'.github/workflows').glob('*'):
        if p.suffix in ('.yml','.yaml'):files.add(p)
    for p in root.rglob('*'):
        if any(x in ('.git','.venv','venv','node_modules','vendor','build','dist') for x in p.relative_to(root).parts):continue
        parts=p.relative_to(root).parts
        if p.is_file() and (p.name.startswith('Dockerfile') or p.suffix in ('.dockerfile','.md')
                            or (p.suffix in ('.sh','.py') and parts[0] in ('scripts','tools'))):
            files.add(p)
    findings=[]
    for p in sorted(files):
        if p.is_symlink():
            findings.append(Finding(p.relative_to(root).as_posix(),'symlink-input','filesystem','symlink not scanned'));continue
        findings.extend(scan_text(p.relative_to(root).as_posix(), input_text(p)))
    # Requirements hash verification, only for static -r files named by guarded inputs.
    for p in sorted(files):
        if p.is_symlink():continue
        text=re.sub(r'\\\s*\n\s*',' ',input_text(p))
        reqs=[]
        for line in text.splitlines():
            if line.lstrip().startswith('#'):continue
            reqs.extend(requirement_refs(line))
        for req in reqs:
            if '$' in req or Path(req).is_absolute() or '..' in Path(req).parts:
                findings.append(Finding(p.relative_to(root).as_posix(),'requirements-path','dependencies',req))
                continue
            target=root/req
            try:target.resolve().relative_to(root.resolve())
            except (ValueError,OSError):
                findings.append(Finding(p.relative_to(root).as_posix(),'requirements-path','dependencies',req))
                continue
            if not target.is_file() or target.is_symlink():
                findings.append(Finding(p.relative_to(root).as_posix(),'requirements-missing','dependencies',req));continue
            content=re.sub(r'\\\s*\n\s*',' ',target.read_text())
            for line in content.splitlines():
                line=line.strip()
                if not line or line.startswith('#'):continue
                if not re.match(r'^[A-Za-z0-9_.-]+(?:\[[\w,.-]+\])?==[^\s;]+',line) or not re.search(r'--hash=sha256:[a-f0-9]{64}(?:\s|$)',line):
                    findings.append(Finding(req,'unhashed-requirement','dependencies',line))
    for lock,source,command in LOCKS:findings.extend(check_lock(root,lock,source,command))
    return list(dict.fromkeys(findings))

def evaluate(findings, baseline):
    entries=baseline.get('findings',[])
    if baseline.get('version') != 1:raise ValueError('baseline version must be 1')
    allowed={}
    for e in entries:
        f=Finding(**{k:e[k] for k in ('path','rule','scope','evidence')})
        if not e.get('issue') or not e.get('reason'):raise ValueError('each baseline entry needs issue and reason')
        if f.fingerprint in allowed:raise ValueError('duplicate baseline entry')
        allowed[f.fingerprint]=e
    current={f.fingerprint:f for f in findings}
    return [f for key,f in current.items() if key not in allowed], [e for key,e in allowed.items() if key not in current]

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path('.'))
    parser.add_argument('--baseline',type=Path,default=Path('.github/scorecard-baseline.json'))
    parser.add_argument('--inventory',action='store_true',help='print findings; never writes/updates a baseline')
    args=parser.parse_args(argv)
    findings=scan(args.root.resolve())
    if args.inventory:
        print(json.dumps([asdict(f) for f in findings],indent=2));return bool(findings)
    path=args.root/args.baseline
    try:
        baseline=json.loads(path.read_text()) if path.exists() else {'version':1,'findings':[]}
        if not isinstance(baseline,dict):raise ValueError('baseline must be an object')
        new,stale=evaluate(findings,baseline)
    except (OSError,ValueError,KeyError,TypeError) as e:print('Invalid baseline:',e,file=sys.stderr);return 2
    for f in new:print(f'{f.path}: {f.rule} [{f.scope}]: {f.evidence}')
    for e in stale:print(f'Stale baseline entry: {e["path"]}: {e["rule"]}; remove after fixing')
    print(f'Scorecard guard: {len(findings)} findings, {len(new)} new, {len(stale)} stale baseline entries')
    return bool(new or stale)

if __name__=='__main__':sys.exit(main())
