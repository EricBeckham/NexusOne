# G1 leftover — BoundSql / COUNT / export FieldPolicy drop honesty beyond #191/#146 — evidence

Recorded on this branch after rebase onto `origin/main` `c1d3150` (Merge #210 G17 Tongsuo/Nginx GMTLS handshake leftover honesty after #217; also #217 at `9be1f68`). **Not** a product G1 close. **Not** a redo of #134 / #146 / #191 / #142 / #162 / #168 / #184. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0/M0 were not redone. Leftover BoundSql / COUNT stay on `QueryAstScopedQuery` → `GuardedJdbc`. Export hidden drop stays on `IsolatedExport` / `ExportFieldPolicyGuard`.

See [docs/g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g1-queryast-boundsql-guard.md](../g1-queryast-boundsql-guard.md) (#146), [g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md) (#191), [g1-export-fieldpolicy-drop.md](../g1-export-fieldpolicy-drop.md) (#134), [g1-datascope-or-in-ast.md](../g1-datascope-or-in-ast.md) (#142/#168), [g1-fieldpolicy-mask-second-auth.md](../g1-fieldpolicy-mask-second-auth.md) (#162), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) (#174), [g2-h5-every-page.md](../g2-h5-every-page.md) (#163), [g2-form-write-honesty.md](../g2-form-write-honesty.md) (#173), [g4-export-wizard-deny-honesty.md](../g4-export-wizard-deny-honesty.md) (#203), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [g5-mid-publish-failure-honesty.md](../g5-mid-publish-failure-honesty.md) (#196), [g3-outbox-consume-crash-honesty.md](../g3-outbox-consume-crash-honesty.md) (#195), [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171), [g7-withdraw-browser-honesty.md](../g7-withdraw-browser-honesty.md) (#175), [g9-redis-down-invalidate-honesty.md](../g9-redis-down-invalidate-honesty.md) (#174), [g10-file-reconcile-honesty.md](../g10-file-reconcile-honesty.md) (#169), and [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Runtime | leftover honesty inventory only | No Docker / Compose. `#191` / `#146` / `#134` scripts not re-run |

## Commands

```bash
./scripts/g1-queryast-export-honesty.sh
./scripts/g1-queryast-export-honesty.sh --verify-report <out>/report.env
./scripts/g1-queryast-export-honesty.sh --verify-report \
  scripts/g1-queryast-export-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g1-queryast-export-honesty.sh --verify-report \
  scripts/g1-queryast-export-honesty/fixtures/fake-live-boundsql-pass.env
./scripts/g1-queryast-export-honesty.sh --verify-report \
  scripts/g1-queryast-export-honesty/fixtures/fake-live-export-drop-pass.env
./scripts/g1-queryast-export-honesty.sh --verify-report \
  scripts/g1-queryast-export-honesty/fixtures/fake-g1-close.env
./scripts/g1-queryast-export-honesty.sh --verify-report \
  scripts/g1-queryast-export-honesty/fixtures/fake-raw-boundsql.env
```

Do **not** treat `./scripts/g1-queryast-boundsql-honesty.sh` as this leftover. That script is the #191 contract.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Live output is gitignored under `.local/g1-queryast-export-honesty/`. A sanitized copy is [`g1-queryast-export-honesty/agent-vm.env`](./g1-queryast-export-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | PASS / expected FAIL | leftover BoundSql/COUNT/export drop PASS; fake close / fake raw BoundSql / fake live PASS rejected |
| leftover #191 / #146 BoundSql / COUNT | PASS | QueryAstScopedQuery → GuardedJdbc; raw BoundSql rejected; COUNT not an AST node |
| leftover #134 export hidden drop | PASS | IsolatedExport drops hidden; mask / second-auth stay on projector |
| Live BoundSql / COUNT | SKIP | `NEXUS_BASE_URL` unset; nothing on `:8080` / `:8088` |
| Live IsolatedExport drop | SKIP | no observed export hidden-drop walk |
| Sibling leftover rows | kept | #134 / #146 / #191 + this leftover + #204 `g2-h5-every-page-form-write-honesty` + #197 `g9-redis-down-invalidate-remainder` + #205 `g17-mfa-recovery-vue-otp-honesty` + #188 `g2-ops-pg-dump-honesty` + #209 `g9-sentinel-cluster-remainder-honesty` + #217 `g5-publishhook-ddl-honesty` + #210 `g17-gm-tls-nginx-handshake-honesty` + #211/#214 tip-sync and other siblings |
| #134/#146/#191 redo | **false** | sibling leftovers |
| Product G1 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR `GuardedLedgerStore` leftover BoundSql list + COUNT walk
- Live IsolatedExport hidden-drop walk
- Encoding `COUNT(*)` onto a Query AST node
- IsolatedExport applying masks
- UI / H5 / report UI leftover BoundSql / COUNT / export FieldPolicy walk
- Product-wide G1 close
- Redo of #134 / #146 / #191
