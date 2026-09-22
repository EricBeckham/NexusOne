# S0 leftover — SecretPort rotate / getenv / LocalThenEnv leftover honesty beyond #223/#180/#200

S0+S1 remainder **after** the #223 rotate/getenv remainder leftover ([s0-secret-rotate-getenv-remainder-honesty.md](./s0-secret-rotate-getenv-remainder-honesty.md)), the #200 rotate-without-restart honesty leftover ([s0-secret-rotate-without-restart-honesty.md](./s0-secret-rotate-without-restart-honesty.md)), and the #180 getenv-snapshot honesty leftover ([s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md)). Rebased onto `origin/main` `c3ffd50` (Merge #212 G7 withdraw browser-evidence honesty after #230). **Not** a P0 / M0-11 CryptoPort redo. **G18 stays closed.** This leftover does **not** redo #143. It does **not** redo #180. It does **not** redo #200. It does **not** redo #223. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** introduce OpenBao / cloud KMS / 国密 KMS / 密码机. It does **not** claim a product S0 close or a SecretPort close. `s0_close=NOT_CLAIMED`. `secret_port_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including `s0-secret-rotate-without-restart` (#143), `s0-secret-getenv-snapshot-honesty` (#180), `s0-secret-rotate-without-restart-honesty` (#200), `s0-secret-rotate-getenv-remainder-honesty` (#223), `g7-withdraw-browser-evidence-honesty` (#212), `g4-hot-purge-staging-leftover-honesty` (#230), `g9-live-multi-jvm-replica-remainder-honesty` (#229), `g3-outbox-consume-idempotent-honesty` (#226), `g10-leftover-honesty-remainder` (#228), `g2-report-physical-dump-honesty` (#221), `g6-recovery-port-dry-run-remainder-honesty` (#225), `g4-pkce-spa-verifier-remainder-honesty` (#222), `g17-separation-leftover-honesty` (#216), `g15-wx-dt-native-bridge-remainder` (#220), `g1-queryast-export-honesty` (#215), `g17-gm-tls-nginx-handshake-honesty` (#210), `g5-publishhook-ddl-honesty` (#217), `g9-sentinel-cluster-remainder-honesty` (#209), `g17-mfa-recovery-vue-otp-honesty` (#205), `g2-ops-pg-dump-honesty` (#188), `g2-h5-every-page-form-write-honesty` (#204), `g4-export-wizard-deny-honesty` (#203), `g10-leftover-honesty` (#202), `g5-mid-publish-failure-honesty` (#196), `g3-outbox-consume-crash-honesty` (#195), `g1-queryast-boundsql-honesty` (#191), `g15-wx-dt-native-bridge-honesty` (#198), `g9-redis-down-invalidate-remainder` (#197), `linux-arm-profile-leftover-honesty` (#193), `g7-query-expense-travel-honesty` (#192), `g4-p1-staging-audit-events-honesty` (#190), `g6-recovery-port-dry-run-honesty` (#186), `s0s1-live-redis-minio-isolation-honesty` (#185), `g17-separation-honesty` (#187), `g4-hot-purge-90d-honesty` (#183), `g1-fieldpolicy-mask-second-auth-honesty` (#184), `g10-identity-drift-honesty` (#179), `g15-native-bridge-honesty` (#181), `g4-pkce-spa-verifier-honesty` (#177), `g5-browser-failed-publish` (#171), `g7-withdraw-browser-honesty` (#175), `g2-form-write-honesty` (#173), `g9-redis-down-invalidate-honesty` (#174), `g10-file-reconcile-honesty` (#169), `g7-withdraw-evidence` (#82), `g7-withdraw-expense-travel` (#103), and `g7-withdraw-browser` (#113/#159). Keep this file.

#223 already is the remainder honesty leftover beyond #180/#200: parent leftover harness fixtures stay always-green **PASS**, live OS getenv rotate and a live JVM restart stay honest **SKIP**. The leftover here is the **honesty leftover beyond #223**: those cells stay honest; parent #223 leftover honesty fixtures stay **PASS**; in-process `LocalThenEnvSecretPort` mutable-map rotate and overlay-wins stay **PASS**. Live OS getenv rotate, a live JVM restart-after-rotate walk, live `LocalThenEnvSecretPort.system()` getenv, JDBC runtime SecretPort, and S3/MinIO client rebuild are **PASS** only when that walk was observed. Java 21 `System.getenv()` is an unmodifiable process-start snapshot. `LocalThenEnvSecretPort.system()` still uses `EnvSecretAdapter.system()` → `System::getenv`. Missing mutability / observation is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behaviour |
|---|---|
| #223 remainder honesty | Already on `main`. Parent `--verify-report` fixtures stay **PASS**. Do **not** redo the #223 leftover or its product code. `leftover_223_harness=PASS`. `leftover_223_cells_honest=PASS`. |
| #180 getenv honesty | Already on `main`. Parent `--verify-report` fixtures stay **PASS**. Do **not** redo the #180 leftover or its product code. `leftover_180_harness=PASS`. `leftover_180_cells_honest=PASS`. |
| #200 rotate honesty | Already on `main`. Parent `--verify-report` fixtures stay **PASS**. Do **not** redo the #200 leftover or its product code. `leftover_200_harness=PASS`. `leftover_200_cells_honest=PASS`. |
| Mutable env-map rotate | Already proven by the #180/#200/#223 harness. Remainder records `env_map_rotate=PASS`. Not a redo of the #143 suite. |
| LocalThenEnv map rotate | Thin in-process `LocalThenEnvSecretPort` probe: mutable env map is re-read when there is no overlay. `local_then_env_map_rotate=PASS`. Not a redo of the #143 suite. |
| LocalThenEnv overlay wins | Overlay still wins after env rotate; delete falls back to env. `local_then_env_overlay_wins=PASS`. |
| Live OS `System.getenv()` rotate | Java 21 `System.getenv()` is an unmodifiable process-start snapshot. OS environ rotate of a running JVM is **not** a supported API. Honesty **SKIP** unless `os_getenv_mutable=true` **and** `os_getenv_rotate_observed=true`. |
| Live JVM restart after rotate | This leftover does **not** restart a running JVM. `live_restart=SKIP` unless `live_restart_observed=true`. A listed env flag is not observation. |
| Live `LocalThenEnvSecretPort.system()` getenv | Product default still uses `EnvSecretAdapter.system()` → `System::getenv`. Honesty **SKIP** unless mutability + observation. |
| Live JDBC runtime SecretPort | Spring still binds `NEXUS_DB_*` at start. Honesty **SKIP**. |
| Live S3 / MinIO client rotate | Credentials stay snapshotted at `fromSecretPort`. Honesty **SKIP**. |
| Honesty gate | `--verify-report` rejects `live_os_getenv_rotate=PASS` / `live_local_then_env_system_getenv=PASS` without mutability + observation, rejects `live_restart=PASS` / `live_jdbc_runtime_secretport=PASS` / `live_s3_client_rotate=PASS` without observation, rejects `s0_close=CLOSED` / `secret_port_close=CLOSED`, OpenBao / KMS introduction, and #143/#180/#200/#223 redo flags. Requires leftover rows kept. |
| Leftover rows | `docs/README.md` keeps #143 / #180 / #200 / #223 plus this leftover and **ALL** sibling leftover rows, including #212 G7 leftover / #230 / #229 / #226 / #228 / #221 / #225 / #222 / #216. |

No OpenBao. No KMS. Test tokens are in-process maps only; none are committed. Do **not** run `./scripts/s0-secret-rotate-without-restart.sh`, `./scripts/s0-secret-getenv-snapshot-honesty.sh`, `./scripts/s0-secret-rotate-without-restart-honesty.sh`, or `./scripts/s0-secret-rotate-getenv-remainder-honesty.sh` as this leftover (parent `--verify-report` on honest fixtures is allowed as harness evidence).

## How to run (no Docker)

```bash
# Always-green parent harness + LocalThenEnv in-process PASS + honest live SKIP
./scripts/s0-secret-rotate-getenv-leftover-honesty.sh

# Honesty gate only
./scripts/s0-secret-rotate-getenv-leftover-honesty.sh --verify-report \
  scripts/s0-secret-rotate-getenv-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/s0-secret-rotate-getenv-leftover-honesty.sh --verify-report \
  scripts/s0-secret-rotate-getenv-leftover-honesty/fixtures/fake-os-getenv-pass.env
./scripts/s0-secret-rotate-getenv-leftover-honesty.sh --verify-report \
  scripts/s0-secret-rotate-getenv-leftover-honesty/fixtures/fake-live-restart-pass.env
./scripts/s0-secret-rotate-getenv-leftover-honesty.sh --verify-report \
  scripts/s0-secret-rotate-getenv-leftover-honesty/fixtures/fake-local-then-env-system-pass.env
./scripts/s0-secret-rotate-getenv-leftover-honesty.sh --verify-report \
  scripts/s0-secret-rotate-getenv-leftover-honesty/fixtures/fake-s0-close.env
./scripts/s0-secret-rotate-getenv-leftover-honesty.sh --verify-report \
  scripts/s0-secret-rotate-getenv-leftover-honesty/fixtures/fake-redone.env
```

Equivalent always-green Java (does **not** redo #143 / #180 / #200 / #223 suites):

```bash
mvn -pl nexus-crypto-core -am test \
  -Dtest=S0SecretRotateGetenvLeftoverHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The existing leftover scripts stay as reproduction commands:

```bash
# do not run for this leftover
# ./scripts/s0-secret-rotate-without-restart.sh
# ./scripts/s0-secret-getenv-snapshot-honesty.sh
# ./scripts/s0-secret-rotate-without-restart-honesty.sh
# ./scripts/s0-secret-rotate-getenv-remainder-honesty.sh
```

Java **21** / Boot **4.1.1**. No Docker. No live `127.0.0.1:5432` required.

See [engineering/s0-secret-rotate-getenv-leftover-honesty-EVIDENCE.md](./engineering/s0-secret-rotate-getenv-leftover-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/s0-secret-rotate-getenv-leftover-honesty/`.

## What this does not claim

| Leftover | Why |
|---|---|
| Product S0 close | Explicitly `s0_close=NOT_CLAIMED`. Env + local overlay only. CORE-SEC-003 / OpenBao / KMS stay out. |
| SecretPort close | Explicitly `secret_port_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| OS process env rotate without restart | JVM snapshot. Honest SKIP. |
| Live JVM restart after rotate | Not observed. Honest SKIP. |
| Live `LocalThenEnvSecretPort.system()` getenv | Product default still snapshots `System.getenv()`. Honest SKIP. |
| JDBC DataSource runtime SecretPort | Spring still binds `NEXUS_DB_*` at start. Honest SKIP. |
| S3 / MinIO client live rotate | Credentials snapshotted at `fromSecretPort`. Honest SKIP. |
| Redo of #143 / #180 / #200 / #223 | Mutable-map / overlay / File S3 snapshot ITs stay on #143. #180 / #200 / #223 honesty leftovers stay siblings. |
| OpenBao / cloud KMS / 国密密码机 | ARCH-007. Not introduced. |
| G18 reopen / AuditPort / P0/M0 redo | Closed / untouched / out of scope. |

## Honest leftovers (not claimed)

- OS `System.getenv()` rotate-without-restart on a running JVM (Java 21 snapshot; SKIP)
- A live JVM restart after a secret rotate, with a post-restart getenv walk
- Live `LocalThenEnvSecretPort.system()` picking up an OS environ rotate
- Rebuilding `MinioObjectStorage` / Spring DataSource after an env rotate
- CORE-SEC-003 tenant `encrypted_payload` / `secret_ref`
- OpenBao / KMS / 密码机 adapters
- Product S0 / S1 / SecretPort release close
- Redo of #143 / #180 / #200 / #223
