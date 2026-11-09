# M0 engine-level live trial (owner-approved 2026-11-09)

One owner-approved trial of the app's municipal submission engine from
GitHub Actions. Design:

- **Engine-level**: `EngineLiveTrialTest` drives the app's real classes
  (`SubmissionCoordinator`, `SubmissionStateMachine`, `OkHttpMunicipalClient`,
  `StreetDirectory`) on the JVM. No emulator, no screens.
- **Two phases** (workflow_dispatch input):
  1. `login-only` - diagnostic Login call only. No case writes.
  2. `submit` - one real case, gated by `approved_sha256`: the approved
     text must hash to the dispatch input the owner approved in chat.
     Same binding as the helper pipeline's `--approved-sha256`.
- **Direct egress first**: GitHub-runner IPs got 403 from the municipal API
  on 2026-10-08, but US datacenter IPs (Render, Google Cloud) file
  successfully, so the block is range-specific, not geo. The trial tries
  direct first; only on failure it brings up the owner-operated L2TP tunnel
  (`l2tp.py`, ported from the private repo, no municipal calls inside) and
  retries once.
- **Privacy**: report specifics (plate, address, coordinates, capture time,
  street lookup) live only in Actions secrets and are passed as env; the
  photo lives here only as `trial-photo.jpg.enc` (AES-256-CBC/PBKDF2, key
  in the `TRIAL_DATA_KEY` secret, decrypted to /tmp inside the runner and
  deleted in the always-cleanup step). Nothing report-specific is printed;
  outcomes print only a fixed `TRIAL_RESULT` line.
- The workflow refuses to run from any ref except `main`.

After the trial concludes, this directory and the workflow are removed and
the `TRIAL_*` secrets deleted.

Note: the encrypted photo blob stays in this repo's git history even after
trial/ is removed; the TRIAL_DATA_KEY's secrecy is the only protection, so
the key must never leave the secrets store.
