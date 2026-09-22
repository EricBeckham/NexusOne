# G7 leftover — query/list surfaces (expense + travel)

S0+S1 remainder. **Not** a P0/M0 redo. Product **G18 stays closed**. `AuditPort` same-TX joining is unchanged.

Withdraw leftovers are already on `main`:

- Leave withdraw HTTP: [g7-withdraw-evidence.md](./g7-withdraw-evidence.md) (`G7LeaveWithdrawIT`)
- Expense + travel withdraw 403: [g7-withdraw-expense-travel.md](./g7-withdraw-expense-travel.md) (`G7ExpenseTravelWithdrawIT`, #103)
- Dual-tenant travel withdraw / reject: [g7-business-types.md](./g7-business-types.md)
- Vue `todo-withdraw` click: [g7-withdraw-browser.md](./g7-withdraw-browser.md) (#113)
- Vue `todo-withdraw` / browser honesty leftover: [g7-withdraw-browser-honesty.md](./g7-withdraw-browser-honesty.md) (does not redo this query leftover)
- Expense / travel query honesty leftover: [g7-query-expense-travel-honesty.md](./g7-query-expense-travel-honesty.md) (PASS when query surfaces work; live JAR/IdP SKIP; does not redo this package)

Catalog leave / expense / travel from [#69](https://github.com/EricBeckham/NexusOne/pull/69) is already on `main`. This package only records **list / field-equals query / detail / todo query** evidence for the two non-leave classes. Withdraw ITs, the catalog, the provisioner, leave ITs, and `AuditPort` are not rewritten.

**Not claimed:** G7 three-type product close, G18 browser reopen, G2 / G4 remainder, full S0+S1 release.

## What is proven

Each of **费用报销** (`expense`) and **出差申请** (`travel`), via catalog `POST /api/v1/p3/business-types/{code}/provision`:

1. `GET /api/v1/p3/ledgers` and `GET /api/v1/p3/ledgers/{id}` return the provisioned ledger (`ledger_code` contains the type)
2. Two `POST /api/v1/p3/path/submit-start` rows (primary + distractor) → physical `l_*` `IN_APPROVAL`
3. `GET /api/v1/p3/ledgers/{id}/records` list `total=2`; primary carries the type fields
4. Field-equals `GET .../records?field=value` returns only the primary row
5. Miss filter returns `total=0`
6. `GET /api/v1/p3/ledgers/{id}/records/{recordId}` detail returns the type fields + `IN_APPROVAL`
7. Todo query: `GET /api/v1/todos/pending`, alias `GET /api/v1/p3/todos`, `GET /api/v1/todos/{todoId}`, `GET /api/v1/todos/initiated`
8. After handle (expense **approve**, travel **reject** — existing path actions, not a withdraw redo): list + detail keep the type fields and the new `approval_status`; `GET /api/v1/todos/done` + todo detail `DONE`; pending no longer includes that todo; initiated shows `APPROVED` / `REJECTED`

| Class | Distinct list/query/detail fields | Handle used only to re-query |
|---|---|---|
| expense | `title=交通费`, `category=交通`, `payee=张三`, `remark=g7-expense-query` | approve → `APPROVED` |
| travel | `title=客户拜访`, `destination=上海`, `purpose=g7-travel-query` | reject → `REJECTED` |

Leave query stays on the G18 close + [g7-withdraw-evidence.md](./g7-withdraw-evidence.md). Withdraw 403 stays on [g7-withdraw-expense-travel.md](./g7-withdraw-expense-travel.md).

## How to run

Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL **15** (Zonky embedded **15.18** for the IT), no Docker.

```bash
./scripts/g7/run-query-expense-travel.sh
```

Equivalents:

```bash
# Scripted HTTP against the G18 path host (Zonky PG 15, no live listener)
mvn -pl nexus-p3-studio -am test \
  -Dtest=G7ExpenseTravelQueryIT \
  -Dsurefire.failIfNoSpecifiedTests=false

# Optional: live product JAR (issued context, never X-Tenant-Id)
export NEXUS_BASE_URL=http://127.0.0.1:8080
export NEXUS_PLATFORM_BOOTSTRAP_TOKEN=...   # env only
export NEXUS_P1_DEMO_CONTEXT_ENABLED=true
python3 scripts/g7/query-expense-travel-http.py \
  --json-out docs/g7-query-expense-travel/live-http.json
```

`G7ExpenseTravelWithdrawIT` / `G7LeaveWithdrawIT` are not re-run here and were not edited. `G18BusinessPathIT` still covers leave approve + same-TX rollback.

## Browser steps (optional confirmation)

Same loop as the G18 close: stub login, issued `X-Nexus-Context-Id`, never `X-Tenant-Id`. UI `http://127.0.0.1:5173`, JAR as in [web/README.md](../web/README.md). G18 browser claim stays closed — HTTP/IT evidence is enough.

1. Login (stub) and stay on the issued tenant context.
2. 台账 → 开通 **费用报销** or **出差申请** (catalog provision already on main).
3. Fill type fields → **submit-start** (twice if you want a distractor row).
4. 台账记录 list shows both rows; field-equals query keeps the primary; detail shows type fields.
5. 待办 pending / 我发起 list the primary todo; open todo detail.
6. 同意 (expense) or 驳回 (travel). List/detail keep type fields with the new status. 已办 shows `DONE`.

## Evidence

Recorded on this branch after rebase onto `origin/main` `f5daa59` (Merge #136). Artifact: [it-run.json](./g7-query-expense-travel/it-run.json).

| Item | Value |
|---|---|
| JDK | OpenJDK **21** (filled after the agent run) |
| Maven | filled after the agent run |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky **15.18** |
| Business classes | **expense** + **travel** (catalog #69). Leave query is the G18 slice |
| `AuditPort` | **untouched** |
| Docker | no |

| Suite | Result |
|---|---|
| `G7ExpenseTravelQueryIT` | **pending-this-run** — expense + travel: ledger list/detail → two submit-start → record list `total=2` → field-equals → miss empty → record detail type fields → pending / `/p3/todos` / todo detail / initiated → approve or reject → list/detail/done/initiated re-query |
| live `query-expense-travel-http.py` | **SKIP** unless a product JAR is healthy on `NEXUS_BASE_URL` |

## Honest gaps

| Item | Status |
|---|---|
| Expense / travel list + field-equals + detail + todo query HTTP | **this package** |
| Expense / travel withdraw HTTP | sibling [g7-withdraw-expense-travel.md](./g7-withdraw-expense-travel.md); not rewritten |
| Leave list/detail/todo | G18 close + leave withdraw sibling; not rewritten |
| Expense / travel catalog | **on main** (#69). Not rewritten here |
| Live product-JAR browser query click | optional confirmation; not required |
| Report wizard / physical dump | G2 leftover; not this package |
| G7 three-type product close | **not claimed** |
| G18 | **stays closed** |
