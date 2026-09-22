# G4 leftover: 90-day hot-purge / P1 staging remainder honesty evidence beyond #183/#190

See [docs/g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g4-hot-purge-90d.md](../g4-hot-purge-90d.md) (#123), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g4-p1-staging-audit-events.md](../g4-p1-staging-audit-events.md) (#156), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), this leftover, [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), and the other leftover rows already on `main`.

Recorded on this agent VM after rebase onto `origin/main` `97a2adc` (Merge #231 S0+S1 claim-list tip-sync; includes #229 / #226 / #228 / #221 / #225 / #224 / #223 / #222 / #216 / #218 / #220). **Not** a product G4 close. **Not** a live `:5432` / JAR prefix DELETE. **Not** a switch of `GET /tenants/{id}/audit-events` off staging. **Not** a redo of #123 / #156 / #183 / #190 / #222 / #223 / #225 / #221 / #228 / #226 / #229. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `platform.audit_record` was **not** dropped. P0 / M0 were not redone.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.7** when present | leftover-off; this leftover does not run parent Jobs |
| Spring Boot parent | **4.1.1** | product BOM |
| Runtime | `./scripts/g4-hot-purge-staging-leftover-honesty.sh` | No Docker / Compose |

## Commands

```bash
./scripts/g4-hot-purge-staging-leftover-honesty.sh
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-leftover-honesty/fixtures/fake-live-purge-pass.env fail
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-leftover-honesty/fixtures/fake-live-staging-pass.env fail
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-leftover-honesty/fixtures/fake-g4-close.env fail
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-leftover-honesty/fixtures/fake-switched-off-staging.env fail
./scripts/g4-hot-purge-staging-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-leftover-honesty/fixtures/fake-redo.env fail
```

Do **not** treat `./scripts/g4-hot-purge-90d.sh`, `./scripts/g4-hot-purge-90d-honesty.sh`, `./scripts/g4-p1-staging-audit-events.sh`, or `./scripts/g4-p1-staging-audit-events-honesty.sh` as this leftover. Those scripts are the #123 / #183 / #156 / #190 contracts.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g4-hot-purge-staging-leftover-honesty/`. A sanitized copy is [`g4-hot-purge-staging-leftover-honesty/agent-vm.env`](./g4-hot-purge-staging-leftover-honesty/agent-vm.env) once recorded. Walk JSON: [`../g4-hot-purge-staging-leftover-honesty/walk.json`](../g4-hot-purge-staging-leftover-honesty/walk.json).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live purge PASS / fake live staging PASS / fake G4 close / fake switched-off staging / redo rejected |
| #183 leftover cells | **PASS** (gate) | window policy PASS; live purge SKIP; `g4_close=NOT_CLAIMED`; not a product redo |
| #190 leftover cells | **PASS** (gate) | staging-like PASS; live `:5432` SKIP; GET still on staging; not a product redo |
| Window policy (90d / 365d, no DROP) | **PASS** | in-memory `AuditRetentionPolicy`; V504 GRANT DELETE only |
| Staging-like query/export | **PASS** | GET still reads `platform.audit_event` |
| Live `:5432` / JAR 90-day prefix DELETE | **SKIP** | no live PG / JAR; Zonky #123 is not a live purge PASS |
| Live Quartz `audit.hot.purge` | **SKIP** | schedule defaults off; 03:00 UTC walk not observed |
| Live MinIO cold drill | **SKIP** | no live MinIO on `:9000` |
| Live `:5432` staging query+export | **SKIP** | credential / listener missing |
| Live JAR / issued context | **SKIP** | `NEXUS_BASE_URL` / context unset; leftover does not start the JAR |
| Leftover rows kept | **true** | ALL sibling leftover rows including #183 / #190 / #222 / #223 / #225 / #221 / #228 / #226 / #229 |
| Product G4 close | **NOT_CLAIMED** | leftover only |
| Hot table dropped | **false** | V504 does not DROP `platform.audit_record` |
| GET switched off staging | **false** | still open |
| #123 / #156 / #183 / #190 / #222 / #223 / #225 / #221 / #228 / #226 / #229 | unchanged | not redone |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product `:5432` 90-day prefix DELETE + suffix `verifyChain()`
- Live Quartz `audit.hot.purge` at 03:00 UTC
- Live MinIO / object-store cold drill as a purge prerequisite
- Switching P1 GET off staging onto M0-11
- Live `:5432` query+export on a host that actually has `platform.audit_event`
- Live JAR / issued-context walk
- Product G4 close
