# G3 leftover: claim-then-write crash window / multi-node Dispatcher honesty evidence

Recorded on this branch after rebase onto `origin/main` `3cdccca` (Merge #165 S0+S1 claim-list tip). **Not** a product G3 close. **Not** a #139 consume-idempotent redo. **Not** an Outbox / Dispatcher rebuild. **Not** live multi-node Dispatcher. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g3-claim-crash-window.md](../g3-claim-crash-window.md). Keep both leftover rows in [docs/README.md](../README.md): [g3-outbox-consume-idempotent.md](../g3-outbox-consume-idempotent.md) (#139) and this leftover. Sibling leftovers (#161 live multi-JVM, #164 RecoveryPort honesty, #163 H5 every-page, #157 ARM honesty, #165 claim-list tip, #166 export-wizard deny, #167 WeChat/DingTalk prod-gap, #169 file-reconcile honesty) stay documented.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10+7-Ubuntu-124.04 | product baseline |
| Maven | 3.8.7 (or agent PATH) | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| Runtime | local `mvn test` + collect script | No Docker / Compose. No second Dispatcher |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

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

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g3-claim-crash-window/`. A sanitized copy is [`g3-claim-crash-window/agent-vm.env`](./g3-claim-crash-window/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Claim-then-write crash window | **SIMULATED** | first claim committed; inbox UPDATE did not; replay DUPLICATE skips write |
| Live process-kill crash | **SKIP** (expected) | `live_crash_observed=false` |
| Multi-node Dispatcher | **SKIP** (expected) | one application JVM; no Kafka/bus |
| Product G3 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #139 consume leftover | not redone | `g3_139_redone=false` |
| G18 / AuditPort / Outbox core | unchanged | not reopened / not redesigned |

## Honest leftovers

- Live process-kill after handler claim, before inbox `UPDATE`
- Multi-node Dispatcher / Kafka / bus / production throughput
- Product G3 close
