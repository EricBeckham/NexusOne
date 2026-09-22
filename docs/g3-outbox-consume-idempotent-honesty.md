# G3 leftover — Outbox consume-idempotent / claim-crash-window honesty beyond #195/#139/#170

S0+S1 remainder **after** the #195 consume-idempotent / claim-crash-window leftover honesty ([g3-outbox-consume-crash-honesty.md](./g3-outbox-consume-crash-honesty.md)), the #139 consume-idempotent leftover ([g3-outbox-consume-idempotent.md](./g3-outbox-consume-idempotent.md)), and the #170 claim-crash-window leftover ([g3-claim-crash-window.md](./g3-claim-crash-window.md)). Rebased onto `origin/main` `1bccc47` (Merge #228 G10 leftover honesty remainder after #221 G2 report/physical-dump leftover honesty / #225 G6 RecoveryPort leftover honesty / #224 S0+S1 claim-list tip-sync / #223 S0 rotate/getenv remainder leftover honesty / #222 G4 live Keycloak CA / SPA PKCE remainder leftover honesty / #216 G17 SoD leftover honesty / #218 S0+S1 claim-list tip-sync / #220 G15 wx-dt/native-bridge remainder leftover honesty / #215 G1 BoundSql/export leftover honesty). **G18 stays closed.** This leftover does **not** redo #139 consume-idempotent Zonky suites. It does **not** redo #170 crash-window ITs. It does **not** redo #195 leftover honesty product code. It does **not** rebuild Outbox / Dispatcher. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** redo P0/M0. It does **not** claim a product G3 close. `g3_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g3-outbox-consume-idempotent.md](./g3-outbox-consume-idempotent.md) (#139), [g3-claim-crash-window.md](./g3-claim-crash-window.md) (#170), [g3-outbox-consume-crash-honesty.md](./g3-outbox-consume-crash-honesty.md) (#195), [g10-leftover-honesty-remainder.md](./g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty.md](./g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty.md](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty.md](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](./g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](./g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](./g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty.md](./g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty.md](./g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty.md](./g17-mfa-recovery-vue-otp-honesty.md) (#205), [g9-redis-down-invalidate-remainder.md](./g9-redis-down-invalidate-remainder.md) (#197), [g2-h5-every-page-form-write-honesty.md](./g2-h5-every-page-form-write-honesty.md) (#204), [g4-export-wizard-deny-honesty.md](./g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty.md](./g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty.md](./g5-mid-publish-failure-honesty.md) (#196), [g15-wx-dt-native-bridge-honesty.md](./g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](./s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](./g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](./linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](./g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](./g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g1-queryast-boundsql-honesty.md](./g1-queryast-boundsql-honesty.md) (#191), [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) (#171), [g7-withdraw-browser-honesty.md](./g7-withdraw-browser-honesty.md) (#175), [g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md) (#169), and [g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md) (#179). Keep this file.

#195 already locked consume-idempotent harness as **PASS** (first `FIRST`, replay `DUPLICATE`, side effect stays 1) and the claim-then-write crash window as **SIMULATED**. #139 already proved at-least-once delivery plus `exec_idempotency` skipping a second side effect. #170 already recorded the crash window as **SIMULATED** and multi-node Dispatcher as honest **SKIP**. The leftover here is the **honesty gate beyond those three**: those cells stay honest; live multi-node Dispatcher and a real process-kill crash window are **PASS** only when observed. Missing second JVM / live kill is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #139 consume harness | Committed first claim is `FIRST`. Replay storm stays `DUPLICATE`. Side effect stays **1**. `leftover_139_cells_honest=PASS`. `consume_idempotent=PASS`. Not a Zonky / #139 IT redo. |
| #170 crash-window order | Same as `InAppDeliveryHandler`: `claim` first, then inbox `UPDATE`. First claim commits. Write does **not**. Replay `DUPLICATE` skips the write. Inbox stays `PENDING`. `leftover_170_cells_honest=PASS`. `crash_window=SIMULATED`. |
| #195 honesty cells | Committed consume harness PASS + crash SIMULATED + live SKIP stay honest. `leftover_195_cells_honest=PASS`. Do not redo. |
| Live crash | `crash_window=PASS` / `live_crash=PASS` only when `live_crash_observed=true`. This VM records SKIP. |
| Multi-node Dispatcher | Honest **SKIP** unless a distinct second JVM is present **and** `NEXUS_G3_MULTI_NODE_OBSERVED=true`. Surefire parent/fork is not multi-node. |
| Sibling leftover rows | `docs/README.md` keeps #139 / #170 / #195 and this leftover, plus #228 `g10-leftover-honesty-remainder`, #221 `g2-report-physical-dump-honesty`, #225 `g6-recovery-port-dry-run-remainder-honesty`, #223 `s0-secret-rotate-getenv-remainder-honesty`, #222 `g4-pkce-spa-verifier-remainder-honesty`, #216 `g17-separation-leftover-honesty`, #220 `g15-wx-dt-native-bridge-remainder`, #215 `g1-queryast-export-honesty`, #218 tip-sync, and the other leftover rows listed above. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g3_close=CLOSED`, rejects #139/#170/#195 redo flags, and requires leftover rows kept. |

No Docker. No Kafka / bus. No second Dispatcher start. Outbox / Dispatcher core stay as-is. `#139` / `#170` / `#195` stay the sibling reproduction commands.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G3 close | Explicitly `g3_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| #139 consume-idempotent redo | Requeue storm / concurrent replay / Zonky ITs stay on that leftover. `redone_139=false`. |
| #170 crash-window redo | Simulated window + multi-node SKIP stay on that leftover. `redone_170=false`. |
| #195 honesty redo | Parent honesty leftover stays sibling. `redone_195=false`. |
| Outbox / Dispatcher rebuild | Existing `OutboxDispatcher` / `JdbcOutboxStore` / lease poller stay as-is. |
| Live multi-node Dispatcher / Kafka / bus | One application JVM. In-process simulation is not a second node. |
| Live process-kill crash recovery | SIMULATED only. A never-written inbox can stay `PENDING`. |
| Redis idempotency keys | Forbidden by ARCH-031. |
| G18 reopen / `AuditPort` / P0/M0 | Closed / untouched / not redone. |

## How to run

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

#139 / #170 / #195 stay the sibling reproduction commands. This leftover does **not** invoke them:

```bash
# do not run for this leftover
# ./scripts/g3-outbox-consume-crash-honesty.sh
# ./scripts/g3-outbox-consume-idempotent.sh
# ./scripts/g3-claim-crash-window.sh
```

Java 21 / Boot **4.1.1**. No Docker. Live second Dispatcher / live process-kill are **not** required.

See [engineering/g3-outbox-consume-idempotent-honesty-EVIDENCE.md](./engineering/g3-outbox-consume-idempotent-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g3-outbox-consume-idempotent-honesty/`.

## Honest leftovers (not claimed)

- Live process-kill after handler claim, before inbox `UPDATE`
- Multi-node Dispatcher / Kafka / bus / production throughput
- Product G3 close
- Redo of #139 / #170 / #195 or Outbox / Dispatcher
