# G3 leftover — Outbox consume-idempotent / claim-crash-window honesty beyond #195/#139/#170 — evidence

Recorded on this branch after rebase onto `origin/main` `1bccc47` (Merge #228 G10 leftover honesty remainder after #221 G2 report/physical-dump leftover honesty / #225 G6 RecoveryPort leftover honesty / #224 S0+S1 claim-list tip-sync / #223 S0 rotate/getenv remainder leftover honesty / #222 G4 live Keycloak CA / SPA PKCE remainder leftover honesty / #216 G17 SoD leftover honesty / #218 S0+S1 claim-list tip-sync / #220 G15 wx-dt/native-bridge remainder leftover honesty / #215 G1 BoundSql/export leftover honesty). **Not** a product G3 close. **Not** a redo of #139 / #170 / #195. **Not** an Outbox / Dispatcher rebuild. **Not** live multi-node Dispatcher. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0/M0 were not redone.

See [docs/g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g3-outbox-consume-idempotent.md](../g3-outbox-consume-idempotent.md) (#139), [g3-claim-crash-window.md](../g3-claim-crash-window.md) (#170), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169), and [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Second Dispatcher | not present | surefire parent/fork is not multi-node |
| Runtime | leftover honesty inventory only | No Docker / Compose. `#139` / `#170` / `#195` scripts not re-run |

## Commands

```bash
./scripts/g3-outbox-consume-idempotent-honesty.sh
./scripts/g3-outbox-consume-idempotent-honesty.sh --verify-report <out>/report.env
./scripts/g3-outbox-consume-idempotent-honesty.sh --verify-report \
  scripts/g3-outbox-consume-idempotent-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g3-outbox-consume-idempotent-honesty.sh --verify-report \
  scripts/g3-outbox-consume-idempotent-honesty/fixtures/fake-live-crash-pass.env
./scripts/g3-outbox-consume-idempotent-honesty.sh --verify-report \
  scripts/g3-outbox-consume-idempotent-honesty/fixtures/fake-multi-node-pass.env
./scripts/g3-outbox-consume-idempotent-honesty.sh --verify-report \
  scripts/g3-outbox-consume-idempotent-honesty/fixtures/fake-g3-close.env
./scripts/g3-outbox-consume-idempotent-honesty.sh --verify-report \
  scripts/g3-outbox-consume-idempotent-honesty/fixtures/fake-redone.env
```

Do **not** treat `./scripts/g3-outbox-consume-crash-honesty.sh` as this leftover. That script is the #195 contract.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Live output is gitignored under `.local/g3-outbox-consume-idempotent-honesty/`. A sanitized copy is [`g3-outbox-consume-idempotent-honesty/agent-vm.env`](./g3-outbox-consume-idempotent-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | PASS / expected FAIL | leftover consume/crash cells PASS; fake close / fake live PASS / fake redo rejected |
| leftover #139 consume-idempotent | PASS | first `FIRST`; replay `DUPLICATE`; side effect stays 1 |
| leftover #170 crash-window | SIMULATED | first claim committed; inbox UPDATE did not; replay DUPLICATE skips write |
| leftover #195 honesty cells | PASS | parent leftover cells stay honest; not re-run |
| Live process-kill crash | SKIP | `live_crash_observed=false` |
| Multi-node Dispatcher | SKIP | one application JVM; no Kafka/bus |
| Sibling leftover rows | kept | #139 / #170 / #195 + this leftover + #223 `s0-secret-rotate-getenv-remainder-honesty` + #222 `g4-pkce-spa-verifier-remainder-honesty` + #216 `g17-separation-leftover-honesty` + #220 `g15-wx-dt-native-bridge-remainder` + #215 `g1-queryast-export-honesty` + #218 tip-sync and other siblings |
| #139/#170/#195 redo | **false** | sibling leftovers |
| Product G3 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / Outbox core / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Live process-kill after handler claim, before inbox `UPDATE`
- Multi-node Dispatcher / Kafka / bus / production throughput
- Product G3 close
- Redo of #139 / #170 / #195
