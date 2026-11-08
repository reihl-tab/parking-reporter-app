# Dependency locks (Python tooling)

The Python tooling (`scripts/synthetic_intake_prototype.py`, run by the
Python tooling smoke job) installs from a hash-locked file, so a changed or
substituted package fails the install instead of running (#70, Scorecard
alert #11). The Android app's Gradle dependencies are not covered here.

## Files

| File | Edited by | Holds |
|---|---|---|
| `requirements.txt` | hand (and Dependabot) | the direct tooling pins: `cryptography==50.0.2`, `pillow==13.0.0` |
| `requirements-lock.txt` | generated, never by hand | every package, transitive ones included, as an exact `==` pin with sha256 hashes |

The lock today pins `cffi==2.1.1`, `cryptography==50.0.2`, `pillow==13.0.0`
and `pycparser==3.12`, with the hashes of every published wheel and the
source archive. The `cffi` and `pycparser` lines carry the markers uv writes
for them (not installed on PyPy).

`requirements.txt` stays the input, at its old path, so Dependabot's `pip`
entry for `/` keeps reading it and the layout contract needs no new path.

## Python versions

The workflows run the runner's `python3` (3.12 on ubuntu-24.04) and set up no
other version, so the lock is generated for 3.12 and newer (`--universal`
writes platform markers, not one platform's wheels). The same lock installed
and passed the smoke tests on Python 3.11, 3.12, 3.13 and 3.14 on Linux
x86_64; every pin has a wheel for those versions.

## Install

```bash
python3 -m venv /tmp/venv
/tmp/venv/bin/pip install --require-hashes -r requirements-lock.txt
/tmp/venv/bin/python scripts/synthetic_intake_prototype.py -v
```

A package whose file does not match a recorded hash is refused by pip.

## Regenerate

After changing a pin in `requirements.txt` (by hand or in a Dependabot
pull request), regenerate the lock from the repository root with uv and
commit both files together:

```bash
uv pip compile --universal --generate-hashes --python-version 3.12 requirements.txt -o requirements-lock.txt
```

Keep the command exactly as written: the lock's header repeats it, and the
guard compares the two.

## Guard

`scripts/security/scorecard_guard.py` (the Scorecard regression guard; self-tests
in `tests/security/test_scorecard_guard.py`) checks, offline:

- install commands in workflows, Dockerfiles, scripts under `scripts/` and
  `tools/`, and the code blocks and inline code of tracked Markdown: an
  install from the network must read a requirements file with
  `--require-hashes`, and every file it reads must be all exact pins with
  sha256 hashes;
- the lock itself: it exists while `requirements.txt` exists, its header
  carries the generating command above, every entry is an exact pin with
  well-formed sha256 hashes, and its top-level entries (`# via -r
  requirements.txt`) are exactly the pins in `requirements.txt`, which must
  themselves be exact pins.

A transitive pin edited by hand without new hashes is refused by pip at
install time, which the smoke job runs. Findings that are still allowed are
listed in `.github/scorecard-baseline.json` with their issue; an entry that no
longer matches anything fails the guard.
