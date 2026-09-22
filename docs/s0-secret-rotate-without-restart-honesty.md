# S0 leftover — rotate-without-restart / getenv snapshot honesty beyond #143/#180

S0+S1 remainder after SecretPort on `main` ([s0-secret-port.md](./engineering/s0-secret-port.md)), the #143 rotate-without-restart leftover ([s0-secret-rotate-without-restart.md](./s0-secret-rotate-without-restart.md)), and the #180 getenv-snapshot honesty leftover ([s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md)). Rebased onto `origin/main` `918417c` (after #199 S0+S1 claim-list tip-sync / #192 G7 query leftover honesty / #193 Linux/ARM leftover honesty / #190 P1 staging audit-events leftover honesty). **Not** a P0 / M0-11 CryptoPort redo. **G18 stays closed.** This leftover does **not** redo #143. It does **not** redo #180. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** introduce OpenBao / cloud KMS / 国密 KMS / 密码机. It does **not** claim a product S0 close or a SecretPort close. `s0_close=NOT_CLAIMED`. `secret_port_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including `s0-secret-rotate-without-restart` (#143), `s0-secret-getenv-snapshot-honesty` (#180), `linux-arm-profile-leftover-honesty` (#193), `g7-query-expense-travel-honesty` (#192), `g4-p1-staging-audit-events-honesty` (#190), `g6-recovery-port-dry-run-honesty` (#186), `s0s1-live-redis-minio-isolation-honesty` (#185), `g4-hot-purge-90d-honesty` (#183), `g17-separation-honesty` (#187), `g4-export-wizard` (#152), `g4-export-wizard-deny` (#166), `g4-pkce-spa-verifier` (#147), `g4-pkce-spa-verifier-honesty` (#177), `g10-identity-drift-honesty` (#179), `g15-native-bridge-honesty` (#181), `g5-browser-failed-publish` (#171), `g1-fieldpolicy-mask-second-auth-honesty` (#184), `g7-withdraw-browser-honesty` (#175), `g2-form-write-honesty` (#173), `g9-redis-down-invalidate-honesty` (#174), and `g17-mfa-recovery-email-honesty` (#172). Keep this file.

#143 already proves a mutable env map is re-read on the next `get` / `find` / `getUtf8`. #180 already is the honesty leftover for OS `System.getenv()` startup-snapshot SKIP. The leftover here is the **honesty gate** beyond both: mutable env-map rotate stays always-green **PASS**. OS environ rotate of a running JVM is **PASS** only when the process map is mutable **and** a live OS rotate was observed. Java 21 `System.getenv()` is an unmodifiable process-start snapshot. Missing mutability / observation is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behaviour |
|---|---|
| #143 mutable env map | Thin in-process probe: `EnvSecretAdapter` does not cache. Next `get` sees the rotated value. Always-green **PASS**. Not a redo of the #143 suite. |
| #180 getenv honesty | Already on `main`. Do **not** redo the #180 leftover or its `--verify-report` fixtures. |
| Product `EnvSecretAdapter.system()` / `System.getenv()` | Java 21 `System.getenv()` is an unmodifiable process-start snapshot. OS environ rotate of a running JVM is **not** a supported API. Honesty **SKIP**. Restart is required. |
| Honesty gate | `--verify-report` rejects `os_getenv_rotate=PASS` without `os_getenv_mutable=true` **and** `os_getenv_rotate_observed=true`. Rejects `s0_close=CLOSED`, `secret_port_close=CLOSED`, OpenBao / KMS introduction, and #143/#180 redo flags. Requires both leftover rows kept. |
| Leftover rows | `docs/README.md` keeps #143 and #180 plus this honesty leftover and **ALL** sibling leftover rows. |

No OpenBao. No KMS. Test tokens are in-process maps only; none are committed. Do **not** run `./scripts/s0-secret-rotate-without-restart.sh` or `./scripts/s0-secret-getenv-snapshot-honesty.sh` as this leftover.

## How to run (no Docker)

```bash
# Always-green env-map rotate + honest OS getenv SKIP
./scripts/s0-secret-rotate-without-restart-honesty.sh

# Honesty gate only
./scripts/s0-secret-rotate-without-restart-honesty.sh --verify-report \
  scripts/s0-secret-rotate-without-restart-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/s0-secret-rotate-without-restart-honesty.sh --verify-report \
  scripts/s0-secret-rotate-without-restart-honesty/fixtures/fake-os-getenv-pass.env
./scripts/s0-secret-rotate-without-restart-honesty.sh --verify-report \
  scripts/s0-secret-rotate-without-restart-honesty/fixtures/fake-s0-close.env
```

Equivalent always-green Java (does **not** redo #143 / #180 suites):

```bash
mvn -pl nexus-crypto-core -am test \
  -Dtest=S0SecretRotateWithoutRestartHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The existing leftover scripts stay as reproduction commands:

```bash
# do not run for this leftover
# ./scripts/s0-secret-rotate-without-restart.sh
# ./scripts/s0-secret-getenv-snapshot-honesty.sh
```

Java **21** / Boot **4.1.1**. No Docker. No live `127.0.0.1:5432` required.

See [engineering/s0-secret-rotate-without-restart-honesty-EVIDENCE.md](./engineering/s0-secret-rotate-without-restart-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/s0-secret-rotate-without-restart-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product S0 close | Explicitly `s0_close=NOT_CLAIMED`. Env + local overlay only. CORE-SEC-003 / OpenBao / KMS stay out. |
| SecretPort close | Explicitly `secret_port_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| OS process env rotate without restart | JVM snapshot. Honest SKIP. |
| Redo of #143 / #180 | Mutable-map / overlay / File S3 snapshot ITs stay on #143. #180 honesty leftover stays a sibling. |
| JDBC DataSource runtime SecretPort | Spring still binds `NEXUS_DB_*` at start. |
| S3 / MinIO client live rotate | Credentials snapshotted at `fromSecretPort`. |
| OpenBao / cloud KMS / 国密密码机 | ARCH-007. Not introduced. |
| G18 reopen / AuditPort / P0/M0 redo | Closed / untouched / out of scope. |

## Honest leftovers (not claimed)

- OS `System.getenv()` rotate-without-restart on a running JVM (Java 21 snapshot; SKIP)
- Rebuilding `MinioObjectStorage` / Spring DataSource after an env rotate
- CORE-SEC-003 tenant `encrypted_payload` / `secret_ref`
- OpenBao / KMS / 密码机 adapters
- Product S0 / S1 / SecretPort release close
