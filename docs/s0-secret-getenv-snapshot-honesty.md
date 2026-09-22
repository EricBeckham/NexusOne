# S0 leftover — OS `System.getenv` startup-snapshot honesty beyond #143

S0+S1 remainder after SecretPort on `main` ([s0-secret-port.md](./engineering/s0-secret-port.md)) and the #143 rotate-without-restart leftover ([s0-secret-rotate-without-restart.md](./s0-secret-rotate-without-restart.md)). Rebased onto `origin/main` `84ac6f7` (after #171). **Not** a P0 / M0-11 CryptoPort redo. **G18 stays closed.** This leftover does **not** redo #143. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** introduce OpenBao / cloud KMS / 国密 KMS / 密码机. It does **not** claim a product S0 close.

Keep **both** leftover rows in [docs/README.md](./README.md): `s0-secret-rotate-without-restart` (#143) and this file. Also keep the rotate-without-restart leftover honesty ([s0-secret-rotate-without-restart-honesty.md](./s0-secret-rotate-without-restart-honesty.md)); that leftover does **not** redo this package. Also keep the rotate/getenv remainder leftover ([s0-secret-rotate-getenv-remainder-honesty.md](./s0-secret-rotate-getenv-remainder-honesty.md)); that leftover does **not** redo this package. Also keep newly landed sibling leftovers `linux-arm-profile-leftover-honesty` (#193), `g7-query-expense-travel-honesty` (#192), `g4-p1-staging-audit-events-honesty` (#190), `g5-browser-failed-publish` (#171), `g7-withdraw-browser-honesty` (#175), `g2-form-write-honesty` (#173), `g9-redis-down-invalidate-honesty` (#174), and `g17-mfa-recovery-email-honesty` (#172).

#143 already proves a mutable env map is re-read on the next `get` / `find` / `getUtf8`. Product `EnvSecretAdapter.system()` uses `System::getenv`. The leftover here is the **honesty gate** for OS process env rotate-without-restart: Java 21 `System.getenv()` is an unmodifiable process-start snapshot. OS environ rotate of a running JVM is **not** a supported API. Test **SKIP**s with that reason unless the process map is mutable **and** a live OS rotate was observed. It never fabricates `PASS`.

## What this leftover covers

| Path | Behaviour |
|---|---|
| #143 mutable env map | Thin in-process probe: `EnvSecretAdapter` does not cache. Next `get` sees the rotated value. Always-green **PASS**. Not a redo of the #143 suite. |
| Product `EnvSecretAdapter.system()` / `System.getenv()` | Java 21 `System.getenv()` is an unmodifiable process-start snapshot. OS environ rotate of a running JVM is **not** a supported API. Honesty **SKIP**. Restart is required. |
| Honesty gate | `--verify-report` rejects `os_getenv_rotate=PASS` without `os_getenv_mutable=true` **and** `os_getenv_rotate_observed=true`. Rejects `s0_close=CLOSED`, OpenBao / KMS introduction, and a #143 redo flag. |
| Sibling leftover | `#143` [s0-secret-rotate-without-restart.md](./s0-secret-rotate-without-restart.md) stays. |

No OpenBao. No KMS. Test tokens are in-process maps only; none are committed.

## How to run (no Docker)

```bash
# Always-green env-map rotate + honest OS getenv SKIP
./scripts/s0-secret-getenv-snapshot-honesty.sh

# Honesty gate only
./scripts/s0-secret-getenv-snapshot-honesty.sh --verify-report \
  scripts/s0-secret-getenv-snapshot-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/s0-secret-getenv-snapshot-honesty.sh --verify-report \
  scripts/s0-secret-getenv-snapshot-honesty/fixtures/fake-os-getenv-pass.env
./scripts/s0-secret-getenv-snapshot-honesty.sh --verify-report \
  scripts/s0-secret-getenv-snapshot-honesty/fixtures/fake-s0-close.env
```

Equivalent always-green Java (does **not** redo #143 suites):

```bash
mvn -pl nexus-crypto-core -am test \
  -Dtest=S0SecretGetenvSnapshotHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The #143 rotate leftover is unchanged as a sibling:

```bash
./scripts/s0-secret-rotate-without-restart.sh
```

Java **21** / Boot **4.1.1**. No Docker. No live `127.0.0.1:5432` required.

See [engineering/s0-secret-getenv-snapshot-honesty-EVIDENCE.md](./engineering/s0-secret-getenv-snapshot-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/s0-secret-getenv-snapshot-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product S0 close | Explicitly `s0_close=NOT_CLAIMED`. Env + local overlay only. CORE-SEC-003 / OpenBao / KMS stay out. |
| OS process env rotate without restart | JVM snapshot. Honest SKIP. |
| Redo of #143 | Mutable-map / overlay / File S3 snapshot ITs stay on that leftover. |
| JDBC DataSource runtime SecretPort | Spring still binds `NEXUS_DB_*` at start. |
| S3 / MinIO client live rotate | Credentials snapshotted at `fromSecretPort`. |
| OpenBao / cloud KMS / 国密密码机 | ARCH-007. Not introduced. |
| G18 reopen / AuditPort / P0/M0 redo | Closed / untouched / out of scope. |

## Honest leftovers (not claimed)

- OS `System.getenv()` rotate-without-restart on a running JVM (Java 21 snapshot; SKIP)
- Rebuilding `MinioObjectStorage` / Spring DataSource after an env rotate
- CORE-SEC-003 tenant `encrypted_payload` / `secret_ref`
- OpenBao / KMS / 密码机 adapters
- Product S0 / S1 release close
