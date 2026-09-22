# G9 leftover: Sentinel / multi-JVM / Redis-down leftover honesty remainder evidence beyond #237/#229/#209/#197

See [docs/g9-leftover-honesty-remainder.md](../g9-leftover-honesty-remainder.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [g17-separation-leftover-honesty-remainder.md](../g17-separation-leftover-honesty-remainder.md) (#249), [g15-h5-pkce-miniprogram-remainder-honesty.md](../g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g2-h5-every-page-form-write-report-remainder-honesty.md](../g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty.md](../g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty.md](../g10-file-object-reconcile-remainder-honesty.md) (#243), [s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g5-publishhook-ddl-remainder-honesty.md](../g5-publishhook-ddl-remainder-honesty.md) (#235), [g17-mfa-recovery-vue-otp-remainder-honesty.md](../g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), and every leftover-style sibling already on `origin/main`. Keep this leftover.

Recorded on this agent VM after rebase onto `origin/main` `b05cdf7` (Merge #249 G17 SoD/admin-force leftover honesty after #240 / #254 / #236 / #251 / #243 / #241 / #235). **Not** a product G9 close. **Not** Sentinel/Cluster close. **Not** a G18 reopen. `AuditPort` same-TX rules were not changed. `PolicySnapshot` was not redesigned. `#237` / `#229` / `#209` / `#197` were not redone. P0 / M0 were not redone.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline |
| Maven | **3.8.x** when present | leftover-off; collect `--skip-tests` when mvn is missing |
| Spring Boot parent | **4.1.1** | product BOM |
| Redis Sentinel / Cluster / replica / live down | not observed | honest SKIP |
| Runtime | `./scripts/g9-leftover-honesty-remainder.sh` | No Docker / Compose |

## Commands

```bash
./scripts/g9-leftover-honesty-remainder.sh
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/fake-live-redis-down-pass.env
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/fake-live-invalidate-pass.env
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/fake-sentinel-pass.env
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/fake-multi-jvm-pass.env
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/fake-g9-close.env
./scripts/g9-leftover-honesty-remainder.sh --verify-report \
  scripts/g9-leftover-honesty-remainder/fixtures/fake-redone.env
```

Do **not** treat `./scripts/g9-redis-down-invalidate-remainder-honesty.sh`, `./scripts/g9-live-multi-jvm-replica-remainder-honesty.sh`, `./scripts/g9-sentinel-cluster-remainder-honesty.sh`, or `./scripts/g9-redis-down-invalidate-remainder.sh` as this leftover. Those scripts are the #237 / #229 / #209 / #197 contracts.

## Results (agent VM)

Filled after the leftover honesty remainder script on this VM. Live output is gitignored under `.local/g9-leftover-honesty-remainder/`. A sanitized copy is [`g9-leftover-honesty-remainder/agent-vm.env`](./g9-leftover-honesty-remainder/agent-vm.env).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live Redis-down / invalidate / Sentinel / multi-JVM PASS / fake G9 close / redo rejected |
| #237 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| #229 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| #209 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| #197 leftover honesty fixture | **PASS** (gate) | parent `--verify-report` only; not a product redo |
| Live Redis-down | **SKIP** | no Redis process-down observed; dead client `:1` is not live-down |
| Live PolicySnapshot invalidate | **SKIP** | no observed PUBLISH/SUBSCRIBE |
| Live Sentinel / Cluster failover | **SKIP** | no failover observed |
| Live replica promotion / multi-JVM | **SKIP** | surefire is not a live partition |
| Product G9 / Sentinel/Cluster close | **NOT_CLAIMED** | leftovers stay open |
| Leftover rows kept | **true** | #237 + #229 + #209 + #197 + ALL sibling leftover rows including #249 / #240 / #254 / #236 / #251 / #243 / #241 / #235 / #233 |
| #237 / #229 / #209 / #197 | unchanged | not redone |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live Redis 3.2 process-down mid-flight on a host that actually has Redis
- Live PolicySnapshot PUBLISH/SUBSCRIBE invalidate walk
- Redis Sentinel / Cluster failover
- Replica promotion / live multi-JVM network partition
- Product G9 close
- Redo of #83 / #100 / #121 / #128 / #148 / #161 / #174 / #197 / #209 / #229 / #237
