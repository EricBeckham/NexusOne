# S0 leftover: rotate-without-restart / getenv snapshot honesty evidence beyond #143/#180

See [docs/s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md) (#143), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), and this leftover. Also keep [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-export-wizard.md](../g4-export-wizard.md) (#152), [g4-export-wizard-deny.md](../g4-export-wizard-deny.md) (#166), [g4-pkce-spa-verifier.md](../g4-pkce-spa-verifier.md) (#147), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g2-form-write-honesty.md](../g2-form-write-honesty.md) (#173), [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) (#174), and [g17-mfa-recovery-email-honesty.md](../g17-mfa-recovery-email-honesty.md) (#172).

Recorded on this agent VM after rebase onto `origin/main` `918417c` (Merge #199; includes #192). **Not** a product S0 close. **Not** a SecretPort close. **Not** OpenBao / KMS. **Not** CORE-SEC-003. **Not** a redo of #143 or #180. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0 / M0 were not redone.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.7** when present | leftover-off unit tests on this VM |
| Spring Boot parent | **4.1.1** | product BOM |
| Runtime | `./scripts/s0-secret-rotate-without-restart-honesty.sh` | No Docker / Compose |

## Commands

```bash
./scripts/s0-secret-rotate-without-restart-honesty.sh
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

Do **not** treat `./scripts/s0-secret-rotate-without-restart.sh` or `./scripts/s0-secret-getenv-snapshot-honesty.sh` as this leftover. Those scripts are the #143 / #180 contracts.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/s0-secret-rotate-without-restart-honesty/`. A sanitized copy is [`s0-secret-rotate-without-restart-honesty/agent-vm.env`](./s0-secret-rotate-without-restart-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake OS getenv PASS / fake S0 close rejected |
| Leftover-off `S0SecretRotateWithoutRestartHonestyTest` | **SKIP** on this VM | `mvn` not on PATH; collect `--skip-tests`; DualStackTlsProbe compile leftover not committed |
| Mutable env-map rotate | **PASS** (gate) | thin probe; not a #143/#180 redo |
| OS `System.getenv()` rotate | **SKIP** | Java 21 process-start snapshot; not a supported API |
| Leftover rows kept | **true** | #143 + #180 plus ALL sibling leftover rows including #193 / #192 / #190 |
| OpenBao / KMS introduced | **false** | ARCH-007 |
| Product S0 / SecretPort close | **NOT_CLAIMED** | leftover only |
| #143 / #180 | unchanged | not redone |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- OS `System.getenv()` rotate-without-restart (JVM snapshot; SKIP)
- Rebuilding MinIO / JDBC clients after an env rotate
- CORE-SEC-003 tenant `encrypted_payload` / `secret_ref`
- OpenBao / cloud KMS / 国密密码机
- Product S0 / S1 / SecretPort release close
