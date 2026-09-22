# G3 leftover — Outbox consume-idempotent / claim-crash-window honesty beyond #139/#170

S0+S1 remainder after the #139 consume-idempotent leftover ([g3-outbox-consume-idempotent.md](./g3-outbox-consume-idempotent.md)) and the #170 claim-crash-window leftover ([g3-claim-crash-window.md](./g3-claim-crash-window.md)). Rebased onto `origin/main` `fb333a3` (Merge #191 G1 BoundSql/COUNT leftover honesty after #198 G15 leftover / #200 S0 rotate leftover / #199 claim-list tip-sync / #192 G7 leftover; includes #193 Linux/ARM leftover honesty, #190 G4 P1 staging audit-events honesty leftover, #194 claim-list tip, #186 G6 RecoveryPort dry-run honesty leftover, #185 live Redis/MinIO honesty leftover, #189 claim-list tip, #187 G17 SoD honesty leftover, and #183 G4 90-day hot-purge honesty leftover). **G18 stays closed.** This leftover does **not** redo #139 consume-idempotent Zonky suites. It does **not** redo #170 crash-window ITs. It does **not** rebuild Outbox / Dispatcher. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim a product G3 close. `g3_close=NOT_CLAIMED`.

Keep **both** leftover rows in [docs/README.md](./README.md): [g3-outbox-consume-idempotent.md](./g3-outbox-consume-idempotent.md) (#139) and [g3-claim-crash-window.md](./g3-claim-crash-window.md) (#170). This file is the PASS/SKIP honesty leftover beside them. Keep sibling leftovers including [g1-queryast-boundsql-honesty.md](./g1-queryast-boundsql-honesty.md) (#191), [g15-wx-dt-native-bridge-honesty.md](./g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](./s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](./g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](./linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](./g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](./g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) (#171), [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md) (#161), and [g6-recovery-port-honesty.md](./g6-recovery-port-honesty.md) (#164). #189 / #194 / #199 claim-list tip alignments stay on `main`.

#139 already proved at-least-once delivery plus `exec_idempotency` skipping a second side effect. #170 already recorded the claim-then-write crash window as **SIMULATED** and multi-node Dispatcher as honest **SKIP**. The leftover here is the **honesty gate** for those remaining paths: the in-memory consume harness may **PASS**; live multi-node Dispatcher and a real process-kill crash window are **PASS** only when observed. Missing second JVM / live kill is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #139 consume harness | In-memory first claim is `FIRST`. Replay storm stays `DUPLICATE`. Side effect stays **1**. `consume_idempotent=PASS`. Not a Zonky / #139 IT redo. |
| #170 crash-window order | Same as `InAppDeliveryHandler`: `claim` first, then inbox `UPDATE`. First claim commits. Write does **not**. Replay `DUPLICATE` skips the write. Inbox stays `PENDING`. `crash_window=SIMULATED`. |
| Live crash | `crash_window=PASS` / `live_crash=PASS` only when `live_crash_observed=true`. This VM records SKIP. |
| Multi-node Dispatcher | Honest **SKIP** unless a distinct second JVM is present **and** `NEXUS_G3_MULTI_NODE_OBSERVED=true`. Surefire parent/fork is not multi-node. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects `g3_close=CLOSED`, rejects #139/#170 redo flags, and requires both leftover rows kept. |

No Docker. No Kafka / bus. No second Dispatcher start. Outbox / Dispatcher core stay as-is. #139 / #170 scripts stay the sibling reproduction commands.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G3 close | Explicitly `g3_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. |
| #139 consume-idempotent redo | Requeue storm / concurrent replay / Zonky ITs stay on that leftover. `g3_139_redone=false`. |
| #170 crash-window redo | Simulated window + multi-node SKIP stay on that leftover. `g3_170_redone=false`. |
| Outbox / Dispatcher rebuild | Existing `OutboxDispatcher` / `JdbcOutboxStore` / lease poller stay as-is. |
| Live multi-node Dispatcher / Kafka / bus | One application JVM. In-process simulation is not a second node. |
| Live process-kill crash recovery | SIMULATED only. A never-written inbox can stay `PENDING`. |
| Redis idempotency keys | Forbidden by ARCH-031. |
| G18 reopen / `AuditPort` / P0/M0 | Closed / untouched / not redone. |

## How to run

```bash
./scripts/g3-outbox-consume-crash-honesty.sh
./scripts/g3-outbox-consume-crash-honesty.sh --verify-report <out>/report.env
./scripts/g3-outbox-consume-crash-honesty.sh --verify-report \
  scripts/g3-outbox-consume-crash-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g3-outbox-consume-crash-honesty.sh --verify-report \
  scripts/g3-outbox-consume-crash-honesty/fixtures/fake-live-crash-pass.env
./scripts/g3-outbox-consume-crash-honesty.sh --verify-report \
  scripts/g3-outbox-consume-crash-honesty/fixtures/fake-multi-node-pass.env
./scripts/g3-outbox-consume-crash-honesty.sh --verify-report \
  scripts/g3-outbox-consume-crash-honesty/fixtures/fake-g3-close.env
```

Always-green Java (does **not** rerun #139 / #170 suites):

```bash
mvn -pl nexus-event-core -am test \
  -Dtest=G3OutboxConsumeCrashHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The sibling leftovers stay siblings:

```bash
./scripts/g3-outbox-consume-idempotent.sh
./scripts/g3-claim-crash-window.sh
```

Do **not** treat those scripts as this leftover. Java 21 / Boot **4.1.1**. No Docker. Live second Dispatcher / live process-kill are **not** required.

See [engineering/g3-outbox-consume-crash-honesty-EVIDENCE.md](./engineering/g3-outbox-consume-crash-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g3-outbox-consume-crash-honesty/`.

## Honest leftovers (not claimed)

- Live process-kill after handler claim, before inbox `UPDATE`
- Multi-node Dispatcher / Kafka / bus / production throughput
- Product G3 close
- Redo of #139 / #170 or Outbox / Dispatcher
