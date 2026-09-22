# NexusOne Web (M0-12)

Minimal Vue 3 shell for tenant admin, org/identity, and permission placeholders.
It talks only to already merged **M0-03** and **M0-04** HTTP APIs. It does **not** close G18.

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

Client isolation check (no running server required):

```bash
cd web && npm run assert:isolation
```

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
| `/p3/ledgers\|todos` | none | TODO M0-06/08 |
| `/p3/forms` | `GET /api/v1/p3/forms` | API is M0-07; this page is still a stub (M0-12) |

Do not treat this shell as a Keycloak Authorization Code + PKCE acceptance. Product login remains M0-04’s confidential client.
