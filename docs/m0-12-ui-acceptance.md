# M0-12 UI & acceptance scripts

Work package for **tenant/org/permission shells** plus **dual-tenant adversarial scripts** against APIs that are already on `main` (M0-03 P1, M0-04 P2).

This package **does not close G18**. Ledger, form, publish, sequential approval, todo, query, and in-app notification wait for M0-06/07/08/10.

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

Then it runs `scripts/m0-12/dual-tenant-isolation.py` and `web/scripts/assert-client-isolation.mjs`.

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
| G1 | Query AST / export / file / cache / Job / Task full path | **DEFERRED** | M0-02 kernel + later modules |
| G1 | Guard-reject logs “zero leak” product claim | **DEFERRED** | isolation kernel evidence, not this UI |
| G2 | UI/API/list/detail/flow same permission engine | **DEFERRED** | M0-05 |
| G5 | Draft/Validate/Publish | **DEFERRED** | M0-07 |
| G8 | Health / trace id | **PASS** | actuator + `/api/v1/meta/runtime` |
| G9 | PolicySnapshot | **DEFERRED** | M0-05 |
| G15 | H5 | **DEFERRED** | S1 |
| G18 | Tenant create | **PASS** | P1 API + tenant admin UI |
| G18 | Org (person/dept/position/employment) | **PASS** | P2 API + org UI |
| G18 | Permission views | **SHELL ONLY** | stub pages, no API |
| G18 | Ledger | **SKIP** | TODO M0-06; script expects 404 |
| G18 | Form / publish | **SKIP** | TODO M0-07 |
| G18 | Business data / query | **SKIP** | TODO M0-06 |
| G18 | Sequential approval / todo | **SKIP** | TODO M0-08 |
| G18 | In-app notification | **SKIP** | TODO M0-10 |
| G18 | Full audit hash chain | **SKIP** | P1 staging read only; M0-11 |
| G18 | End-to-end real business walkthrough | **NOT CLOSED** | later packages |

Script status values: `PASS` (asserted), `SKIP` (honestly deferred), `FAIL` (regressed).

## Out of scope (explicit)

- Claiming G18 or G1 product-wide closed
- Implementing M0-05 permission engine
- Fake ledger/todo data
- Docker / Compose
- Browser Authorization Code + PKCE as accepted login
- GaussDB

## Evidence (this agent VM)

Recorded against local PostgreSQL **15.19** (`Ubuntu 15.19-1.pgdg24.04+2`), OpenJDK 21.0.10, Spring Boot 4.1.1, Stub IdP, memory IAM. Command: `./scripts/m0-12/run-dual-tenant.sh`.

`PASS=19 FAIL=0 SKIP=6`. G18 remains open (SKIP rows for M0-05/06/07/08/10). Vue `npm run build` and `npm run assert:isolation` succeeded.
