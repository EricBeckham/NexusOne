# M0-12 UI & acceptance scripts

Work package for **tenant/org/permission shells** plus **dual-tenant adversarial scripts** against APIs that are already on `main` (M0-03 P1, M0-04 P2, M0-05 permission, M0-06/07/08 P3, M0-10 notify).

This package **does not close G18**. Ledger, form, and todo **UI** in `web/` call live HTTP (`/api/v1/p3/ledgers`, `/api/v1/p3/forms`, `/api/v1/p3/todos`, `/api/v1/p3/path/submit-start`). Notify and audit-by-trace have sidebar pages. MOCK adapters for 台账 / 表单 / 待办 are removed.

## Browser state (NOT CLOSED)

Eric browser-tested UI `http://127.0.0.1:5173` + API `:18085` on `ace55ff` (stub login「浏览器管理员」, tenant `ui2205`). He could only reach **tenant / org / permission snapshot**. That walk is **not closed**.

Two independent blockers on that commit:

1. **Grants:** permission snapshot **`grant_count=0`**, `ledger/read` **`allowed=false` / `no_grant`**. HTTP E2E / `G18BusinessPathIT` already seeded `tenant_member` via `/api/v1/p3/*`; the browser permission page did not. **#66** (`7fffd39` on `main`) seeds role `tenant_member` onto the current **person id** from `PermissionAuthFilter` / `FlowAuthFilter`. `TenantMemberGrantSeedApiTest` (**2026-09-22T14:17:57Z**) is that permission-shell path, not a browser G18 close.
2. **Shell:** ledger list empty / quota 0/10 / **no create-ledger UI**; form list empty / **no create / validate / publish**; fill copy was `form_submission`-only; todos had **no path approve**; **no sidebar** for notifications or audit hash-chain.

This PR adds the shell entry points against APIs already on `main`. It still **does not claim product G18 closed** until Eric re-runs the browser E2E (login → grants present → create ledger → publish form → submit-start → approve → notify → audit by trace). Do not treat a later non-zero snapshot as a G18 close.

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

TenantContext for `/api/v1/notifications`, `/api/v1/audit`, and `/api/v1/p3/*` (except todos) is bound by P3 `one.nexus.p3.infra.isolation.IssuedTenantIsolationFilter` (PR #62 on `main`). This package does **not** register a second bootstrap filter. Todos / process keep `FlowAuthFilter`. Vue never assigns `X-Tenant-Id`.

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
| G1 | Query AST / export / file / cache / Job / Task full path | **DEFERRED** | M0-02 kernel + later modules |
| G1 | Guard-reject logs “zero leak” product claim | **DEFERRED** | isolation kernel evidence, not this UI |
| G2 | Permission health + check via `/api/v1/permission` | **LIVE API** | On `ace55ff` health UP but `grant_count=0` / `ledger/read` `no_grant`. #66 on `main` seeds person-id `tenant_member` on the permission filter. G2 product-wide not closed |
| G5 | Draft/Validate/Publish | **LIVE API** | M0-07 HTTP + Vue fill/publish; G5 rollback is server-side, not a G18 walkthrough |
| G8 | Health / trace id | **PASS** | actuator + `/api/v1/meta/runtime` |
| G9 | PolicySnapshot | **LIVE API** | `GET /api/v1/permission/snapshot` with issued context |
| G15 | H5 | **DEFERRED** | S1 |
| G18 | Tenant create | **PASS** | P1 API + tenant admin UI |
| G18 | Org (person/dept/position/employment) | **PASS** | P2 API + org UI |
| G18 | Permission views | **LIVE SHELL / BLOCKED ON ace55ff** | Snapshot + check only. Browser on `ace55ff`: `grant_count=0`, `ledger/read` `no_grant`. #66 seeds person id. No full matrix UI |
| G18 | Ledger | **LIVE API + CREATE UI** | Vue list/create/detail → `/api/v1/p3/applications` + `/api/v1/p3/ledgers`. No field PUT. Browser G18 did not reach this on `ace55ff` |
| G18 | Form / publish | **LIVE API + CREATE UI** | Create DRAFT, Validate, Publish. Fill defaults to **submit-start**. G5 not claimed closed here. Browser G18 did not reach this on `ace55ff` |
| G18 | Business data / query | **LIVE LIST** | record APIs exist; physical row is written by submit-start, not by form_submission-only |
| G18 | Sequential approval / todo | **LIVE API** | inbox/done/initiated + **path** approve/reject/withdraw. Browser G18 did not reach this on `ace55ff` |
| G18 | In-app notification | **LIVE UI** | sidebar + `/p3/notifications` → `/api/v1/notifications`. Browser G18 did not reach this on `ace55ff` |
| G18 | Full audit hash chain | **LIVE READ UI** | `/p3/audit` → `GET /api/v1/audit/traces/{id}` (tenant-filtered, read-only). Not a product G4/G18 close |
| G18 | End-to-end real business walkthrough | **NOT CLOSED** | Backend path is wired (`docs/g18-business-path.md`). Browser on `ace55ff` reached only tenant / org / permission snapshot (`grant_count=0` / `no_grant`) and had no create/publish/submit-start UI. Vue now exposes those buttons; #66 seeds the subject. Product G18 is **not** closed until Eric re-walks. |

Script status values: `PASS` (asserted), `SKIP` (honestly deferred), `FAIL` (regressed).

## Out of scope (explicit)

- Claiming G18 or G1 product-wide closed
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
| GET `/api/v1/permission/roles` | Does not exist | Snapshot + check only |
| Quota on ledger list | P1 `/api/v1/p1/tenants/{id}/quotas` | Optional platform-header read |
| Audit by trace | `GET /api/v1/audit/traces/{traceId}` | Sidebar page; tenant-filtered; no chain mutation |

## Evidence (this agent VM)

See the PR for `npm run build`, `npm run assert:isolation`, `npm run assert:p3-live`, and dual-tenant script output when PostgreSQL 15.x is available.

Browser on `ace55ff`: tenant / org / permission snapshot only; `grant_count=0` / `no_grant`. G18 remains **NOT CLOSED**. Do not claim closed.
