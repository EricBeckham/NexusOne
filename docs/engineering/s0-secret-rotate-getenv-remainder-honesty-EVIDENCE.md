# S0 leftover: rotate-without-restart / getenv remainder honesty evidence beyond #180/#200

See [docs/s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md) (#143), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), and this leftover. Also keep [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), and the other leftover rows already on `main`.

Recorded on this agent VM after rebase onto `origin/main` `165b5f4` (Merge #222; includes #216 / #218 / #220 / #215 / #210 / #217 / #209 / #214). **Not** a product S0 close. **Not** a SecretPort close. **Not** OpenBao / KMS. **Not** CORE-SEC-003. **Not** a redo of #143, #180, or #200. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0 / M0 were not redone.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.7** when present | leftover-off; collect `--skip-tests` when mvn is missing |
| Spring Boot parent | **4.1.1** | product BOM |
| Runtime | `./scripts/s0-secret-rotate-getenv-remainder-honesty.sh` | No Docker / Compose |

## Commands

```bash
./scripts/s0-secret-rotate-getenv-remainder-honesty.sh
./scripts/s0-secret-rotate-getenv-remainder-honesty.sh --verify-report \
  scripts/s0-secret-rotate-getenv-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/s0-secret-rotate-getenv-remainder-honesty.sh --verify-report \
  scripts/s0-secret-rotate-getenv-remainder-honesty/fixtures/fake-os-getenv-pass.env
./scripts/s0-secret-rotate-getenv-remainder-honesty.sh --verify-report \
  scripts/s0-secret-rotate-getenv-remainder-honesty/fixtures/fake-live-restart-pass.env
./scripts/s0-secret-rotate-getenv-remainder-honesty.sh --verify-report \
  scripts/s0-secret-rotate-getenv-remainder-honesty/fixtures/fake-s0-close.env
./scripts/s0-secret-rotate-getenv-remainder-honesty.sh --verify-report \
  scripts/s0-secret-rotate-getenv-remainder-honesty/fixtures/fake-redone.env
```

Do **not** treat `./scripts/s0-secret-rotate-without-restart.sh`, `./scripts/s0-secret-getenv-snapshot-honesty.sh`, or `./scripts/s0-secret-rotate-without-restart-honesty.sh` as this leftover. Those scripts are the #143 / #180 / #200 contracts.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/s0-secret-rotate-getenv-remainder-honesty/`. A sanitized copy is [`s0-secret-rotate-getenv-remainder-honesty/agent-vm.env`](./s0-secret-rotate-getenv-remainder-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake OS getenv PASS / fake live restart PASS / fake S0 close / redo rejected |
| #180 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| #200 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| Mutable env-map rotate | **PASS** (gate) | already proven by #180/#200 harness |
| Live OS `System.getenv()` rotate | **SKIP** | Java 21 process-start snapshot; not a supported API |
| Live JVM restart after rotate | **SKIP** | leftover did not restart a process; walk not observed |
| Leftover rows kept | **true** | #143 + #180 + #200 plus ALL sibling leftover rows including #222 / #216 / #218 / #220 / #215 / #210 / #217 / #209 |
| OpenBao / KMS introduced | **false** | ARCH-007 |
| Product S0 / SecretPort close | **NOT_CLAIMED** | leftover only |
| #143 / #180 / #200 | unchanged | not redone |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- OS `System.getenv()` rotate-without-restart (JVM snapshot; SKIP)
- A live JVM restart after a secret rotate
- Rebuilding MinIO / JDBC clients after an env rotate
- CORE-SEC-003 tenant `encrypted_payload` / `secret_ref`
- OpenBao / cloud KMS / 国密密码机
- Product S0 / S1 / SecretPort release close
