# G1 leftover — FieldPolicy mask / second-auth honesty evidence

Recorded on this branch after rebase onto `origin/main` `6487b87` (Merge #153 claim-list tip align; includes #159 Vue `todo-withdraw`, #143 secret rotate-without-restart, and #155 ops `pg_dump` honesty). **Not** a product G1 close. **Not** a redo of #115 / #130 / #134 / #142 / #146. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. Mask / 二次授权 stay on `FieldPolicyProjector`.

See [docs/g1-fieldpolicy-mask-second-auth.md](../g1-fieldpolicy-mask-second-auth.md). Keep leftover rows [g7-withdraw-browser.md](../g7-withdraw-browser.md), [g2-ops-pg-dump.md](../g2-ops-pg-dump.md), and [s0-secret-rotate-without-restart.md](../s0-secret-rotate-without-restart.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | pending this run | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| Runtime | local `mvn test` + leftover script | No Docker / Compose |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"
./scripts/g1-fieldpolicy-mask-second-auth-honesty.sh
```

## Results (agent VM)

Filled after the honesty script on this VM.

| Check | Result | Notes |
|---|---|---|
| `FieldPolicyMaskHonestyTest` | pending | leftoverMaskFields + fixture gate |
| `G1FieldPolicyMaskSecondAuthHonestyTest` | pending | compiled SQL still selects raw `phone`; projector masks; export grant writes raw cells |
| Honest leftover fixture | pending | `g1_close=NOT_CLAIMED` |
| Fake `g1_close=CLOSED` | pending FAIL | honesty gate |
| Fake `mask_on_ast=true` | pending FAIL | honesty gate |
| `#115` / `#130` / `#134` / `#142` / `#146` | **not redone** | sibling leftovers |
| Product G1 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |

## Honest leftovers

- Encoding mask / 二次授权 onto Query AST
- IsolatedExport applying masks
- Unencodable DataScope leftovers / `COUNT(*)` as a Query AST node
- Product-wide G1 close
