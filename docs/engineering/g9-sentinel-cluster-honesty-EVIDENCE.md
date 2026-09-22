# G9 Sentinel / Cluster / replica promotion honesty leftover — evidence

Recorded on this branch after rebase onto `origin/main` (`e0a409a`, includes #141 G10 identity drift-fix dry-run, #144 isolation-only catalog, #142 DataScope IN, #139 Outbox consume-idempotent). **Not** a full G9 close. **Not** Sentinel / Cluster / replica promotion / live multi-JVM. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `PolicySnapshot` core from #89 / #83 / #100 / #121 / #128 was not redesigned.

See [docs/g9-sentinel-cluster-honesty.md](../g9-sentinel-cluster-honesty.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10+7-Ubuntu-124.04 | product baseline |
| Maven | 3.8.7 | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| Redis | not listening on `127.0.0.1:6379` | `redis-cli` / `redis-sentinel` not installed |
| Runtime | local `mvn test` + collect script | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g9-sentinel-cluster-honesty.sh
./scripts/g9-sentinel-cluster-honesty.sh --verify-report <out>/report.env
./scripts/g9-sentinel-cluster-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-sentinel-cluster-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-honesty/fixtures/fake-sentinel-pass.env
./scripts/g9-sentinel-cluster-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-honesty/fixtures/fake-g9-close.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g9-sentinel-cluster/`. A sanitized copy is [`g9-sentinel-cluster/agent-vm.txt`](./g9-sentinel-cluster/agent-vm.txt) once recorded.

| Check | Result | Notes |
|---|---|---|
| In-process #128 dual-reader | pending record | same `InMemoryPolicyRepository` / `SnapshotManager` |
| Sentinel failover | **SKIP** (expected) | no `redis-sentinel`; no `:26379` |
| Cluster failover | **SKIP** (expected) | no Redis Cluster |
| Replica promotion | **SKIP** (expected) | no replica |
| Live multi-JVM partition | **SKIP** (expected) | one JVM; no iptables / Docker |
| Product G9 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / PolicySnapshot core | unchanged | not reopened / not redesigned |

## Honest leftovers

- Redis Sentinel quorum + automatic failover on a host that actually has that topology
- Redis Cluster slot failover / replica promotion
- Live multi-JVM network partition
- Product G9 close
