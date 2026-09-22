# G9 leftover — live multi-JVM / replica remainder honesty beyond #209/#161/#148

Recorded on this branch after rebase onto `origin/main` `323b22d` (Merge #226 G3 Outbox consume-idempotent leftover honesty after #228 G10 leftover honesty remainder / #221 G2 report/physical-dump leftover honesty / #225 G6 RecoveryPort dry-run remainder leftover honesty / #224 S0+S1 claim-list tip-sync / #223 S0 rotate/getenv remainder leftover honesty / #222 G4 SPA verifier remainder leftover honesty / #216 G17 SoD leftover honesty / #218 S0+S1 claim-list tip-sync / #220 G15 wx-dt/native honesty remainder / #215 / #210 / #217 / #209). **Not** a product G9 close. **Not** a Sentinel / Cluster product close. **Not** a #83/#100/#121/#128/#148/#161/#174/#209 redo. **Not** live replica promotion. **Not** live multi-JVM. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `PolicySnapshot` core was not redesigned.

See [docs/g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g9-sentinel-cluster-honesty.md](../g9-sentinel-cluster-honesty.md) (#148), [g9-live-multi-jvm-replica.md](../g9-live-multi-jvm-replica.md) (#161), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline; leftover does not boot the JAR |
| Maven | `$HOME/tools/apache-maven-3.8.7` when present | collect may `--skip-tests` |
| Spring Boot parent | **4.1.1** | product BOM |
| Redis replica / second JVM | not present | honest SKIP when unobserved |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh --verify-report \
  scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh --verify-report \
  scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/fake-replica-pass.env
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh --verify-report \
  scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/fake-multi-jvm-pass.env
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh --verify-report \
  scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/fake-g9-close.env
./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh --verify-report \
  scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/fake-sentinel-cluster-close.env
```

## Results (agent VM)

Filled after collect on this VM. Live output is gitignored under `.local/g9-live-multi-jvm-replica-remainder-honesty/`. Sanitized copy: [`g9-live-multi-jvm-replica-remainder-honesty/agent-vm.env`](./g9-live-multi-jvm-replica-remainder-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake **FAIL** | honest-skip PASS; fake-replica / fake-multi-jvm / fake-g9-close / fake-sentinel-cluster-close FAIL |
| Standalone INFO replication inventory stub | **PASS** | `role=master` `connected_slaves=0`; surefire is not a second JVM |
| Replica promotion | **SKIP** | `promotion_observed=false`; no replica |
| Live multi-JVM | **SKIP** | `multi_jvm_observed=false`; one application JVM |
| Sibling leftover rows | **kept** | includes #148/#161/#209/#223/#222/#216/#220/#215/#210/#217/#188 |
| Java honesty tests | leftover suite only | #83/#100/#121/#128/#148/#161/#174/#209 not rerun |
| Product G9 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| Sentinel / Cluster product close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / PolicySnapshot core / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Redis replica promotion on a host that actually has a replica and observed failover
- Live multi-JVM network partition (two JARs / netem)
- Redis Sentinel / Cluster product close (#148 / #209)
- Product G9 close
