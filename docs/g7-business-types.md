# G7 three business types (S0+S1 remainder)

Work package: add **two more distinct real business classes** on the existing ledger / form / sequential-approval / todo / query / in-app notify stack so G7 has **three** classes. Product G18 already closed **请假**. This package adds **费用报销** and **出差申请**, and records **withdraw** evidence that the G18 browser walk did not click.

Baseline unchanged: Java 21, Spring Boot **4.1.1**, PostgreSQL 15, Flowable **7.1.0**, Flyway **13.7.0**. No Docker. `AuditPort` / hash-chain same-TX joining is not modified (G4 parallel).

**Not claimed:** full S0+S1 release, G2 permission-matrix UI, G4 login/permission/admin/export/guard-reject on the same chain, G1 export/file/cache/Job/Task, G15 H5.

## Three classes

| Class | Code | Distinct fields (plus `approval_status`) | Handle evidenced |
|---|---|---|---|
| 请假 | `leave` | `title`, `days`, `applicant`, `reason` | approve (G18 browser + this IT) |
| 费用报销 | `expense` | `title`, `amount`, `category`, `payee`, `remark` | reject (this IT) |
| 出差申请 | `travel` | `title`, `destination`, `days`, `purpose`, `applicant` | **withdraw** (this IT) |

Each type: provision → Validate/Publish → `submit-start` (initiate) → approve **or** reject **or** withdraw → query physical `l_*` row → todo boxes → in-app notify list → audit by trace.

## API

| Method | Path |
|---|---|
| GET | `/api/v1/p3/business-types` |
| POST | `/api/v1/p3/business-types/{code}/provision` body `{ "publish": true, "suffix": "optional" }` |
| POST | `/api/v1/p3/path/submit-start` |
| POST | `/api/v1/p3/path/tasks/{todoId}/approve` |
| POST | `/api/v1/p3/path/tasks/{todoId}/reject` |
| POST | `/api/v1/p3/path/instances/{instanceId}/withdraw` |

Provision reuses M0-06 `ApplicationService` / `LedgerDefinitionService` and M0-07 `FormService`. Path actions are the existing G18 `BusinessPathService`. No second DDL path. No `AuditPort` change.

`tenant_member` seed now includes `process.instance` / `withdraw` (start / approve / reject already existed). Flow `guard()` still only hard-fails `explicit_deny` / `tenant_boundary`.

## How to run / verify

```bash
# Catalog + three types + withdraw (embedded PostgreSQL 15.18, no Docker)
mvn -pl nexus-p3-studio -am test \
  -Dtest=BusinessTypeCatalogTest,G7BusinessTypesIT \
  -Dsurefire.failIfNoSpecifiedTests=false

# Seed now exposes withdraw
mvn -pl nexus-permission-core -am test \
  -Dtest=TenantMemberGrantSeederTest,TenantMemberGrantSeedApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# UI wiring (no server)
cd web && npm run assert:p3-live
```

Browser / JAR (same G18 loop: stub login, issued `X-Nexus-Context-Id`, never `X-Tenant-Id`):

1. 台账 → 创建台账 → pick 请假 / 报销 / 出差 → 开通此类业务（Validate + Publish）。
2. 表单填报 `submit-start`。
3. 待办：同意或驳回；或「我发起」点 **撤回**（`data-testid=todo-withdraw`）。
4. 台账记录 `approval_status` = `APPROVED` / `REJECTED` / `WITHDRAWN`；通知与按 trace 审计可查。

## Withdraw evidence (scripted)

`G7BusinessTypesIT.travelWithdrawIsIdempotentAndLeavesAuditEvidence`:

- `submit-start` on catalog `travel` → physical row `IN_APPROVAL`, todo PENDING, initiated RUNNING
- initiator `POST .../instances/{id}/withdraw` → process `WITHDRAWN`, row `approval_status=WITHDRAWN`, query returns `destination=上海`
- second withdraw stays `WITHDRAWN` (idempotent)
- `platform.audit_record` for the trace includes `p3.path.submitted`, `p3.approval.started`, `p3.approval.withdrawn`
- `GET /api/v1/todos/initiated` status `WITHDRAWN`; `GET /api/v1/notifications` accepts issued context
- hash chain `verifyChain()` intact

G18 browser close on `38f1a73` approved leave and did **not** click withdraw. This IT is the withdraw evidence for the shared path.
