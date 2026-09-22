# M0-12 UI & acceptance scripts

Work package for **tenant/org/permission shells** plus **dual-tenant adversarial scripts** against APIs that are already on `main` (M0-03 P1, M0-04 P2, M0-05 permission, M0-06/07/08 P3, M0-10 notify).

Ledger, form, and todo **UI** in `web/` call live HTTP (`/api/v1/p3/ledgers`, `/api/v1/p3/forms`, `/api/v1/p3/todos`, `/api/v1/p3/path/submit-start`). Notify and audit-by-trace have sidebar pages. MOCK adapters for 台账 / 表单 / 待办 are removed. **Product G18 is closed** by the `38f1a73` browser record in [g18-business-path.md](./g18-business-path.md).

## Browser state

### `38f1a73` — G18 closed (2026-09-22)

UI `http://127.0.0.1:5173`, API `:18086`, PostgreSQL 15.18 database `nexus_g18_loop`. Stub login `ui2231@example.com` / 浏览器管理员. Tenant 浏览器租户 `ui2231` (`01a0c98a-1f3c-7f72-b724-6ac34746e981`). Person ACTIVE, org Root / `ROOT`. Snapshot `grant_count=7`; `ledger`/`read` allowed. Ledger `leave2231` READY. Form `leave2231_form` Validate PASS, `running_version=1`. submit-start then 同意. Record 年假 / 3 / 浏览器走通 / `APPROVED`. Notifications `DELIVERED`. Audit by both traces. Detail and ids: [g18-business-path.md](./g18-business-path.md).

### `ace55ff` — prior walk, incomplete

Eric browser-tested UI `http://127.0.0.1:5173` + API `:18085` on `ace55ff` (stub login「浏览器管理员」, tenant `ui2205`). He could only reach **tenant / org / permission snapshot**. Kept as history.

Two independent blockers on that commit:

1. **Grants:** permission snapshot **`grant_count=0`**, `ledger/read` **`allowed=false` / `no_grant`**. HTTP E2E / `G18BusinessPathIT` already seeded `tenant_member` via `/api/v1/p3/*`; the browser permission page did not. **#66** (`7fffd39` on `main`) seeds role `tenant_member` onto the current **person id** from `PermissionAuthFilter` / `FlowAuthFilter`. `TenantMemberGrantSeedApiTest` (**2026-09-22T14:17:57Z**) is that permission-shell path, not a browser G18 close.
2. **Shell:** ledger list empty / quota 0/10 / **no create-ledger UI**; form list empty / **no create / validate / publish**; fill copy was `form_submission`-only; todos had **no path approve**; **no sidebar** for notifications or audit hash-chain.

The shell entry points landed before the close. The close is the `38f1a73` walk above (login → grants present → create ledger → publish form → submit-start → approve → notify → audit by trace), together with `G18RollbackProbe` and the dispatcher timing already in [g18-business-path.md](./g18-business-path.md). A non-zero snapshot by itself was not the close; the full walk was.

## How to run

PostgreSQL **15.x** on localhost, credentials from the environment, no Docker.

```bash
export NEXUS_DB_URL='jdbc:postgresql://127.0.0.1:5432/nexusone'
export NEXUS_DB_USER='nexus'
export NEXUS_DB_PASSWORD   # env only
chmod +x scripts/m0-12/run-dual-tenant.sh
./scripts/m0-12/run-dual-tenant.sh
```

The wrapper packages `nexus-bootstrap`, starts it with:

- `NEXUS_P1_DEMO_CONTEXT_ENABLED=true` (P1 platform demo headers; strip in production)
- `NEXUS_IAM_IDP=stub` (Keycloak live login is M0-04, not this package)
- `NEXUS_IAM_PERSISTENCE=memory` (avoid a second Flyway on `iam` while bootstrap already applies `V200`)
- `NEXUS_PLATFORM_BOOTSTRAP_TOKEN` (also bound as `NEXUS_IAM_PLATFORM_BOOTSTRAP_TOKEN`) for `POST /api/v1/internal/tenants/{id}/admin-users`

Then it runs `scripts/m0-12/dual-tenant-isolation.py`, `web/scripts/assert-client-isolation.mjs`, and `web/scripts/assert-p3-live.mjs`.

Point at an already running JAR:

```bash
export NEXUS_BASE_URL=http://127.0.0.1:8080
export NEXUS_PLATFORM_BOOTSTRAP_TOKEN=...
python3 scripts/m0-12/dual-tenant-isolation.py
```

UI:

```bash
cd web && npm install && npm run dev
```

See [web/README.md](../web/README.md).

## Isolation filter owner

TenantContext for `/api/v1/notifications`, `/api/v1/audit`, `/api/v1/files`, and `/api/v1/p3/*` (except todos) is bound by P3 `one.nexus.p3.infra.isolation.IssuedTenantIsolationFilter` (PR #62 on `main`; files path is the G15 H5 binder). This package does **not** register a second bootstrap filter. Todos / process keep `FlowAuthFilter`. Vue never assigns `X-Tenant-Id`.

## Checklist: assertable now vs deferred

| Gate | Item | This package | Evidence |
|---|---|---|---|
| G1 | Client `X-Tenant-Id` rejected at API | **PASS** | `P2.IAM.CLIENT_TENANT_REJECTED` |
| G1 | Vue client never assigns tenant identity headers | **PASS** | `npm run assert:isolation` |
| G1 | Persons listed in tenant A exclude tenant B | **PASS** | dual-tenant HTTP script |
| G1 | GET person across tenant → not found | **PASS** | `P2.IAM.NOT_FOUND` |
| G1 | Department / position ids do not leak | **PASS** | dual-tenant HTTP script |
| G1 | Context bound to IdP subject | **PASS** | other token + stolen context id |
| G1 | Switch without membership forbidden | **PASS** | `P2.IAM.FORBIDDEN` |
| G1 | Two P1 tenants independent; quota on A does not bump B | **PASS** | P1 consume/list |
| G1 | Platform tenant cannot be disabled | **PASS** | `P1.TENANT.0004` |
| G1 | In-app notifications do not leak across tenants | **PASS** | enqueue in A, list in B empty |
| G1 | Query AST / export / file / cache / Job / Task full path | **PARTIAL** | Query AST + INNER/OUTER/CROSS JOIN + subquery/batch/sort/page + nested subquery-in-subquery + export/file/cache/Job/Task + export FieldPolicy: [g1-isolation-surfaces.md](./g1-isolation-surfaces.md), [g1-query-join-field-policy.md](./g1-query-join-field-policy.md), [g1-query-ast-remainder.md](./g1-query-ast-remainder.md), [g1-query-nested-subquery.md](./g1-query-nested-subquery.md), [g1-query-outer-cross-join.md](./g1-query-outer-cross-join.md), [g1-datascope-fieldpolicy-ast.md](./g1-datascope-fieldpolicy-ast.md), [g1-export-fieldpolicy-drop.md](./g1-export-fieldpolicy-drop.md) (#134), [g1-datascope-or-in-ast.md](./g1-datascope-or-in-ast.md) (#142), [g1-fieldpolicy-mask-second-auth.md](./g1-fieldpolicy-mask-second-auth.md) (mask / 二次授权仍在 AST 旁). Optional live Redis / MinIO cache+file drills: [g1-live-redis-minio.md](./g1-live-redis-minio.md). Not a product-wide zero-leak close. |
| G1 | Guard-reject logs “zero leak” product claim | **PARTIAL** | Those surfaces emit `ISOLATION_REJECT` + `AuditPort` `GUARD_REJECT`. UI/H5/report UI remain open. |
| G2 | Permission health + check via `/api/v1/permission` | **LIVE API** | `38f1a73` health UP, `grant_count=7`, `ledger`/`read` allowed. G2 list/detail/flow/form-studio + form fill `form.write` + submit-start dual-check + H5 `/m/*` + export Task type / Job/Task submit share `SurfaceGuard` + role matrix (`docs/g2-permission.md`, [g2-form-submission-write.md](./g2-form-submission-write.md), [g2-submit-start-form-write.md](./g2-submit-start-form-write.md), [g2-h5-permission.md](./g2-h5-permission.md)). SurfaceGuard bypass CI: [g2-surfaceguard-bypass-ci.md](./g2-surfaceguard-bypass-ci.md) (#122). Report query/export leftover: [g2-report-surface-guard.md](./g2-report-surface-guard.md) (#124). Isolation-only `tenant_only` / catalog whitelist leftover: [g2-tenant-only-catalog.md](./g2-tenant-only-catalog.md) (#144). Physical dump leftover: [g2-physical-dump-surface.md](./g2-physical-dump-surface.md) (#137). Todo HTTP leftover: [g2-todo-http-surface.md](./g2-todo-http-surface.md). H5 every page / `pg_dump` stay open. Not a product-wide G2 close |
| G5 | Draft/Validate/Publish (forms) | **IT / script** | Success path is live HTTP + Vue. Failed paths: [g5-form-publish-rollback.md](./g5-form-publish-rollback.md). Mid-publish leftover: [g5-mid-publish-failure.md](./g5-mid-publish-failure.md). Browser failed-publish click honesty leftover: [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) (live browser/IdP/JAR SKIP). Not a full G5 product close |
| G7 | Leave withdraw (scripted HTTP) | **RECORDED** | [g7-withdraw-evidence.md](./g7-withdraw-evidence.md). Catalog leave/expense/travel is on main (#69). This row is the leave-path IT only; not a three-type close. UI click leftover: [g7-withdraw-browser.md](./g7-withdraw-browser.md) |
| G8 | Health / trace id | **PASS** | actuator + `/api/v1/meta/runtime` |
| G9 | PolicySnapshot | **PARTIAL** | `GET /api/v1/permission/snapshot` with issued context. Version poll + invalidate-only: [g9-policy-snapshot.md](./g9-policy-snapshot.md). Redis-down failover: [g9-policysnapshot-invalidate.md](./g9-policysnapshot-invalidate.md). P2 timeout-then-reject: [g9-p2-timeout-reject.md](./g9-p2-timeout-reject.md). Fail-closed DENY leftover: [g9-failover-remainder.md](./g9-failover-remainder.md). Redis-down / live invalidate honesty leftover: [g9-redis-down-invalidate-honesty.md](./g9-redis-down-invalidate-honesty.md). In-process dual-reader / lost-invalidate version-poll: [g9-multinode-partition.md](./g9-multinode-partition.md). Sentinel / Cluster honesty leftover: [g9-sentinel-cluster-honesty.md](./g9-sentinel-cluster-honesty.md). Live multi-JVM / replica promotion honesty leftover: [g9-live-multi-jvm-replica.md](./g9-live-multi-jvm-replica.md). Not Sentinel/Cluster / replica promotion / live multi-JVM / live Redis-down product close. |
| G15 | H5 | **PARTIAL** | S1 mobile browser: login redirect + files + routing + F014 host messages + F010 runtime pages + **simulated** 小程序 web-view parent in `web/src/mobile/` (see [g15-h5-mobile.md](./g15-h5-mobile.md), [g15-f010-h5-runtime.md](./g15-f010-h5-runtime.md), [g15-miniprogram-webview.md](./g15-miniprogram-webview.md)). APP WebView **web** envelope walk: [g15-app-webview-host.md](./g15-app-webview-host.md). H5 `/m/*` PKCE leftover (Playwright/stub): [g15-h5-pkce-browser.md](./g15-h5-pkce-browser.md) (#126). WeChat / DingTalk production-host **stub**: [g15-wx-dt-prod-host.md](./g15-wx-dt-prod-host.md) (#132). APP WebView **native-bridge stub**: [g15-native-bridge-stub.md](./g15-native-bridge-stub.md) (not store-ready). APP WebView **native-bridge honesty leftover**: [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md) (PASS/SKIP beyond #151; not IPA/APK production accept). WeChat / DingTalk production-gap **honesty stub**: [g15-wx-dt-prod-gap-honesty.md](./g15-wx-dt-prod-gap-honesty.md) (not production验收 vs #117/#132/#151). Native APP / WeChat/DingTalk production **not** accepted. Desktop P3 pages untouched. |
| G18 | Tenant create | **PASS** | P1 API + tenant admin UI |
| G18 | Org (person/dept/position/employment) | **PASS** | P2 API + org UI |
| G18 | Permission views | **PASS** | `38f1a73`: snapshot `grant_count=7`, `ledger`/`read` allowed. G18 close unchanged. Matrix UI is the G2 slice |
| G18 | Ledger | **PASS** | `leave2231` READY, quota 1/10, table `l_ui2231_leave2231`. No field PUT |
| G18 | Form / publish | **PASS** | `leave2231_form` Validate PASS, `running_version=1`. G5 failed-path evidence is [g5-form-publish-rollback.md](./g5-form-publish-rollback.md), not this walk |
| G18 | Business data / query | **PASS** | Record 年假 / 3 / 浏览器走通 / `APPROVED` after 同意 |
| G18 | Sequential approval / todo | **PASS** | submit-start then path 同意. Withdraw was not clicked on this walk; G7 travel withdraw + reject dual-tenant HTTP evidence is [g7-business-types.md](./g7-business-types.md). Leave UI withdraw leftover (stub login, #113, does not reopen G18): [g7-withdraw-browser.md](./g7-withdraw-browser.md) |
| G7 | Three business types + withdraw | **SCRIPTED** | Catalog leave / expense / travel (#69); expense reject; travel withdraw IT + dual-tenant HTTP walkthrough; expense/travel withdraw 403 (#103, [g7-withdraw-expense-travel.md](./g7-withdraw-expense-travel.md)). Browser `todo-withdraw` leftover is [g7-withdraw-browser.md](./g7-withdraw-browser.md) (#113; stub login, not a three-type product close). Expense/travel list/detail/todo query leftover is [g7-query-expense-travel.md](./g7-query-expense-travel.md) (#140). Not a product G7 close or a full S0+S1 close |
| G18 | In-app notification | **PASS** | `approval.task.assigned` and `approval.task.approved` both `DELIVERED` |
| G18 | Audit by trace | **PASS** | Submit trace `1a1f7b8b-4de0-4ef0-978c-3d39a65744ff`; approve trace `cb9e0a9f-0943-4384-aa06-31f05e292103`. Chain indexes 4–7 linked. G4 login/permission/admin/guard-reject: [g4-audit-coverage.md](./engineering/g4-audit-coverage.md). Export + 90-day archive Job + 365-day same-PG purge: [g4-export-audit-archive.md](./g4-export-audit-archive.md). Same-trace MockMvc walk: [g4-same-trace-walk.md](./g4-same-trace-walk.md). HTTP-driver PKCE: [g4-pkce-login-audit.md](./g4-pkce-login-audit.md). Object-store cold copy: [g4-object-cold-archive.md](./g4-object-cold-archive.md). SurfaceGuard deny leftover: [g4-guard-deny-audit.md](./g4-guard-deny-audit.md) (#136). Remaining G4 is human browser PKCE. 90-day hot-row purge leftover: [g4-hot-purge-90d.md](./g4-hot-purge-90d.md) |
| G18 | End-to-end real business walkthrough | **PASS** | `38f1a73` browser record in [g18-business-path.md](./g18-business-path.md). Same-TX rollback and dispatcher timing are the earlier records cited there |

Script status values: `PASS` (asserted), `SKIP` (honestly deferred), `FAIL` (regressed).

## Out of scope (explicit)

- Claiming G1 product-wide closed (export / file / cache / Job / Task / INNER/OUTER/CROSS JOIN / subquery / nested subquery / batch / sort / page / export FieldPolicy guards are evidenced; UI/H5/report UI are not)
- Reimplementing backend modules or inventing MOCK fixtures again
- Docker / Compose
- Browser Authorization Code + PKCE as accepted login
- GaussDB

## API gaps the UI handles thinly (not MOCK)

| Need | Live API | UI handling |
|---|---|---|
| Ledger create | POST `/api/v1/p3/applications` + POST `.../ledgers` | Create form on `/p3/ledgers` (new app if needed) |
| Ledger field update | POST fields only | Disable property save; document `P3.LEDGER.NO_FIELD_UPDATE` |
| Expense-shaped record columns | Records are a generic map | Render whatever keys the ledger returns |
| Form create / validate / publish | POST forms + validate + publish | List create + studio publish tab |
| Form fill → physical row + Flowable | `POST /api/v1/p3/path/submit-start` | Default fill action; submissions API remains available but is not the G18 button |
| Todo comment | approve/reject have no comment body | Local textarea only |
| GET `/api/v1/permission/roles` | `GET /roles|/assignments|/grants|/matrix` | Role / DataScope / field tabs |
| Quota on ledger list | P1 `/api/v1/p1/tenants/{id}/quotas` | Optional platform-header read |
| Audit by trace | `GET /api/v1/audit/traces/{traceId}` | Sidebar page; tenant-filtered; no chain mutation |

## Evidence (this agent VM)

See the PR for `npm run build`, `npm run assert:isolation`, `npm run assert:p3-live`, and dual-tenant script output when PostgreSQL 15.x is available.

`ace55ff` browser: tenant / org / permission snapshot only; `grant_count=0` / `no_grant`. Superseded by the `38f1a73` walk. Product G18 is **closed** for that one slice.
