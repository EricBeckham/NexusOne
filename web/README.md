# NexusOne Web (M0-12 + G15 H5 mobile)

Vue 3 shell for tenant admin, org/identity, permission snapshot, and **P3 台账 / 表单 / 待办 / 通知 / 审计**.
G15 adds a **separate** mobile tree under `src/mobile/` (`/m/login`, `/m/callback`, `/m/p3/home`, `/m/p3/files`). Desktop ledger / form / todo pages are unchanged. This is **not** a full G15 close — see [docs/g15-h5-mobile.md](../docs/g15-h5-mobile.md).

- Tenant / org pages talk to merged **M0-03** and **M0-04** HTTP APIs.
- Permission pages talk to **M0-05** `/api/v1/permission` (health / snapshot / check).
- Ledger / form / todo pages talk to live **M0-06/07/08** HTTP plus the G18/G7 path (`/api/v1/p3/path/submit-start`, `/api/v1/p3/business-types`).
- Notify: `/api/v1/notifications`. Audit by trace (read-only): `/api/v1/audit/traces/{traceId}`.
- Product G18 (one vertical slice) is closed by the 2026-09-22 browser record in [docs/g18-business-path.md](../docs/g18-business-path.md). The earlier `ace55ff` walk only reached tenant / org / permission snapshot.

## Run

Backend (PostgreSQL 15.x, no Docker):

```bash
export NEXUS_DB_URL='jdbc:postgresql://127.0.0.1:5432/nexusone'
export NEXUS_DB_USER='nexus'
export NEXUS_DB_PASSWORD   # env only
export NEXUS_P1_DEMO_CONTEXT_ENABLED=true
export NEXUS_PLATFORM_BOOTSTRAP_TOKEN='m0-12-bootstrap'
export NEXUS_IAM_IDP=stub
mvn -q -DskipTests package
java -jar nexus-bootstrap/target/nexus-bootstrap-0.1.0-SNAPSHOT.jar
```

Frontend (Vite proxies `/api` and `/actuator` to the JAR so the browser never attaches `X-Tenant-Id`):

```bash
cd web
npm install
npm run dev
```

Open `http://127.0.0.1:5173/login`. Default Vite proxy target is `:8080`; set `NEXUS_API_ORIGIN=http://127.0.0.1:18085` if the JAR listens on 18085.

- Stub login generates the same `stub.<base64url(json)>` token format as `StubIdentityProvider`.
- Enable **平台操作** and set the demo actor / bootstrap token (same values as the backend env).
- Tenant pages use P1 demo PlatformContext headers (local JAR only).
- Person / org / position / P3 / permission / notify / audit pages use `Authorization` + `X-Nexus-Context-Id`.

Checks (no running server required):

```bash
cd web
npm run assert:isolation
npm run assert:p3-live
npm run assert:h5
npm run build
```

## Live P3

MOCK adapters are removed. The UI always calls live HTTP. Missing TenantContext is **403**. Empty lists are empty tenants, not fixtures.

Do **not** set `VITE_NEXUS_P3_USE_MOCK` (retired). Do **not** send `X-Tenant-Id`.

## Pages

| Route | API | Notes |
|---|---|---|
| `/p1/tenants` | `GET /api/v1/p1/tenants` | Directory |
| `/p1/tenants/new` | `POST /api/v1/p1/tenants` | Optional P2 admin bootstrap |
| `/p1/tenants/:id` | config / quota / entitlement / audit | Enable/disable is confirm=true |
| `/p2/persons` | `/api/v1/persons` | Tenant-scoped by issued context |
| `/p2/org` | `/api/v1/departments/tree` | |
| `/p2/positions` | positions + employments | Position ≠ Role |
| `/p2/permissions/*` | `/api/v1/permission/health\|snapshot\|check` | No GET role catalog |
| `/p3/ledgers` | GET list + POST `/api/v1/p3/business-types/{code}/provision` | G7 请假/报销/出差；quota from P1 if platform mode |
| `/p3/ledgers/:id` | ledger + `.../records` + POST `.../fields` | No field PUT |
| `/p3/forms` | GET list + POST create | Bind selected ledger field_code |
| `/p3/forms/:id` | validate / publish / **submit-start** | Fill writes physical row + starts Flowable. Each click sends `Idempotency-Key` (G3) |
| `/p3/todos` | `/api/v1/p3/todos` + `/api/v1/p3/path/tasks/*` + withdraw | 同意/驳回/「我发起」撤回 (`data-testid=todo-withdraw`) |
| `/p3/notifications` | `GET /api/v1/notifications` | Mark read |
| `/p3/audit` | `GET /api/v1/audit/traces/{traceId}` | Read-only, tenant-filtered |
| `/m/login` | stub token + safe `return` | F011 mobile login redirect (not PKCE) |
| `/m/callback` | `code` / `state` → `POST /api/v1/auth/login` | Strips query after hop; no token in URL |
| `/m/p3/home` | session in `sessionStorage` | F013 refresh / back |
| `/m/p3/files` | `POST/GET /api/v1/files` | F012 size + MIME + signed download |
| `/m/p3/files/:id` | metadata + signed-url | Deep link; tenant stays in issued context |

Do not treat this shell as a Keycloak Authorization Code + PKCE acceptance. Product login remains M0-04’s confidential client. G15 host messages (F014) are not accepted.
