# G7 withdraw evidence (expense + travel leftover)

S0+S1 remainder. **Not** a P0/M0 redo. Product **G18 stays closed**. `AuditPort` same-TX joining is unchanged.

Leave-path withdraw HTTP is already recorded in [g7-withdraw-evidence.md](./g7-withdraw-evidence.md) (`G7LeaveWithdrawIT`). Expense / travel **catalog** from [#69](https://github.com/EricBeckham/NexusOne/pull/69) is already on `main`. This package only adds the **same withdraw evidence shape** for those two catalog types: initiator withdraw, non-initiator **403**, idempotent replay, query fields, initiated box, and audit events.

**Not claimed:** G7 three-type product close, G18 browser reopen, G2 / G4 remainder, full S0+S1 release. Catalog / provisioner / seeder / leave IT / `AuditPort` are not rewritten here.

## What is proven

Each of **费用报销** (`expense`) and **出差申请** (`travel`), via catalog `POST /api/v1/p3/business-types/{code}/provision`:

1. `POST /api/v1/p3/path/submit-start` → physical `l_*` row `IN_APPROVAL`, process `RUNNING`, todo `PENDING`
2. Non-initiator `POST /api/v1/p3/path/instances/{id}/withdraw` → **403**
3. Initiator withdraw → process `WITHDRAWN`, row `approval_status=WITHDRAWN`, query returns the type fields
4. Second withdraw stays `WITHDRAWN` (idempotent; no second `p3.approval.withdrawn`)
5. `GET /api/v1/todos/initiated` shows `WITHDRAWN`
6. `AuditPort.findByTraceId` includes `p3.path.submitted`, `p3.approval.started`, `p3.approval.withdrawn`
7. Hash chain `verifyChain()` intact. `AuditPort` implementation is unchanged.

| Class | Distinct query fields asserted after withdraw |
|---|---|
| expense | `title=交通费`, `category=交通`, `payee=张三`, `remark=g7-expense-withdraw` |
| travel | `title=客户拜访`, `destination=上海`, `purpose=g7-travel-withdraw` |

Leave stays on [g7-withdraw-evidence.md](./g7-withdraw-evidence.md). Dual-tenant travel walkthrough stays on [g7-business-types.md](./g7-business-types.md) (#69 / #80).

## How to run

Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL **15** (Zonky embedded **15.18** for the IT), no Docker.

```bash
./scripts/g7/run-withdraw-expense-travel.sh
```

Equivalents:

```bash
# Scripted HTTP against the G18 path host (Zonky PG 15, no live listener)
mvn -pl nexus-p3-studio -am test \
  -Dtest=G7ExpenseTravelWithdrawIT \
  -Dsurefire.failIfNoSpecifiedTests=false

# Optional: live product JAR (issued context, never X-Tenant-Id)
export NEXUS_BASE_URL=http://127.0.0.1:8080
export NEXUS_PLATFORM_BOOTSTRAP_TOKEN=...   # env only
export NEXUS_P1_DEMO_CONTEXT_ENABLED=true
python3 scripts/g7/withdraw-expense-travel-http.py \
  --json-out docs/g7-withdraw-expense-travel/live-http.json
```

`G7LeaveWithdrawIT` is not re-run here and was not edited. `G18BusinessPathIT` still covers approve + same-TX rollback.

## Browser steps (optional confirmation)

Same loop as the G18 close: stub login, issued `X-Nexus-Context-Id`, never `X-Tenant-Id`. UI `http://127.0.0.1:5173`, JAR as in [web/README.md](../web/README.md). G18 browser claim stays closed — HTTP/IT evidence is enough. The leftover Vue click of `data-testid=todo-withdraw` (stub login, not a G7 product close) is [g7-withdraw-browser.md](./g7-withdraw-browser.md).

1. Login (stub) and stay on the issued tenant context.
2. 台账 → 开通 **费用报销** or **出差申请** (catalog provision already on main).
3. Fill type fields → **submit-start**.
4. 待办 → **我发起** → **撤回** (`data-testid=todo-withdraw`).
5. Ledger record `approval_status=WITHDRAWN`. 我发起 status `WITHDRAWN`.
6. Sidebar 审计 → paste the withdraw `X-Trace-Id` → events include `p3.approval.withdrawn`.

## Evidence

Recorded **2026-09-22T17:14:20Z** on this branch after rebase onto `92fd7b5` (`#100`) with `./scripts/g7/run-withdraw-expense-travel.sh`. Artifact: [it-run.json](./g7-withdraw-expense-travel/it-run.json).

| Item | Value |
|---|---|
| JDK | OpenJDK **21.0.10** (`21.0.10+7-Ubuntu-124.04`) |
| Maven | **3.8.7** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky **15.18** (`Database: PostgreSQL 15.18`; Flyway V100→**V550**) |
| Business classes | **expense** + **travel** (catalog #69). Leave withdraw is a sibling package |
| `AuditPort` | **untouched** |
| Docker | no |

| Suite | Result |
|---|---|
| `G7ExpenseTravelWithdrawIT` | **2/2** `BUILD SUCCESS` (9.535 s) — expense and travel: submit-start → non-initiator 403 → initiator `WITHDRAWN` → repeat withdraw stays `WITHDRAWN` (one `p3.approval.withdrawn`) → query type fields → initiated `WITHDRAWN` → `audit_record` ≥ 3 + `verifyChain()` intact |
| live `withdraw-expense-travel-http.py` | **SKIP** — no product JAR on `:8080`. Script is ready for a live loop |

## Honest gaps

| Item | Status |
|---|---|
| Expense withdraw HTTP (submit-start → 403 → withdraw → query → audit) | **this package** |
| Travel withdraw HTTP with non-initiator 403 + single `p3.approval.withdrawn` | **this package** (catalog travel withdraw without 403 was already on #69) |
| Leave withdraw HTTP | sibling [g7-withdraw-evidence.md](./g7-withdraw-evidence.md); not rewritten |
| Expense / travel catalog | **on main** (#69). Not rewritten here |
| Expense / travel list / detail / todo query | sibling [g7-query-expense-travel.md](./g7-query-expense-travel.md); not this package |
| G7 three-type product close | **not claimed** |
| G18 | **stays closed** |
