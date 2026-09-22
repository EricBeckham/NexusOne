# G9 leftover — Redis-down / PolicySnapshot invalidate remainder honesty beyond #197/#174/#83/#121 — evidence

Recorded on this branch after rebase onto `origin/main` `dceb6c0` (Merge #232 G2 H5/form.write leftover honesty remainder after #212 G7 withdraw browser-evidence leftover honesty / #230 G4 hot-purge / P1 staging leftover honesty / #231 S0+S1 claim-list tip-sync). **Not** a product G9 close. **Not** a redo of #83 / #121 / #174 / #197. **Not** live Redis process-down. **Not** live PolicySnapshot invalidate. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `PolicySnapshot` core was not redesigned. P0/M0 were not redone.

See [docs/g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g9-policysnapshot-invalidate.md](../g9-policysnapshot-invalidate.md) (#83), [g9-failover-remainder.md](../g9-failover-remainder.md) (#121), [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) (#174), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169), and [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Redis | not required | live Redis-down / live invalidate SKIP when listener missing or walk unobserved |
| Runtime | leftover honesty remainder inventory only | No Docker / Compose. `#83` / `#121` / `#174` / `#197` scripts not re-run |

## Commands

```bash
./scripts/g9-redis-down-invalidate-remainder-honesty.sh
./scripts/g9-redis-down-invalidate-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g9-redis-down-invalidate-remainder-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g9-redis-down-invalidate-remainder-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder-honesty/fixtures/fake-live-redis-down-pass.env
./scripts/g9-redis-down-invalidate-remainder-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder-honesty/fixtures/fake-live-invalidate-pass.env
./scripts/g9-redis-down-invalidate-remainder-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder-honesty/fixtures/fake-g9-close.env
./scripts/g9-redis-down-invalidate-remainder-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder-honesty/fixtures/fake-redone.env
```

Do **not** treat `./scripts/g9-redis-down-invalidate-remainder.sh` or `./scripts/g9-redis-down-invalidate-honesty.sh` as this leftover. Those scripts are the #197 / #174 contracts. Parent `--verify-report` on honest fixtures is harness evidence.

## Results (agent VM)

Filled after the leftover remainder honesty script on this VM at `2026-09-22T21:49:56Z`. Live output is gitignored under `.local/g9-redis-down-invalidate-remainder-honesty/`. A sanitized copy is [`g9-redis-down-invalidate-remainder-honesty/agent-vm.env`](./g9-redis-down-invalidate-remainder-honesty/agent-vm.env).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live Redis-down / live invalidate PASS, fake `g9_close=CLOSED`, and fake #197 redo rejected (`2026-09-22T21:49:56Z`) |
| leftover #83 / #121 cells still honest | PASS | dead client `:1` stays a simulation, not a live crash |
| leftover #174 cells still honest | PASS | parent leftover honesty fixtures stay PASS |
| leftover #197 cells still honest | PASS | parent leftover remainder fixtures stay PASS |
| Dead-client harness (port `1`) | PASS | leftover remainder source + #197 fixture; not remote success |
| Live Redis-down | SKIP (expected) | no listener / process-down not observed; dead-client PASS is not live-down |
| Live PolicySnapshot invalidate | SKIP (expected) | no observed PUBLISH/SUBSCRIBE on `:6379` |
| Dead client is live Redis-down | **false** | `#83` / `#121` simulation is not a live crash |
| Sibling leftover rows | kept | #83 / #121 / #174 / #197 + this leftover + #229 / #230 / #212 / #232 / #226 / #228 / #221 / #225 siblings |
| #83/#121/#174/#197 redo | **false** | sibling leftovers |
| Product G9 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / PolicySnapshot core / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Live Redis 3.2 process-down mid-flight on a host that actually has Redis
- Live PolicySnapshot PUBLISH/SUBSCRIBE invalidate walk
- Redis Sentinel / Cluster / replica promotion / live multi-JVM (#148 / #161 / #209)
- Product G9 close
- Redo of #83 / #100 / #121 / #128 / #148 / #161 / #174 / #197
