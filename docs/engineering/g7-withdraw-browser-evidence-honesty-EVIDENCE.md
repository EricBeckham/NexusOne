# G7 leftover: browser-visible withdraw evidence honesty

Recorded on this branch after rebase onto `origin/main` `97a2adc` (Merge #231 claim-list tip-sync after #229 G9 live multi-JVM/replica remainder leftover after #226 G3 consume-idempotent leftover after #228 G10 leftover honesty remainder after #221 G2 report/physical-dump SurfaceGuard leftover; includes #225 / #223 / #222 / #216 / #218 / #220 / #215 / #210 / #217 / #209 / #188 / #205 / #197 / #204). **Not** a product G7 close. **Not** a redo of #82 / #103 / #113 / #140 / #159 / #175 / #192. **Not** a live product-JAR / live IdP withdraw click. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md), including [g7-withdraw-evidence.md](../g7-withdraw-evidence.md) (#82), [g7-withdraw-expense-travel.md](../g7-withdraw-expense-travel.md) (#103), [g7-withdraw-browser.md](../g7-withdraw-browser.md) (#113/#159), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), and [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
./scripts/g7-withdraw-browser-evidence-honesty.sh
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report <out>/report.env
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-live-pass.env fail
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-g7-close.env fail
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-redone.env fail
./scripts/g7-withdraw-browser-evidence-honesty.sh --verify-report \
  scripts/g7-withdraw-browser-evidence-honesty/fixtures/fake-stub-as-live.env fail
```

Do **not** treat `./scripts/g7/run-withdraw-browser.sh` or `./scripts/g7-withdraw-browser-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g7-withdraw-browser-evidence-honesty/`. A sanitized copy is [`g7-withdraw-browser-evidence-honesty/agent-vm.env`](./g7-withdraw-browser-evidence-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** / fake live PASS rejected / fake `g7_close=CLOSED` rejected / fake redo rejected / fake stub-as-live rejected | `2026-09-22T21:42:42Z` after rebase onto `97a2adc` |
| Leave / expense / travel withdraw HTTP | **PASS** (gate) | #82/#103 leftover docs still present; not rerun |
| Vue `todo-withdraw` + HTTP path | **PASS** | #113/#159 control still present |
| #175 leftover honesty | **PASS** | parent honest-skip fixture still verifies |
| #192 query leftover honesty | **PASS** | sibling leftover; withdraw paths not redone |
| Browser-visible stub walk | **PASS** | leave + expense + travel; stub ≠ live |
| Screenshot artifacts | **PASS** | stub-login / 我发起 / todo-withdraw / WITHDRAWN |
| leftover rows kept | **true** | parents + #229 G9 live multi-JVM/replica remainder leftover + #226 G3 consume-idempotent leftover + #228 G10 leftover honesty remainder + #221 G2 report/physical-dump honesty + #225 G6 RecoveryPort dry-run remainder + #223 S0 rotate/getenv remainder + #222 G4 PKCE SPA verifier remainder + #216 G17 SoD leftover honesty + #220 G15 wx-dt/native-bridge remainder + #215 G1 QueryAst/export honesty + #210 G17 GMTLS handshake honesty + #217 G5 PublishHook DDL honesty + #209 G9 Sentinel/Cluster remainder honesty + #188 G2 ops pg_dump honesty + #205 G17 MFA recovery/OTP honesty + #197 G9 Redis-down remainder + #204 G2 H5/form.write honesty + all sibling leftover rows |
| AuditPort same-TX | **PASS** | untouched |
| Live JAR/IdP browser withdraw | **SKIP** (expected) | `NEXUS_BASE_URL` unset; nothing on `:8080` / `:8088` |
| Live browser-visible withdraw | **SKIP** (expected) | stub walk is not a live walk |
| Live browser query click | **SKIP** (expected) | still open; not this leftover |
| #82/#103/#113/#140/#159/#175/#192 redo | **false** | sibling leftovers |
| Product G7 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR + live/Keycloak IdP Playwright click of `todo-withdraw`
- Live browser-visible withdraw on leave + expense + travel
- Live browser query click for expense / travel
- Three-type product G7 close
- Reopening G18 or rewriting `AuditPort` same-TX joining
