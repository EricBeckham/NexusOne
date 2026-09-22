# G5 leftover — form publish mid-failure rollback

S0+S1 remainder after the G5 forms package already on `main` (#70 validate/hook, #85 SchemaService gate, #94 product CREATE/evolve + evolve DDL abort). **Not** a P0/M0 redo. Product **G18 stays closed**. `AuditPort` same-TX joining is unchanged.

This package only adds **mid-publish failure** evidence **beyond** the existing evolve `23502` abort: after Running v1 exists, a failed v2 publish keeps `form.running_version`, the RUNNING `form_version` row, and `snapshot_json` identical to the pre-failure pointer, and writes `form_publish_log.outcome=ROLLED_BACK`.

**Not claimed:** product G5 close, P4/app-package publish, form designer, G7/G2/G4/G1 remainder, G18 reopen.

Browser failed-publish click / PublishHook honesty leftover is the sibling [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) (live browser/IdP/JAR **SKIP**). PASS/SKIP honesty leftover beyond this file and #171 is [g5-mid-publish-failure-honesty.md](./g5-mid-publish-failure-honesty.md). PublishHook / real SchemaService DDL honesty leftover beyond #196/#171/#145 is [g5-publishhook-ddl-honesty.md](./g5-publishhook-ddl-honesty.md). Keep both leftover rows, plus the honesty leftovers.

Existing evolve-abort / validate-fail / CREATE-success evidence stays on [g5-form-publish-rollback.md](./g5-form-publish-rollback.md). This leftover does not rewrite `FormService` / `SchemaService` / `AuditPort`.

## What is proven

| Failure | How it is produced | Must stay unchanged |
|---|---|---|
| Product CREATE abort after Running | v1 published; `DROP` physical table; plant a same-name `SEQUENCE` so v2 `createPhysicalTable` hits PG 15 `42P07` (`SchemaService create failed`) | `running_version=1`, RUNNING row, `snapshot_json`, no v2 `form_version`, table still absent, `schema_version=1`, `ROLLED_BACK` |
| SchemaService work then abort | v1 published; optional `remark` declared; `ensurePhysicalTable` **evolves successfully** inside the publish TX; hook then throws `mid-publish abort after SchemaService` | same pointer + snapshot; `remark` column absent (DDL rolled back); `schema_version=1`; `ROLLED_BACK` |
| HTTP / memory leftover | `SchemaServicePublishHook` finishes `ensurePhysicalTable`, then aborts on the second publish | HTTP `P3.FORM.0006`; Running v1 + snapshot; `ROLLED_BACK` |

The product path still has no “fail publish” switch. CREATE abort is a real `JdbcSchemaService` + PG 15 relation clash, not a `ConfirmThenAbortEvolve` wrapper.

## How to run (no Docker)

Baseline: Java 21, Spring Boot **4.1.1**, PostgreSQL **15** (Zonky embedded **15.18**), no Docker.

```bash
./scripts/g5-form-publish-rollback.sh
```

Equivalent:

```bash
mvn -pl nexus-p3-studio -am test \
  -Dtest=JdbcFormPublishRollbackIT,FormPublishFailureApiTest,FormPublishStateMachineTest,SchemaServicePublishHookTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## Evidence

Recorded **pending-this-run** on this agent VM. `./scripts/g5-form-publish-rollback.sh` → **pending**. No Docker. No live `127.0.0.1:5432`. `AuditPort` untouched.

| Item | Value |
|---|---|
| JDK | OpenJDK **21** |
| Maven | **3.8.x** |
| Spring Boot | **4.1.1** |
| PostgreSQL | Zonky **15.18** |

| Suite | Result | What it proved |
|---|---|---|
| `JdbcFormPublishRollbackIT` | pending | #94 cases plus CREATE abort after Running (`42P07`) and evolve-then-abort (remark column rolled back); pointer + snapshot unchanged |
| `FormPublishFailureApiTest` | pending | HTTP leftover: abort after `ensurePhysicalTable` → `P3.FORM.0006`, Running v1 + snapshot + `ROLLED_BACK` |
| `FormPublishStateMachineTest` | pending | Memory leftover: abort after SchemaService work leaves Running + snapshot |
| `SchemaServicePublishHookTest` | pending | abort-after-ensure propagates |

## Honest leftovers (not claimed)

| Still open | Why |
|---|---|
| Product G5 close | Forms failed-path IT only. App / P4 publish, designer, and browser fail-click are out of this package. |
| Browser failed publish click | sibling leftover [g5-browser-failed-publish.md](./g5-browser-failed-publish.md); UI publish-log is read-only and is not a fail-click |
| First-publish CREATE abort (no Running yet) | Leftover prefers the Running-pointer contract after v1 |
| Live `127.0.0.1:5432` product JAR fail-publish | Evidence is Zonky / MockMvc |
| G7 / G2 / G4 / G1 remainder | Parallel packages |
| G18 reopen | Closed; success publish stays on [g18-business-path.md](./g18-business-path.md) |
