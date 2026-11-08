# Release and wiki write grants (least privilege)

This page describes the `GITHUB_TOKEN` grants of every workflow as the
files on `main` set them today (read at `51d6d88`, v0.9.0). It documents;
it changes nothing. Every claim names `file:line` under
`.github/workflows/`. Scorecard's Token-Permissions alerts #9 and #14
point at the two publishing grants below (`release.yml:21` and
`wiki-sync.yml:33`); see issue #71.

## Summary

- Every workflow sets a read-only or empty top-level grant, except
  `manual-gpg-signing.yml`, whose top-level grant is `contents: write`
  (`manual-gpg-signing.yml:4-5`, tracked in #73).
- Four jobs can write: `release` (contents), wiki `sync` (contents),
  Scorecard `analysis` (security-events and id-token) and the manual
  `signing-test` (contents, inherited from the top level).
- No workflow grants a write on `packages`, `pull-requests`, `actions`,
  `issues` or `deployments`. Nothing publishes a package.
- Every "Run summary" job pins `contents: read` at job level.
- Every checkout sets `persist-credentials: false` except in the three
  jobs that push with git: `release` (`release.yml:23-26`), wiki `sync`
  (`wiki-sync.yml:36`) and `signing-test` (`manual-gpg-signing.yml:15-17`).
- Scorecard can still warn about the two job-level writes: it flags
  job-level writes too. Job scoping narrows the grant; it does not make
  the finding go away.

## Per-workflow grants

| Workflow | Triggers | Top-level | Job grants | Checkout credentials |
|---|---|---|---|---|
| `android.yml` | dispatch, push to main, PR to main, weekly cron (`:2-11`) | `contents: read` (`:12-13`) | `changes`, `build`, `emulator` inherit read; `summary` read (`:172-173`) | not persisted (`:26`, `:58`, `:105`, `:182`) |
| `city-readonly-checks.yml` | dispatch only (`:2-3`) | `contents: read` (`:4-5`) | `city-readonly-checks` inherits read; owner on main only (`:16`), behind an environment (`:17`) | not persisted (`:24`) |
| `layout.yml` | push to main, any PR, dispatch (`:3-7`) | `contents: read` (`:9-10`) | `layout` inherits read | not persisted (`:18`) |
| `manual-gpg-signing.yml` | dispatch only (`:2-3`) | `contents: write` (`:4-5`) | `signing-test` inherits write; owner on main only (`:11`); `summary` read (`:65-66`) | persisted in `signing-test` (`:15-17`, no `persist-credentials`); not persisted in `summary` (`:75`) |
| `release.yml` | push to main (`:3-5`) | `contents: read` (`:7-8`) | `release` `contents: write` (`:20-21`); `summary` read (`:55-56`) | persisted in `release` (`:23-26`); not persisted in `summary` (`:65`) |
| `repository-checks.yml` | dispatch, push to main, PR to main (`:3-8`) | `contents: read` (`:9-10`) | `scaffold`, `python-smoke` inherit read; `summary` read (`:50-51`) | not persisted (`:25`, `:38`, `:60`) |
| `scorecard.yml` | branch protection change, any PR, push to main, weekly cron (`:2-9`) | `read-all` (`:11`) | `guard` `contents: read` (`:18-19`); `analysis` `security-events: write` and `id-token: write` only (`:33-37`), not on PRs (`:29`); `summary` read (`:56-57`) | not persisted (`:23`, `:42`, `:65`) |
| `vuln-scan.yml` | any PR, weekly cron, dispatch (`:3-7`) | none, `{}` (`:9`) | `scan-pr` `contents: read`, `pull-requests: read` (`:14-16`); `scan-weekly` `contents: read` (`:20-21`) | inside the pinned reusable workflow (`:17`, `:22`); not read here |
| `wiki-sync.yml` | push to main touching `docs/wiki/**` or the workflow, dispatch (`:15-21`) | `contents: read` (`:23-24`) | `sync` `contents: write` (`:32-33`); `summary` read (`:68-69`) | persisted in `sync` (`:36`, no `with:`); not persisted in `summary` (`:78`) |

A job-level `permissions:` block replaces the top-level one for that job,
so `analysis` (`scorecard.yml:33-37`) holds only the two scopes it names.

## Release grant (`release.yml`)

What the grant is for: the `release` job (`release.yml:15`) creates the
release commit, pushes it and the version tag, and creates the GitHub
release. `contents: write` (`release.yml:21`) covers all three; no other
scope is granted.

- Trigger: push to `main` only (`release.yml:3-5`). Runs are serialized,
  never cancelled (`release.yml:10-12`).
- Loop guard: the job skips a push whose head commit starts with
  `chore(release):` (`release.yml:17`). Pushes made with `GITHUB_TOKEN`
  start no workflow run anyway: at release commit `51d6d88` the API lists
  no run of this repository's workflows, only GitHub's own dynamic
  "Push on main" run (read 2026-11-08).
- Checkout keeps the token in the local git config (`release.yml:23-26`,
  no `persist-credentials`). The publish step uses it for
  `git ls-remote` (`:39`), `git push origin main` (`:50`) and the tag
  push (`:51`). The same token reaches `gh` as `GH_TOKEN` (`:35`) for
  `gh release create` (`:52`).
- While that token is in the git config, `python3 scripts/release.py`
  runs (`release.yml:31`). It is this repository's own script from the
  pushed `main` commit, not PR code.
- Data boundary: the release notes are built from commit subjects
  (`scripts/release.py:49-50`), written to a file
  (`scripts/release.py:113-114`) and passed with `--notes-file`
  (`release.yml:52`). No `${{ }}` expression is expanded inside a `run:`
  script; values arrive through `env:` (`release.yml:29-37`).
- What it writes: `CHANGELOG.md` and `app/build.gradle.kts`
  (`release.yml:45`), one commit (`:46`), the `vX.Y.Z` tag (`:49`, `:51`)
  and one release (`:52`). It pushes to `main` directly (`:50`), so
  branch protection must allow that push (not checked here).
- The `summary` job (`release.yml:54-70`) is `contents: read` and does not
  persist credentials.

## Wiki grant (`wiki-sync.yml`)

What the grant is for: the `sync` job (`wiki-sync.yml:31`) pushes
`docs/wiki/` to this repository's own wiki (`<repo>.wiki.git`). The
wiki's git backend is covered by `contents: write` (`wiki-sync.yml:33`).
The file header states the rule: no PAT, and a fallback PAT needs owner
approval first (`wiki-sync.yml:7-10`).

- Trigger: push to `main` that touches `docs/wiki/**` or the workflow
  file, or manual dispatch (`wiki-sync.yml:15-21`). PRs never run it.
  Newer runs cancel older ones (`wiki-sync.yml:26-28`).
- The token goes into the wiki remote URL (`wiki-sync.yml:40`, `:43`)
  of a clone in a temporary directory (`:44-45`). The clone's error
  output is discarded (`:45`); Actions also masks the token in logs.
- What it writes: only the `*.md` files at the top level of `docs/wiki/`
  are copied (`wiki-sync.yml:50-54`). `--delete` removes wiki pages with
  no source file; files the filter excludes are kept, because
  `--delete-excluded` is not set. Then one bot commit (`:61-63`) and one
  push to the wiki's `master` (`:64`). It pushes nothing to this
  repository.
- Run 40860886669 (2026-10-29, at `95e2404`, the same grant shape)
  pushed `HEAD -> master` with this grant. Run 40919834784 (at `99cb783`)
  logged `Contents: write` in its token permissions and "No wiki changes
  to publish."
- The repository checkout (`wiki-sync.yml:36`) keeps the token in the
  workspace's git config, but the push uses the explicit wiki URL. See
  "Gaps" below.
- The `summary` job (`wiki-sync.yml:67-83`) is `contents: read` and does
  not persist credentials.

## Other write grants

- Scorecard `analysis`: `security-events: write` uploads the SARIF file
  (`scorecard.yml:35`, `:49-52`); `id-token: write` lets
  `publish_results` publish to OpenSSF (`scorecard.yml:37`, `:48`). It
  does not run on pull requests (`scorecard.yml:29`).
- Manual GPG test: top-level `contents: write` (`manual-gpg-signing.yml:4-5`)
  so `signing-test` can push one empty signed commit to a new
  `gpg-signing-test/<run>` branch (`manual-gpg-signing.yml:48-54`).
  Dispatch only, by the owner, on `main` (`manual-gpg-signing.yml:3`,
  `:11`). Moving this write to the job is #73.

## How CI holds the line

`.github/scorecard-baseline.json` lists every write grant above as an
accepted finding with its issue and reason (`:4-43`). The offline guard
`scripts/security/scorecard_guard.py` runs on every PR and push in the
`guard` job (`scorecard.yml:14-27`) and fails when:

- a workflow lacks exactly one top-level `permissions` mapping
  (`scorecard_guard.py:97-99`);
- `write-all` or an inline mapping is used (`scorecard_guard.py:115-117`);
- any `write` scope appears, as `top-level-write` at the top or
  `job-write` in a job (`scorecard_guard.py:127-128`), and it is not in
  the baseline (`scorecard_guard.py:303-313`, `:330-333`);
- a baseline entry no longer matches a finding (stale entry,
  `scorecard_guard.py:331`).

So a new write, a write moved to the top level, or a "Run summary" job
given a write each fail the guard. The self-tests cover the top-level and
job-level cases (`tests/security/test_scorecard_guard.py:41-44`).

## Gaps (not changed here)

- The baseline entries for these grants cite #73, not #71, and their
  reasons are short (`.github/scorecard-baseline.json:13-18`, `:37-42`).
  The issue asks for the exact grants and reasons to be recorded there;
  that is a `.github/` change for the maintainer.
- `wiki-sync.yml:36` does not set `persist-credentials: false`, though
  the push uses its own URL (`:43`, `:64`). Turning it off would
  probably not break the sync (not tested in CI).
- `wiki-sync.yml:34` runs on `ubuntu-latest` and the `sync` job has no
  `timeout-minutes`.

Not checked here: the live code-scanning alerts themselves, branch
protection, environment rules and the reusable workflow's own checkout.
