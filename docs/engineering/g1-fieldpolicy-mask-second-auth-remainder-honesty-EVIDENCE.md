# G1 leftover — FieldPolicy mask / second-auth remainder honesty beyond #184/#162/#215 — evidence

Recorded on this branch after rebase onto `origin/main` `c800294` (Merge #239 G7 expense/travel query leftover honesty remainder after #237 G9 Redis-down/invalidate leftover honesty remainder / #232 G2 H5 every-page / form.write leftover honesty remainder / #212 G7 withdraw browser-evidence leftover honesty / #230 G4 hot-purge/P1 staging leftover honesty / #231 S0+S1 claim-list tip-sync / #229 G9 live multi-JVM / replica remainder leftover honesty / #226 G3 Outbox/claim-crash leftover honesty / #228 G10 leftover honesty remainder / #221 G2 report/physical-dump leftover honesty / #225 G6 RecoveryPort leftover honesty / #224 S0+S1 claim-list tip-sync / #223 S0 rotate/getenv remainder leftover honesty / #222 G4 live Keycloak CA / SPA PKCE remainder leftover honesty / #216 G17 SoD leftover honesty / #218 S0+S1 claim-list tip-sync / #220 G15 WeChat/DingTalk native-bridge remainder leftover / #215 G1 BoundSql/COUNT/export leftover honesty). **Not** a product G1 close. **Not** a redo of #115 / #130 / #134 / #142 / #146 / #162 / #168 / #184 / #215. **Not** IsolatedExport applying masks. **Not** a live product-JAR FieldPolicy walk. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0/M0 were not redone.

See [docs/g1-fieldpolicy-mask-second-auth-remainder-honesty.md](../g1-fieldpolicy-mask-second-auth-remainder-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g1-fieldpolicy-mask-second-auth.md](../g1-fieldpolicy-mask-second-auth.md) (#162), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169), and [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| IsolatedExport mask | not observed | granted export still writes raw cells (#162/#184) |
| Runtime | leftover honesty inventory only | No Docker / Compose. `#162` / `#184` / `#215` scripts not re-run |

## Commands

```bash
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-live-api-mask-pass.env
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-live-export-mask-pass.env
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-live-second-auth-pass.env
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-g1-close.env
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-mask-on-ast.env
./scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty.sh --verify-report \
  scripts/g1-fieldpolicy-mask-second-auth-remainder-honesty/fixtures/fake-redone.env
```

Do **not** treat `./scripts/g1-fieldpolicy-mask-leftover-honesty.sh` or `./scripts/g1-queryast-export-honesty.sh` as this leftover. Those scripts are the #184 / #215 contracts.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Live output is gitignored under `.local/g1-fieldpolicy-mask-second-auth-remainder-honesty/`. A sanitized copy is [`g1-fieldpolicy-mask-second-auth-remainder-honesty/agent-vm.env`](./g1-fieldpolicy-mask-second-auth-remainder-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | PASS / expected FAIL | leftover #162/#184/#215 cells PASS; fake close / fake mask-on-AST / fake live PASS / fake redo rejected |
| leftover #162 leftoverMaskFields | PASS | mask / 二次授权 stay on `FieldPolicyProjector` beside Query AST |
| leftover #184 honesty cells | PASS | parent leftover honesty fixtures stay honest; not re-run |
| leftover #215 BoundSql / export | PASS | sibling BoundSql / COUNT / export hidden-drop leftover stays honest |
| IsolatedExport applies mask | **false** | granted export still writes raw cells |
| Live JAR list/detail mask | SKIP | `NEXUS_BASE_URL` unset; nothing on `:8080` / `:8088` |
| Live IsolatedExport mask | SKIP | not observed |
| Live second-auth grant | SKIP | not observed |
| Sibling leftover rows | kept | #162 / #184 / #215 + this leftover + #229 `g9-live-multi-jvm-replica-remainder-honesty` + #230 `g4-hot-purge-staging-leftover-honesty` + #212 `g7-withdraw-browser-evidence-honesty` + #232 `g2-h5-every-page-form-write-remainder-honesty` + #237 `g9-redis-down-invalidate-remainder-honesty` + #239 `g7-query-expense-travel-honesty-remainder` + #226 `g3-outbox-consume-idempotent-honesty` + #228 `g10-leftover-honesty-remainder` and other siblings |
| #162/#184/#215 redo | **false** | sibling leftovers |
| Product G1 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Encoding mask / 二次授权 onto Query AST
- IsolatedExport applying masks
- Live product-JAR list/detail FieldPolicy mask / live IsolatedExport mask / live second-auth grant
- Product G1 close
- Redo of #162 / #184 / #215
