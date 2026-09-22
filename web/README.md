# NexusOne Web (M0-12)

Vue 3 shell for tenant admin, org/identity, permission snapshot, and **P3 台账 / 表单 / 待办**.

- Tenant / org pages talk to merged **M0-03** and **M0-04** HTTP APIs.
- Permission pages talk to **M0-05** `/api/v1/permission` (health / snapshot / check).
- Ledger / form / todo pages talk to live **M0-06/07/08** HTTP (`/api/v1/p3/*`, `/api/v1/process/*`, `/api/v1/todos/*`).
- This package **does not close G18**.

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

Open `http://127.0.0.1:5173/login`.

- Stub login generates the same `stub.<base64url(json)>` token format as `StubIdentityProvider`.
- Enable **平台操作** and set the demo actor / bootstrap token (same values as the backend env).
- Tenant pages use P1 demo PlatformContext headers (local JAR only).
- Person / org / position / P3 / permission pages use `Authorization` + `X-Nexus-Context-Id`.

Checks (no running server required):

```bash
cd web
npm run assert:isolation
npm run assert:p3-live
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
| `/p3/ledgers` | `GET /api/v1/p3/ledgers` | List; quota from P1 if platform mode |
| `/p3/ledgers/:id` | ledger + `.../records` + POST `.../fields` | No field PUT |
| `/p3/forms` | `GET /api/v1/p3/forms` | Form catalog |
| `/p3/forms/:id` | validate / publish / submissions | G5 rollback is server-side |
| `/p3/todos` | `/api/v1/p3/todos`, `/api/v1/todos/done\|initiated`, `/api/v1/process/tasks/*` | Sequential approval |

Do not treat this shell as a Keycloak Authorization Code + PKCE acceptance. Product login remains M0-04’s confidential client.
