# G7 withdraw evidence (leave path)

S0+S1 remainder. **Not** a P0/M0 redo. Product **G18 stays closed**.

Eric’s G18 browser walk on `38f1a73` approved 请假 and **did not click 撤回**. This package records that missing step as **scripted HTTP** on the existing leave path already on `main`. Parallel [#69](https://github.com/EricBeckham/NexusOne/pull/69) may add expense / travel types; this package stays orthogonal and does **not** touch those files, `AuditPort`, or the `tenant_member` seeder.

**Not claimed:** G7 closed (still need three distinct real businesses), G2 / G4 remainder, full S0+S1 release.

## What is proven

Leave (G18 class `leave` / 请假):

1. `POST /api/v1/p3/path/submit-start` → physical `l_*` row `IN_APPROVAL`, process `RUNNING`, todo `PENDING`
2. Non-initiator `POST /api/v1/p3/path/instances/{id}/withdraw` → **403**
3. Initiator withdraw → process `WITHDRAWN`, row `approval_status=WITHDRAWN`, query returns the leave fields
4. Second withdraw stays `WITHDRAWN` (idempotent; no second `p3.approval.withdrawn`)
5. `GET /api/v1/todos/initiated` shows `WITHDRAWN`
6. `GET /api/v1/audit/traces/{traceId}` includes `p3.path.submitted`, `p3.approval.started`, `p3.approval.withdrawn`
7. Hash chain `verifyChain()` intact (IT). `AuditPort` implementation is unchanged.

Travel / expense withdraw is left to #69 once it lands. Prefer re-running `scripts/g7/withdraw-leave-http.py` against a travel form after that merge; the path API is the same.

## How to run

Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL **15** (Zonky embedded **15.18** for the IT), no Docker.

```bash
./scripts/g7/run-withdraw-leave.sh
```

Equivalents:

```bash
# Scripted HTTP against the G18 path host (Zonky PG 15, no live listener)
mvn -pl nexus-p3-studio -am test \
  -Dtest=G7LeaveWithdrawIT \
  -Dsurefire.failIfNoSpecifiedTests=false

# Optional: live product JAR (issued context, never X-Tenant-Id)
export NEXUS_BASE_URL=http://127.0.0.1:8080
export NEXUS_PLATFORM_BOOTSTRAP_TOKEN=...   # env only
export NEXUS_P1_DEMO_CONTEXT_ENABLED=true
python3 scripts/g7/withdraw-leave-http.py --json-out docs/g7-withdraw-evidence/live-http.json
```

`G18BusinessPathIT` is not re-run here. It still covers approve + same-TX rollback.

## Browser steps (leave, after the G18 shell)

Same loop as the G18 close: stub login, issued `X-Nexus-Context-Id`, never `X-Tenant-Id`. UI `http://127.0.0.1:5173`, JAR as in [web/README.md](../web/README.md).

1. Login (stub) and stay on the issued tenant context.
2. 台账 → create 请假 ledger + 表单 → Validate → Publish (or reuse the G18 leave form).
3. Fill 年假 / days / applicant / reason → **submit-start** (not `form_submission`-only).
4. 待办 → **我发起** → select the RUNNING instance → **撤回**.
5. Ledger record `approval_status=WITHDRAWN`. 我发起 status `WITHDRAWN`.
6. Sidebar 审计 → paste the withdraw `X-Trace-Id` → events include `p3.approval.withdrawn`.

The withdraw control is the ghost **撤回** button on `/p3/todos?box=initiated` (only when the selected instance is still pending). Parallel #69 may add `data-testid=todo-withdraw`; this package does not edit that Vue file.

This agent recorded the **HTTP** path. A second human click of 撤回 is optional confirmation, not required to keep G18 closed.

## Evidence

Recorded **2026-09-22T15:40:45Z** on this branch with `./scripts/g7/run-withdraw-leave.sh`. Artifact: [it-run.json](./g7-withdraw-evidence/it-run.json).

| Item | Value |
|---|---|
| JDK | OpenJDK **21.0.10** (`21.0.10+7-Ubuntu-124.04`) |
| Maven | **3.8.7** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky **15.18** (`Database: PostgreSQL 15.18`; Flyway V100→…→**V550**) |
| Business class | **leave** (G18 请假). Travel deferred to #69 |
| `AuditPort` | **untouched** |
| Docker | no |

| Suite | Result |
|---|---|
| `G7LeaveWithdrawIT` | **1/1** `BUILD SUCCESS` — submit-start → non-initiator 403 → initiator `WITHDRAWN` → repeat withdraw stays `WITHDRAWN` (one `p3.approval.withdrawn`) → query 年假 / `g7-withdraw` → initiated `WITHDRAWN` → `audit_record` ≥ 3 + `verifyChain()` intact |
| live `withdraw-leave-http.py` | **SKIP** — no product JAR on `:8080`. Script is ready for a live loop; it also hits `GET /api/v1/audit/traces/{id}` (bootstrap controller, not on the G18 path host) |

## Honest gaps

| Item | Status |
|---|---|
| Leave withdraw HTTP (submit-start → withdraw → query → audit) | **this package** |
| Leave withdraw browser click | Documented above; G18 walk did not click it |
| Expense / travel types | **#69**, not this PR |
| `tenant_member` seed `process.instance/withdraw` | **#69**. Current `guard()` only hard-fails `explicit_deny` / `tenant_boundary`, so initiator withdraw already succeeds on `main` |
| G7 three-type close | **open** |
| G18 | **stays closed** |
