# NexusOne Web (M0-12)

Vue 3 shell for tenant admin, org/identity, and **P3 台账 / 表单 / 待办** pages.

- Tenant / org pages talk to merged **M0-03** and **M0-04** HTTP APIs.
- Ledger / form / todo pages default to a **MOCK** adapter (`src/api/mocks/`).
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
- Person / org / position pages use `Authorization` + `X-Nexus-Context-Id`.

Checks (no running server required):

```bash
cd web
npm run assert:isolation
npm run assert:p3-mock
```

## Toggle P3 MOCK → real APIs

Default is MOCK even though M0-06/07/08 HTTP APIs are on `main`. Toggle live only after you have a TenantContext.

| How | Command / action |
|---|---|
| Stay on fixtures | `npm run dev` (unset or any value except `false`/`0`) |
| Live HTTP | `VITE_NEXUS_P3_USE_MOCK=false npm run dev` |
| Runtime | P3 page banner **尝试真实 API** / **回到 MOCK** |
| Code | implement `src/api/p3-live.js`, keep `p3.js` method names |

Full notes: [src/api/mocks/README.md](./src/api/mocks/README.md).

MOCK markers: `_source: "MOCK"`, ids contain `-mock-`, orange banner. Reset: banner **重置 MOCK** or `sessionStorage.removeItem("nexus.m012.p3.mock")`.

## Pages

| Route | API | Notes |
|---|---|---|
| `/p1/tenants` | `GET /api/v1/p1/tenants` | Directory |
| `/p1/tenants/new` | `POST /api/v1/p1/tenants` | Optional P2 admin bootstrap |
| `/p1/tenants/:id` | config / quota / entitlement / audit | Enable/disable is confirm=true |
| `/p2/persons` | `/api/v1/persons` | Tenant-scoped by issued context |
| `/p2/org` | `/api/v1/departments/tree` | |
| `/p2/positions` | positions + employments | Position ≠ Role |
| `/p2/permissions/*` | none | TODO M0-05 |
| `/p3/ledgers` | MOCK or `GET /api/v1/p3/ledgers` | List + quota; M0-06 API on main |
| `/p3/ledgers/:id` | MOCK | Records + field designer shell |
| `/p3/forms` | MOCK or `GET /api/v1/p3/forms` | Form catalog; M0-07 API on main |
| `/p3/forms/:id` | MOCK | Fill / designer / publish shell |
| `/p3/todos` | MOCK or `GET /api/v1/p3/todos` | Inbox / done / initiated; M0-08 API on main |

Do not treat this shell as a Keycloak Authorization Code + PKCE acceptance. Product login remains M0-04’s confidential client.
