# G1 leftover — FieldPolicy mask / BoundSql export remainder honesty beyond #238/#215/#184 — evidence

Recorded on this branch after rebase onto `origin/main` `6c224e1` (Merge #260 G5 leftover honesty after #256 G7 query/withdraw leftover honesty / #265 G4 leftover honesty / #263 Linux/ARM leftover remainder honesty / #262 G10 file/object leftover honesty / #259 G9 leftover honesty). **Not** a product G1 close. **Not** a redo of #115 / #130 / #134 / #142 / #146 / #162 / #168 / #184 / #215 / #238. **Not** IsolatedExport applying masks. **Not** a live product-JAR FieldPolicy / BoundSql walk. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0/M0 were not redone.

See [docs/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.md](../g1-fieldpolicy-mask-boundsql-export-remainder-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md), including [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](../g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g5-publishhook-ddl-leftover-honesty.md](../g5-publishhook-ddl-leftover-honesty.md) (#260), [g7-query-withdraw-leftover-honesty.md](../g7-query-withdraw-leftover-honesty.md) (#256), [g4-hot-purge-staging-pkce-leftover-honesty.md](../g4-hot-purge-staging-pkce-leftover-honesty.md) (#265), [linux-arm-profile-leftover-remainder-honesty.md](../linux-arm-profile-leftover-remainder-honesty.md) (#263), [g10-file-object-reconcile-leftover-honesty.md](../g10-file-object-reconcile-leftover-honesty.md) (#262), [g9-leftover-honesty-remainder.md](../g9-leftover-honesty-remainder.md) (#259), [g17-separation-leftover-honesty-remainder.md](../g17-separation-leftover-honesty-remainder.md) (#249), [g15-h5-pkce-miniprogram-remainder-honesty.md](../g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g2-h5-every-page-form-write-report-remainder-honesty.md](../g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty.md](../g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty.md](../g10-file-object-reconcile-remainder-honesty.md) (#243), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), [g17-mfa-recovery-vue-otp-remainder-honesty.md](../g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g5-publishhook-ddl-remainder-honesty.md](../g5-publishhook-ddl-remainder-honesty.md) (#235), [s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md) (#241), and every leftover-style sibling already on `origin/main`.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| IsolatedExport mask | not observed | granted export still writes raw cells (#162/#184) |
| Runtime | leftover honesty remainder inventory only | No Docker / Compose. `#184` / `#215` / `#238` scripts not re-run |

## Commands

```bash
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-live-api-mask-pass.env fail
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-live-export-mask-pass.env fail
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-live-second-auth-pass.env fail
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-live-boundsql-pass.env fail
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-g1-close.env fail
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-redone.env fail
./scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/fixtures/fake-harness-as-live.env fail
```

Do **not** treat `./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh` or `./scripts/g1-queryast-export-honesty.sh` as this leftover. Those scripts are the #238 / #215 contracts. Parent `--verify-report` on honest fixtures is harness evidence. `#238` harness PASS is **not** a live product-JAR / IsolatedExport mask / BoundSql walk.

## Results (agent VM)

Filled after the leftover honesty remainder script on this VM. Live output is gitignored under `.local/g1-fieldpolicy-mask-boundsql-export-remainder-honesty/`. A sanitized copy is [`g1-fieldpolicy-mask-boundsql-export-remainder-honesty/agent-vm.env`](./g1-fieldpolicy-mask-boundsql-export-remainder-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | PASS | leftover #162/#184/#215/#238 cells PASS; fake close / fake live PASS / fake redo / fake harness-as-live rejected |
| leftover #162 leftoverMaskFields | PASS | mask / 二次授权 stay on `FieldPolicyProjector` beside Query AST |
| leftover #184 honesty cells | PASS | parent leftover honesty fixtures stay honest; not re-run |
| leftover #215 BoundSql / export | PASS | sibling BoundSql / COUNT / export hidden-drop leftover stays honest |
| leftover #238 remainder cells | PASS | parent remainder honesty fixtures stay PASS; not a live JAR / IsolatedExport mask walk |
| IsolatedExport applies mask | **false** | granted export still writes raw cells |
| Live JAR list/detail mask | SKIP | `NEXUS_BASE_URL` unset; nothing on `:8080` / `:8088` |
| Live IsolatedExport mask | SKIP | not observed |
| Live second-auth grant | SKIP | not observed |
| Live BoundSql / COUNT | SKIP | not observed |
| Live export FieldPolicy drop | SKIP | not observed |
| Sibling leftover rows | kept | #162 / #184 / #215 / #238 + this leftover + #260 `g5-publishhook-ddl-leftover-honesty` + #256 `g7-query-withdraw-leftover-honesty` + #265 `g4-hot-purge-staging-pkce-leftover-honesty` + #263 `linux-arm-profile-leftover-remainder-honesty` + #262 `g10-file-object-reconcile-leftover-honesty` + #259 `g9-leftover-honesty-remainder` + #249 `g17-separation-leftover-honesty-remainder` + #240 `g15-h5-pkce-miniprogram-remainder-honesty` + #254 `g2-h5-every-page-form-write-report-remainder-honesty` + #236 `linux-arm-profile-remainder-honesty` + #251 `g4-hot-purge-staging-pkce-remainder-honesty` + #243 `g10-file-object-reconcile-remainder-honesty` and other siblings |
| #184/#215/#238 redo | **false** | sibling leftovers |
| Product G1 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Encoding mask / 二次授权 onto Query AST
- IsolatedExport applying masks
- Treating #238 / #215 harness PASS as a live product-JAR / IsolatedExport mask / BoundSql walk
- Live product-JAR list/detail FieldPolicy mask / live IsolatedExport mask / live second-auth grant
- Live leftover BoundSql / COUNT / IsolatedExport hidden-drop walk
- Product G1 close
- Redo of #184 / #215 / #238
