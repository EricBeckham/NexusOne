# S0 leftover: SecretPort rotate / getenv / LocalThenEnv leftover honesty remainder evidence beyond #241/#223

See [docs/s0-secret-rotate-getenv-leftover-honesty-remainder.md](../s0-secret-rotate-getenv-leftover-honesty-remainder.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md) (#143), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md) (#241), and this leftover. Also keep [g17-separation-leftover-honesty-remainder.md](../g17-separation-leftover-honesty-remainder.md) (#249), [g9-leftover-honesty-remainder.md](../g9-leftover-honesty-remainder.md) (#259), [g15-h5-pkce-miniprogram-remainder-honesty.md](../g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g2-h5-every-page-form-write-report-remainder-honesty.md](../g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty.md](../g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty.md](../g10-file-object-reconcile-remainder-honesty.md) (#243), [g5-publishhook-ddl-remainder-honesty.md](../g5-publishhook-ddl-remainder-honesty.md) (#235), [g17-mfa-recovery-vue-otp-remainder-honesty.md](../g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](../g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), and every other leftover row already on `main`.

Recorded on this agent VM after rebase onto `origin/main` `e10be1f` (Merge #259 G9 leftover honesty remainder after #249). **Not** a product S0 close. **Not** a SecretPort close. **Not** OpenBao / KMS. **Not** CORE-SEC-003. **Not** a redo of #143, #180, #200, #223, or #241. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0 / M0 were not redone.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.7** when present | leftover-off; collect `--skip-tests` when mvn is missing |
| Spring Boot parent | **4.1.1** | product BOM |
| Runtime | `./scripts/s0-secret-rotate-getenv-leftover-honesty-remainder.sh` | No Docker / Compose |

## Commands

```bash
./scripts/s0-secret-rotate-getenv-leftover-honesty-remainder.sh
./scripts/s0-secret-rotate-getenv-leftover-honesty-remainder.sh --verify-report \
  scripts/s0-secret-rotate-getenv-leftover-honesty-remainder/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/s0-secret-rotate-getenv-leftover-honesty-remainder.sh --verify-report \
  scripts/s0-secret-rotate-getenv-leftover-honesty-remainder/fixtures/fake-os-getenv-pass.env fail
./scripts/s0-secret-rotate-getenv-leftover-honesty-remainder.sh --verify-report \
  scripts/s0-secret-rotate-getenv-leftover-honesty-remainder/fixtures/fake-live-restart-pass.env fail
./scripts/s0-secret-rotate-getenv-leftover-honesty-remainder.sh --verify-report \
  scripts/s0-secret-rotate-getenv-leftover-honesty-remainder/fixtures/fake-local-then-env-system-pass.env fail
./scripts/s0-secret-rotate-getenv-leftover-honesty-remainder.sh --verify-report \
  scripts/s0-secret-rotate-getenv-leftover-honesty-remainder/fixtures/fake-s0-close.env fail
./scripts/s0-secret-rotate-getenv-leftover-honesty-remainder.sh --verify-report \
  scripts/s0-secret-rotate-getenv-leftover-honesty-remainder/fixtures/fake-redone.env fail
```

Do **not** treat `./scripts/s0-secret-rotate-without-restart.sh`, `./scripts/s0-secret-getenv-snapshot-honesty.sh`, `./scripts/s0-secret-rotate-without-restart-honesty.sh`, `./scripts/s0-secret-rotate-getenv-remainder-honesty.sh`, or `./scripts/s0-secret-rotate-getenv-leftover-honesty.sh` as this leftover. Those scripts are the #143 / #180 / #200 / #223 / #241 contracts.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/s0-secret-rotate-getenv-leftover-honesty-remainder/`. A sanitized copy is [`s0-secret-rotate-getenv-leftover-honesty-remainder/agent-vm.env`](./s0-secret-rotate-getenv-leftover-honesty-remainder/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake OS getenv PASS / fake live restart PASS / fake LocalThenEnv.system PASS / fake S0 close / redo rejected |
| #241 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| #223 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| #180 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| #200 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| Mutable env-map rotate | **PASS** (gate) | already proven by #180/#200/#223/#241 harness |
| LocalThenEnv map rotate | **PASS** (gate) | in-process; no overlay; not a #143 redo |
| LocalThenEnv overlay wins | **PASS** (gate) | overlay still wins after env rotate |
| Live OS `System.getenv()` rotate | **SKIP** | Java 21 process-start snapshot; not a supported API |
| Live JVM restart after rotate | **SKIP** | leftover did not restart a process; walk not observed |
| Live `LocalThenEnvSecretPort.system()` getenv | **SKIP** | product default still uses `System::getenv` |
| Live JDBC runtime SecretPort | **SKIP** | Spring still binds `NEXUS_DB_*` at start |
| Live S3/MinIO client rotate | **SKIP** | credentials snapshotted at `fromSecretPort` |
| Leftover rows kept | **true** | #143 + #180 + #200 + #223 + #241 plus ALL sibling leftover rows including #259 G9 leftover / #249 G17 SoD leftover / #240 G15 leftover / #254 G2 leftover / #236 Linux/ARM leftover / #251 / #243 |
| OpenBao / KMS introduced | **false** | ARCH-007 |
| Product S0 / SecretPort close | **NOT_CLAIMED** | leftover only |
| #143 / #180 / #200 / #223 / #241 | unchanged | not redone |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- OS `System.getenv()` rotate-without-restart (JVM snapshot; SKIP)
- A live JVM restart after a secret rotate
- Live `LocalThenEnvSecretPort.system()` getenv rotate
- Rebuilding MinIO / JDBC clients after an env rotate
- CORE-SEC-003 tenant `encrypted_payload` / `secret_ref`
- OpenBao / cloud KMS / 国密密码机
- Product S0 / S1 / SecretPort release close
- Redo of #143 / #180 / #200 / #223 / #241
