# Private municipality response diagnostics

Production records each response by report ID: timestamp, endpoint name, status,
Content-Type/Date/Retry-After only, and a bounded redacted JSON body. No request,
auth headers, cookies or URLs are stored. Token/password/credential keys are
recursively removed and known credential/token values scrubbed. Non-JSON or
oversized bodies are omitted; network errors record class only. This is not a
complete byte-for-byte archive. Logging errors never change submission behavior.

Files live under noBackupFilesDir/municipal-response-log with AES-GCM and a
separate Android Keystore key. No backup, normal UI, report-share or export path.
Retention is seven days on next append, max 20 entries and 2 MB total. Application
uninstall clears files/key. No new server requests, retries or status/list calls.

Only the manual live instrumented test can decrypt logs into a transient evidence
file. The live workflow pulls it into the existing encrypted private bundle and
removes plaintext. It never appears in public output or ordinary CI artifacts.

Redacted responses may retain personal contact data (name, ID, phone, email)
returned by the municipality. It remains encrypted locally and in private live
evidence only, subject to the same caps and retention.
