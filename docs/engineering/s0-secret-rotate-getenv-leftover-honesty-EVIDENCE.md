# S0 leftover: SecretPort rotate / getenv / LocalThenEnv leftover honesty evidence beyond #223/#180/#200

See [docs/s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md) (#143), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), and this leftover. Also keep [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), and the other leftover rows already on `main`.

Recorded on this agent VM after rebase onto `origin/main` `2fe75d5` (Merge #233 G17 MFA/OTP leftover honesty after #238). **Not** a product S0 close. **Not** a SecretPort close. **Not** OpenBao / KMS. **Not** CORE-SEC-003. **Not** a redo of #143, #180, #200, or #223. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0 / M0 were not redone.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.7** when present | leftover-off; collect `--skip-tests` when mvn is missing |
| Spring Boot parent | **4.1.1** | product BOM |
| Runtime | `./scripts/s0-secret-rotate-getenv-leftover-honesty.sh` | No Docker / Compose |

## Commands

```bash
./scripts/s0-secret-rotate-getenv-leftover-honesty.sh
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

Do **not** treat `./scripts/s0-secret-rotate-without-restart.sh`, `./scripts/s0-secret-getenv-snapshot-honesty.sh`, `./scripts/s0-secret-rotate-without-restart-honesty.sh`, or `./scripts/s0-secret-rotate-getenv-remainder-honesty.sh` as this leftover. Those scripts are the #143 / #180 / #200 / #223 contracts.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/s0-secret-rotate-getenv-leftover-honesty/`. A sanitized copy is [`s0-secret-rotate-getenv-leftover-honesty/agent-vm.env`](./s0-secret-rotate-getenv-leftover-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake OS getenv PASS / fake live restart PASS / fake LocalThenEnv.system PASS / fake S0 close / redo rejected |
| #223 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| #180 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| #200 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| Mutable env-map rotate | **PASS** (gate) | already proven by #180/#200/#223 harness |
| LocalThenEnv map rotate | **PASS** (gate) | in-process; no overlay; not a #143 redo |
| LocalThenEnv overlay wins | **PASS** (gate) | overlay still wins after env rotate |
| Live OS `System.getenv()` rotate | **SKIP** | Java 21 process-start snapshot; not a supported API |
| Live JVM restart after rotate | **SKIP** | leftover did not restart a process; walk not observed |
| Live `LocalThenEnvSecretPort.system()` getenv | **SKIP** | product default still uses `System::getenv` |
| Live JDBC runtime SecretPort | **SKIP** | Spring still binds `NEXUS_DB_*` at start |
| Live S3/MinIO client rotate | **SKIP** | credentials snapshotted at `fromSecretPort` |
| Leftover rows kept | **true** | #143 + #180 + #200 + #223 plus ALL sibling leftover rows including #212 G7 leftover / #232 G2 leftover / #237 G9 leftover / #239 G7 leftover / #238 G1 leftover / #233 G17 MFA/OTP leftover / #230 / #229 |
| OpenBao / KMS introduced | **false** | ARCH-007 |
| Product S0 / SecretPort close | **NOT_CLAIMED** | leftover only |
| #143 / #180 / #200 / #223 | unchanged | not redone |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- OS `System.getenv()` rotate-without-restart (JVM snapshot; SKIP)
- A live JVM restart after a secret rotate
- Live `LocalThenEnvSecretPort.system()` getenv rotate
- Rebuilding MinIO / JDBC clients after an env rotate
- CORE-SEC-003 tenant `encrypted_payload` / `secret_ref`
- OpenBao / cloud KMS / 国密密码机
- Product S0 / S1 / SecretPort release close
