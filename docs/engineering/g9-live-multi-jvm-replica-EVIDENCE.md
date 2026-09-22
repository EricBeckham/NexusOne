# G9 live multi-JVM / replica promotion honesty leftover — evidence

Recorded on this branch after rebase onto `origin/main` (`e66518b`, Merge #162 G1 FieldPolicy mask / second-auth honesty). **Not** a full G9 close. **Not** Sentinel / Cluster product close. **Not** replica promotion / live multi-JVM. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `PolicySnapshot` core from #89 / #83 / #100 / #121 / #128 / #148 was not redesigned.

See [docs/g9-live-multi-jvm-replica.md](../g9-live-multi-jvm-replica.md). Keep both leftover rows in [docs/README.md](../README.md): [g1-fieldpolicy-mask-second-auth.md](../g1-fieldpolicy-mask-second-auth.md) (#162) and this leftover. Sibling leftovers (#148 Sentinel/Cluster honesty, #143 S0 rotate, #155 ops `pg_dump`, #159 G7 withdraw) stay documented.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10+7-Ubuntu-124.04 | product baseline |
| Maven | 3.8.7 (or agent PATH) | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| Redis | not required | replica SKIP when listener / replica missing |
| Runtime | local `mvn test` + collect script | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g9-live-multi-jvm-replica.sh
./scripts/g9-live-multi-jvm-replica.sh --verify-report <out>/report.env
./scripts/g9-live-multi-jvm-replica.sh --verify-report \
  scripts/g9-live-multi-jvm-replica/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-live-multi-jvm-replica.sh --verify-report \
  scripts/g9-live-multi-jvm-replica/fixtures/fake-replica-pass.env
./scripts/g9-live-multi-jvm-replica.sh --verify-report \
  scripts/g9-live-multi-jvm-replica/fixtures/fake-multi-jvm-pass.env
./scripts/g9-live-multi-jvm-replica.sh --verify-report \
  scripts/g9-live-multi-jvm-replica/fixtures/fake-sentinel-cluster-close.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g9-live-multi-jvm-replica/`. A sanitized copy is [`g9-live-multi-jvm-replica/agent-vm.env`](./g9-live-multi-jvm-replica/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Replica promotion | **SKIP** (expected) | no replica / promotion not observed |
| Live multi-JVM partition | **SKIP** (expected) | one application JVM; no iptables / Docker |
| Sentinel / Cluster product close | **NOT_CLAIMED** | #148 inventory not redone |
| Product G9 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #89/#83/#100/#121/#128/#148 | not redone | `g9_89_83_100_121_128_148_redone=false` |
| G18 / AuditPort / PolicySnapshot core | unchanged | not reopened / not redesigned |

## Honest leftovers

- Redis replica promotion on a host that actually has a replica and observed failover
- Live multi-JVM network partition (two JARs / netem)
- Redis Sentinel / Cluster product close (#148)
- Product G9 close
