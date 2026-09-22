# G1 leftover — BoundSql / COUNT honesty beyond #146

Recorded on this branch after rebase onto `origin/main` `724f5f0` (Merge #198 G15 wx-dt/native leftover honesty; includes #200 SecretPort rotate/getenv leftover honesty / #199 claim-list tip-sync / #192 G7 expense/travel query leftover honesty / #193 Linux/ARM Profile leftover honesty / #190 P1 staging audit-events leftover honesty / #194 claim-list tip-sync / #186 RecoveryPort dry-run leftover honesty / #185 live Redis/MinIO isolation leftover honesty / #187 G17 SoD leftover honesty / #183 G4 90-day hot-purge leftover honesty / #184 FieldPolicy mask leftover honesty). **Not** a product G1 close. **Not** a redo of #142 / #146 / #162 / #168. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. Leftover BoundSql / COUNT stay on `QueryAstScopedQuery` → `GuardedJdbc`.

See [docs/g1-queryast-boundsql-honesty.md](../g1-queryast-boundsql-honesty.md). Keep **all** sibling leftover rows in [docs/README.md](../README.md): [g1-queryast-boundsql-guard.md](../g1-queryast-boundsql-guard.md) (#146), [g1-datascope-or-in-ast.md](../g1-datascope-or-in-ast.md) (#142/#168), [g1-fieldpolicy-mask-second-auth.md](../g1-fieldpolicy-mask-second-auth.md) (#162), and [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), plus [g15-wx-dt-native-bridge-honesty.md](../g15-wx-dt-native-bridge-honesty.md) (#198), [s0-secret-rotate-without-restart-honesty.md](../s0-secret-rotate-without-restart-honesty.md) (#200), [g7-query-expense-travel-honesty.md](../g7-query-expense-travel-honesty.md) (#192), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177) and [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), plus the #199 claim-list tip-sync pointers (`4d40e06` / `#192` / `#193`) and the #194 pointers (`4c26d76` / `#185` / `#186`).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Maven | `$HOME/tools/apache-maven-3.8.7` when present | collect may `--skip-tests` |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Runtime | collect script + honesty fixtures + `mvn test` | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

./scripts/g1-queryast-boundsql-honesty.sh
./scripts/g1-queryast-boundsql-honesty.sh --verify-report <out>/report.env
./scripts/g1-queryast-boundsql-honesty.sh --verify-report \
  scripts/g1-queryast-boundsql-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g1-queryast-boundsql-honesty.sh --verify-report \
  scripts/g1-queryast-boundsql-honesty/fixtures/fake-live-product-pass.env
./scripts/g1-queryast-boundsql-honesty.sh --verify-report \
  scripts/g1-queryast-boundsql-honesty/fixtures/fake-g1-close.env
./scripts/g1-queryast-boundsql-honesty.sh --verify-report \
  scripts/g1-queryast-boundsql-honesty/fixtures/fake-raw-boundsql.env
```

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g1-queryast-boundsql-honesty/`. A sanitized copy is [`g1-queryast-boundsql-honesty/agent-vm.env`](./g1-queryast-boundsql-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | pending | leftover BoundSql/COUNT PASS + fake close / fake raw BoundSql / fake live PASS |
| leftover BoundSql guard | pending PASS | QueryAstScopedQuery.executed keeps quoted `"tenant_id"` |
| leftover COUNT guard | pending PASS | `countExecuted` wrap; not a Query AST node |
| raw BoundSql rejected | pending true | unquoted `tenant_id` BoundSql fails closed |
| Live product walk | pending SKIP | `NEXUS_BASE_URL` unset; nothing on `:8080` / `:8088` |
| Sibling leftover rows | kept | #146 / #142+#168 / #162 / #184 |
| #142/#146/#162/#168 redo | **false** | sibling leftovers |
| Product G1 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR `GuardedLedgerStore` leftover BoundSql list + COUNT walk
- Encoding `COUNT(*)` onto a Query AST node
- UI / H5 / report UI leftover BoundSql / COUNT walk
- Product-wide G1 close
