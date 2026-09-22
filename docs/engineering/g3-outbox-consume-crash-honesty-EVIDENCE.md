# G3 leftover — Outbox consume-idempotent / claim-crash-window honesty beyond #139/#170

Recorded on this branch after rebase onto `origin/main` `724f5f0` (Merge #198 G15 WeChat/DingTalk + native-bridge leftover honesty after #200 S0 rotate leftover / #199 claim-list tip-sync / #192 G7 leftover; includes #193 Linux/ARM leftover honesty, #190 G4 P1 staging audit-events honesty leftover, #194 claim-list tip, #186 G6 RecoveryPort dry-run honesty leftover, #185 live Redis/MinIO honesty leftover, #189 claim-list tip, #187 G17 SoD honesty leftover, and #183 G4 90-day hot-purge honesty leftover). **Not** a product G3 close. **Not** a #139 consume-idempotent redo. **Not** a #170 crash-window redo. **Not** an Outbox / Dispatcher rebuild. **Not** live multi-node Dispatcher. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md). Keep **both** leftover rows in [docs/README.md](../README.md): [g3-outbox-consume-idempotent.md](../g3-outbox-consume-idempotent.md) (#139) and [g3-claim-crash-window.md](../g3-claim-crash-window.md) (#170). Sibling leftovers stay documented: [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g9-live-multi-jvm-replica.md](../g9-live-multi-jvm-replica.md) (#161), [g6-recovery-port-honesty.md](../g6-recovery-port-honesty.md) (#164).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline; leftover does not boot the JAR |
| Maven | `$HOME/tools/apache-maven-3.8.7` when present | collect may `--skip-tests` |
| Spring Boot parent | **4.1.1** | product BOM |
| Second Dispatcher | not present | surefire parent/fork is not multi-node |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

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

## Results (agent VM)

Filled after collect on this VM (`5cfe732` rebased onto `724f5f0`). Live output is gitignored under `.local/g3-outbox-consume-crash-honesty/`. Sanitized copy: [`g3-outbox-consume-crash-honesty/agent-vm.env`](./g3-outbox-consume-crash-honesty/agent-vm.env).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake **FAIL** | honest-skip PASS; fake-live-crash / fake-multi-node / fake-g3-close FAIL |
| Consume-idempotent harness | **PASS** | first `FIRST`; replay `DUPLICATE`; side effect stays 1 |
| Claim-then-write crash window | **SIMULATED** | first claim committed; inbox UPDATE did not; replay DUPLICATE skips write |
| Live process-kill crash | **SKIP** | `live_crash_observed=false` |
| Multi-node Dispatcher | **SKIP** | one application JVM; no Kafka/bus |
| Both leftover rows | **kept** | `g3-outbox-consume-idempotent` + `g3-claim-crash-window` (siblings including #185/#186/#187/#190/#192/#193/#194/#198/#199/#200 stay) |
| Java honesty tests | **9/9** | `G3OutboxConsumeCrashHonestyTest` only; #139/#170 not rerun |
| #139/#170 redo | **false** | sibling leftovers |
| Product G3 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / Outbox core / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Live process-kill after handler claim, before inbox `UPDATE`
- Multi-node Dispatcher / Kafka / bus / production throughput
- Product G3 close
