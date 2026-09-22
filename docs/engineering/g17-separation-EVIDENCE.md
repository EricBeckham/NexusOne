# G17 三权分立 slice — mvn evidence

Recorded on this branch after rebase onto `origin/main` (`ce365dd`, includes G2 #73/#91/#93, G4 #77/#86/#90/#92, G17 admin-MFA #96). **Not** a full G17 close. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g17-separation-of-duties.md](../g17-separation-of-duties.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10 | `java -version` |
| Maven | 3.8.7 | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| PostgreSQL | Zonky **15.18** | `G17AuditAdminIT` / `G4AuditCoverageIT` / `G18BusinessPathIT` |
| Runtime | local `mvn test` | No Docker / Compose |

`SHOW server_version` / Zonky start log:

```
starting PostgreSQL 15.18 on x86_64-pc-linux-gnu
Database: ******** (PostgreSQL 15.18)
```

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"

mvn -pl nexus-permission-core -am test \
  -Dtest=G17SeparationEngineTest,G17DutyAssignTest,G17AuditSurfaceTest,G17PlatformDutyApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false

mvn -pl nexus-p3-studio -am test \
  -Dtest=G18BusinessPathIT \
  -Dsurefire.failIfNoSpecifiedTests=false

mvn -pl nexus-bootstrap -am test \
  -Dtest=G17AuditAdminIT,G4AuditCoverageIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## Results (agent VM)

| Suite | Finished at (UTC) | Result |
|---|---|---|
| `G17SeparationEngineTest` + `G17DutyAssignTest` + `G17AuditSurfaceTest` + `G17PlatformDutyApiTest` | **2026-09-22T16:55:50Z** | **BUILD SUCCESS** — Tests run: **6**, Failures: **0**, Errors: **0**, Skipped: **0** |
| `G18BusinessPathIT` | **2026-09-22T16:57:50Z** | **BUILD SUCCESS** — Tests run: **4**, Failures: **0**, Errors: **0**, Skipped: **0** |
| `G17AuditAdminIT` | **2026-09-22T17:00:18Z** | **BUILD SUCCESS** — Tests run: **1**, Failures: **0**, Errors: **0**, Skipped: **0** |
| `G4AuditCoverageIT` | **2026-09-22T17:01:01Z** | **BUILD SUCCESS** — Tests run: **1**, Failures: **0**, Errors: **0**, Skipped: **0** |

What those tests prove:

- Three mutually exclusive duty roles (`platform_system_admin` / `platform_security_admin` / `platform_audit_admin`) with disjoint GRANT sets and audit-mutation DENY cells.
- Assign of a second duty in the same tenant is **409** `P2.PERM.0014`; evaluate of a conflicting pair is `duty_conflict`.
- Audit-admin **GET** `/api/v1/audit/traces/{id}` **200**; `PUT /config`, `DELETE /traces/{id}`, `POST /admin` **403** and both `p2.permission.denied` + `isolation.guard.reject` land on `platform.audit_record` via `appendNewTransaction`. Login row remains; `verifyChain()` intact.
- G18 leave path (HTTP happy path + in-process rollback) stays green. In-process `SequentialApprovalService.guard` seeds the existing `tenant_member` demo role the same way form/list surfaces do (no second auth system).

## Honest leftovers

- Full G17 (密码机 / classified / password-policy product close) is **not** this PR. Local 国密 TLS 双证书 leftover is [g17-gm-tls-dual-cert.md](../g17-gm-tls-dual-cert.md).
- Admin-session MFA is a **separate** slice already on main ([g17-admin-mfa.md](../g17-admin-mfa.md)); not redone here.
- P1 tenant APIs still use demo `ALL_DEMO` headers; they are not rewired onto P2 duty roles.
- No product UI to assign the three duties.
- Audit config endpoints refuse mutation; there is no retention-policy store.
- Tenant-scoped GET traces stays open for callers who hold **no** platform duty (G4 / G18 leave path).
- `AuditPort` same-TX append semantics were **not** changed.
