# G9 leftover — Sentinel / Cluster remainder honesty beyond #148/#161

Recorded on this branch after rebase onto `origin/main` `eff62d8` (Merge #214 tip-sync after #188 G2 ops pg_dump leftover honesty / #211 tip-sync / #205 G17 MFA recovery/OTP leftover honesty / #197 G9 Redis-down invalidate remainder leftover honesty / #204 G2 H5/form.write leftover honesty / #206 tip-sync / #203 G4 export-wizard deny leftover honesty / #202 G10 file-reconcile / identity-drift leftover honesty / #196 G5 mid-publish / browser honesty / #195 G3 consume-crash honesty). **Not** a product G9 close. **Not** a #83/#100/#121/#128/#148/#161/#174 redo. **Not** live Sentinel failover. **Not** live Cluster failover. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `PolicySnapshot` core was not redesigned.

See [docs/g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g9-sentinel-cluster-honesty.md](../g9-sentinel-cluster-honesty.md) (#148), [g9-live-multi-jvm-replica.md](../g9-live-multi-jvm-replica.md) (#161), [g9-policysnapshot-invalidate.md](../g9-policysnapshot-invalidate.md) (#83), [g9-failover-remainder.md](../g9-failover-remainder.md) (#121), [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) (#174), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md) (#203), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline; leftover does not boot the JAR |
| Maven | `$HOME/tools/apache-maven-3.8.7` when present | collect may `--skip-tests` |
| Spring Boot parent | **4.1.1** | product BOM |
| Redis Sentinel / Cluster | not present | honest SKIP when unobserved |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g9-sentinel-cluster-remainder-honesty.sh
./scripts/g9-sentinel-cluster-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g9-sentinel-cluster-remainder-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-sentinel-cluster-remainder-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-remainder-honesty/fixtures/fake-sentinel-pass.env
./scripts/g9-sentinel-cluster-remainder-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-remainder-honesty/fixtures/fake-cluster-pass.env
./scripts/g9-sentinel-cluster-remainder-honesty.sh --verify-report \
  scripts/g9-sentinel-cluster-remainder-honesty/fixtures/fake-g9-close.env
```

## Results (agent VM)

Filled after collect on this VM. Live output is gitignored under `.local/g9-sentinel-cluster-remainder-honesty/`. Sanitized copy: [`g9-sentinel-cluster-remainder-honesty/agent-vm.env`](./g9-sentinel-cluster-remainder-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake **FAIL** | honest-skip PASS; fake-sentinel / fake-cluster / fake-g9-close FAIL |
| Standalone INFO / SENTINEL\|CLUSTER inventory stub | **PASS** | `redis_mode=standalone` `role=master` `connected_slaves=0`; no SENTINEL/CLUSTER command |
| Live Sentinel failover | **SKIP** | `sentinel_failover_observed=false` |
| Live Cluster failover | **SKIP** | `cluster_failover_observed=false` |
| Sibling leftover rows | **kept** | includes #148/#161/#174/#195/#196/#202/#203/#204/#197/#205/#188 |
| Java honesty tests | leftover suite only | #83/#100/#121/#128/#148/#161/#174 not rerun |
| Product G9 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / PolicySnapshot core / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Redis Sentinel quorum + automatic failover on a host that actually has that topology
- Redis Cluster slot failover / replica promotion
- Live multi-JVM network partition
- Product G9 close
