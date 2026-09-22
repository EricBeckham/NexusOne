# G3 leftover — claim-then-write crash window / multi-node Dispatcher honesty

S0+S1 remainder after submit-start keys ([g3-data-consistency.md](./g3-data-consistency.md)) and the #139 consume-idempotent leftover ([g3-outbox-consume-idempotent.md](./g3-outbox-consume-idempotent.md)). **G18 stays closed.** This leftover does **not** redo #139. It does **not** rebuild Outbox / Dispatcher. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim a product G3 close.

#139 proved at-least-once delivery plus `exec_idempotency` skipping a second side effect. The leftover here is **honesty only** for the remaining claim-then-write crash window and multi-node Dispatcher remainder.

Keep both leftover rows in [docs/README.md](./README.md): `g3-outbox-consume-idempotent` and this file. Keep sibling leftovers including [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md) (#161), [g6-recovery-port-honesty.md](./g6-recovery-port-honesty.md) (#164), [g2-h5-every-page.md](./g2-h5-every-page.md) (#163), [linux-arm-profile-honesty.md](./linux-arm-profile-honesty.md) (#157), [g10-file-reconcile-honesty.md](./g10-file-reconcile-honesty.md) (#169), [g4-export-wizard-deny.md](./g4-export-wizard-deny.md) (#166), and [g15-wx-dt-prod-gap-honesty.md](./g15-wx-dt-prod-gap-honesty.md) (#167). #165 claim-list tip alignment stays on `main`.

## What this leftover covers

| Path | Behavior |
|---|---|
| Claim-then-write order | Same as `InAppDeliveryHandler`: `claim` first, then inbox `UPDATE`. |
| Simulated crash window | First claim commits. Inbox write does **not**. Replay sees `DUPLICATE` and skips the write. Inbox stays `PENDING`. Side effect stays lost. Recorded as **SIMULATED**, not a live process kill. |
| Live crash | `crash_window=PASS` only when `live_crash_observed=true`. This VM records SIMULATED. |
| Multi-node Dispatcher | Honest **SKIP** unless a distinct second JVM is present **and** `NEXUS_G3_MULTI_NODE_OBSERVED=true`. Surefire parent/fork is not multi-node. |
| Honesty gate | `--verify-report` rejects fake PASS, rejects `g3_close=CLOSED`, rejects a #139 redo flag. |

No Docker. No Kafka / bus. No second Dispatcher start. Outbox / Dispatcher core stay as-is.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G3 close | Explicitly `g3_close=NOT_CLAIMED`. |
| #139 consume-idempotent redo | Requeue storm / concurrent replay stay on that leftover. |
| Outbox / Dispatcher rebuild | Existing `OutboxDispatcher` / `JdbcOutboxStore` / lease poller stay as-is. |
| Live multi-node Dispatcher / Kafka / bus | One application JVM. In-process simulation is not a second node. |
| Production throughput / 30s soak | Not a load run. |
| Live process-kill crash recovery | SIMULATED only. A never-written inbox can stay `PENDING`. |
| Redis idempotency keys | Forbidden by ARCH-031. |
| G18 reopen / `AuditPort` / P0/M0 | Closed / untouched / not redone. |

## How to run

```bash
./scripts/g3-claim-crash-window.sh
./scripts/g3-claim-crash-window.sh --verify-report <out>/report.env
./scripts/g3-claim-crash-window.sh --verify-report \
  scripts/g3-claim-crash-window/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g3-claim-crash-window.sh --verify-report \
  scripts/g3-claim-crash-window/fixtures/fake-crash-window-pass.env
./scripts/g3-claim-crash-window.sh --verify-report \
  scripts/g3-claim-crash-window/fixtures/fake-multi-node-pass.env
./scripts/g3-claim-crash-window.sh --verify-report \
  scripts/g3-claim-crash-window/fixtures/fake-g3-close.env
```

Always-green Java (does not redo #139 suites):

```bash
mvn -pl nexus-event-core -am test \
  -Dtest=G3ClaimCrashWindowHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Zonky / live second Dispatcher are **not** required. Java 21 / Boot **4.1.1**. No Docker.

See [engineering/g3-claim-crash-window-EVIDENCE.md](./engineering/g3-claim-crash-window-EVIDENCE.md).

## Honest leftovers (not claimed)

- Live process-kill after handler claim, before inbox `UPDATE`
- Multi-node Dispatcher / Kafka / bus / production throughput
- Product G3 close
- Redesigning Outbox / Dispatcher or redoing #139
