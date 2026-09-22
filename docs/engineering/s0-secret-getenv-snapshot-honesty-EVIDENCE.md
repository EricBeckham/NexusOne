# S0 leftover: OS `System.getenv` startup-snapshot honesty evidence

See [docs/s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md). Keep **both** leftover rows: [s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md) (#143) and this leftover. Keep [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g2-form-write-honesty.md](../g2-form-write-honesty.md) (#173), [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) (#174), and [g17-mfa-recovery-email-honesty.md](../g17-mfa-recovery-email-honesty.md) (#172).

Recorded on this agent VM after rebase onto `origin/main` `84ac6f7` (Merge #171). **Not** a product S0 close. **Not** OpenBao / KMS. **Not** CORE-SEC-003. **Not** a redo of #143. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0 / M0 were not redone.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.7** | leftover-off unit tests on this VM |
| Spring Boot parent | **4.1.1** | product BOM |
| Runtime | `./scripts/s0-secret-getenv-snapshot-honesty.sh` | No Docker / Compose |

## Commands

```bash
./scripts/s0-secret-getenv-snapshot-honesty.sh
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

Do **not** treat `./scripts/s0-secret-rotate-without-restart.sh` as this leftover. That script is the #143 contract.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/s0-secret-getenv-snapshot-honesty/`. A sanitized copy is [`s0-secret-getenv-snapshot-honesty/agent-vm.env`](./s0-secret-getenv-snapshot-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake OS getenv PASS / fake S0 close rejected |
| Leftover-off `S0SecretGetenvSnapshotHonestyTest` | **8/8 PASS** | DualStackTlsProbe compile leftover moved aside only; not committed |
| Mutable env-map rotate | **PASS** (gate) | thin probe; not a #143 redo |
| OS `System.getenv()` rotate | **SKIP** | Java 21 process-start snapshot; not a supported API |
| OpenBao / KMS introduced | **false** | ARCH-007 |
| Product S0 close | **NOT_CLAIMED** | leftover only |
| #143 rotate-without-restart | unchanged | not redone |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- OS `System.getenv()` rotate-without-restart (JVM snapshot; SKIP)
- Rebuilding MinIO / JDBC clients after an env rotate
- CORE-SEC-003 tenant `encrypted_payload` / `secret_ref`
- OpenBao / cloud KMS / 国密密码机
- Product S0 / S1 release close
