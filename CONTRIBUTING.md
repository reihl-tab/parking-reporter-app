# Contributing

## Workflow

1. Open or select an issue with scope, acceptance criteria and risks. Security-sensitive reports follow SECURITY.md.
2. Create a focused branch, such as `feat/m1-capture-shell` or `fix/approval-invalidation`.
3. Make small, meaningful commits: `docs:`, `test:`, `fix:`, `feat:` or `chore:` with a clear imperative description. Do not mix unrelated changes.
4. Open a pull request linked to the issue. Explain behavior, tests, privacy effects and any design changes.
5. Review the diff, run the applicable checks and squash-merge only after authorized review and passing CI. Delete the merged branch. Do not bypass checks or push directly to main.

## Standards

- Kotlin typed interfaces and explicit state transitions; English code/docs and Hebrew report text.
- Unit tests plus functional/emulator-based e2e tests for user flows. Mock network transport; CI never calls the municipal API or creates reports.
- Regression tests for approval invalidation, duplicate photos/IDs, crash checkpoints, uncertain outcomes and no-retry behavior.
- Synthetic photos, plates and reporter profiles only. No private evidence, personal details, secrets, generated build output or junk files.
- Design diagrams: any issue or proposal that needs design (API, containers, architecture, data flow, security boundaries, test pipelines) includes a Mermaid diagram in the issue body or docs. Mermaid renders natively on GitHub and stays version-controlled; do not add binary diagram files. Diagrams use synthetic data only: no real plates, locations or living-person data.
- Update README, design and release notes when behavior changes. Record material architectural choices in a short design note.
- Pin toolchain and dependencies, verify Gradle distribution checksums and review dependency licenses before release.

## Versioning

- Semantic Versioning, derived from conventional commit subjects: `fix` bumps PATCH, `feat` bumps MINOR, a breaking change (`BREAKING CHANGE` or `!:`) bumps MAJOR.
- Releases are automated and lightweight: `.github/workflows/release.yml` runs `scripts/release.py` on every merge to main, bumps `versionName`/`versionCode`, adds the CHANGELOG.md entry, tags `v<version>` and creates the GitHub release with notes in Hebrew and English (commit subjects stay in English). Small, frequent releases; no manual ceremony.
- Every bug fix ships with a regression test.
- The version is visible in the app (Settings screen).
- No distribution outside this repository (APK, stores, municipality) without explicit owner approval.

## Current checks

With Python 3 installed, run `python3 scripts/check_repository.py` from the repository root. This checks the scaffolding; it is not an Android test suite or a full secret scanner.

Run the Gradle commands in README.md for Android build, lint, unit tests and emulator tests. Never use a skipped or placeholder job as evidence of correctness.

## Issues: labels, milestones and order

The same scheme is used in every public repo. Do not add parallel label schemes.

**Labels** (every open issue has one type, one area and one priority)
- `type: feature | fix | task | docs | security` - the kind of work.
- `area: ...` - the part of the project (for example api, ci, containers, data, docs, quality, security). The list is per repo.
- `priority: high | medium | low` - high is done first.
- `status: blocked | held | in-progress | needs-review` - only when it applies. `status: in-progress` means someone claimed it.
- `needs-triage` - not sorted yet. Remove it when the three labels above are set.
- `good first issue` - small and clear, a good place to start.

**Milestones**
- `Now` - in progress or due this week. `Next` - planned after Now. `Later` - backlog.
- Every open issue is in exactly one. High priority goes to Now, medium to Next, low to Later, unless the maintainer decides otherwise. Repos may keep their own dated or goal milestones as well.

**How to pick an issue**
1. Open the repo's issues filtered by milestone `Now`, then `Next`.
2. Within a milestone, take `priority: high` first, then medium, then low. Prefer `good first issue` if you are new.
3. Skip anything with `status: blocked`, `status: held` or `status: in-progress`.
4. Comment "taking this" on the issue, then work from a fork and open a PR that references it.
## File placement (enforced)

Decide where a file lives BEFORE creating it. The contract in `.repo-layout.yml` lists the allowed top-level directories, the file types permitted in each, and the required community-standards files; `python tools/check_repo_layout.py` enforces it in CI. A new kind of file means updating the contract in the same PR.
