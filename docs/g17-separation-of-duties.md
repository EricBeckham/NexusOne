# G17 slice — 三权分立 (ARCH-CMP-002 / P1-M05-G01-F003)

S0+S1 PostgreSQL release work on M0-05 `PermissionEngine` / `SurfaceGuard` and the existing M0-11 hash chain. **G18 stays closed.** This is not a redo of P0/M0 packages, does not fork TenantContext, and does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules.

This package is **one G17 slice**: platform three-way separation of duties. It does **not** close full G17 (classified / GM TLS dual certs / password policy / 密码机). Admin-session MFA is a separate slice already on main ([g17-admin-mfa.md](./g17-admin-mfa.md)); this slice does not redo it.

## What this slice delivers

Three mutually exclusive P2 system roles, catalogued and installable without assigning the current person:

| Role code | Duty | Grant set (ALL scope) | Explicit DENY |
|---|---|---|---|
| `platform_system_admin` | 系统管理员 | `tenant` create/enable/disable/list/get; `tenant.config` get/update; `tenant.entitlement` get; `quota` consume/release | `audit` write/delete/admin |
| `platform_security_admin` | 安全管理员 | `permission.role` / `permission.grant` / `permission.assignment` write; `security.policy` write; `security.mfa` write | `audit` write/delete/admin |
| `platform_audit_admin` | 审计管理员 | `audit` read + verify | `audit` write/delete/admin; `audit.config` write; `audit.chain` mute/delete |

Install: `POST /api/v1/permission/duties/example`. Read: `GET /api/v1/permission/duties`. Same repository and `PermissionEngine` as G2. `tenant_member` is unchanged. In-process flow `guard()` seeds that demo role the same way `FormSurfaceGuard` / `PermissionDataScopeHook` do so the G18 rollback probe is not `no_grant`.

## Conflict enforcement (same tenant / platform scope)

A principal cannot hold any pair of the three duties.

| Gate | Behaviour |
|---|---|
| Assign | `PolicyAdminService.assign` expands inherited roles and rejects with `P2.PERM.0014` (409). Reject uses `appendNewTransaction` → `p2.permission.denied`. |
| Inherit | Duty role cannot parent another duty role (`P2.PERM.0014`). |
| Evaluate | `PermissionEngine` after tenant-boundary: two or more duty roles → `duty_conflict` deny (403 via `SurfaceGuard`). |

## Audit-admin path

`GET /api/v1/audit/traces/{traceId}` stays tenant-scoped for callers who do **not** hold a platform duty (G4 / G18 leave path). When the caller holds a duty role, the GET goes through `SurfaceGuard.require(audit, read)` — audit-admin is allowed; system/security admin is `no_grant`.

WRITE / DELETE / admin-of-audit always go through the same engine:

| Method | Resource / action | Audit-admin |
|---|---|---|
| `PUT /api/v1/audit/config` | `audit.config` / `write` | 403 `explicit_deny` |
| `DELETE /api/v1/audit/traces/{id}` | `audit` / `delete` | 403 `explicit_deny` |
| `POST /api/v1/audit/admin` | `audit` / `admin` | 403 `explicit_deny` |
| `POST /api/v1/audit/chain/mute` | `audit.chain` / `mute` | 403 `explicit_deny` |

Denied surfaces emit **both** `p2.permission.denied` and `isolation.guard.reject` on the existing hash chain via `appendNewTransaction`. The chain itself is not rewritten, muted, or deleted.

## Honest leftovers (not claimed)

- Full G17: qualified GM/T provider, 密码机, password/session product close, classified delivery. Local 国密 TLS 双证书 leftover is [g17-gm-tls-dual-cert.md](./g17-gm-tls-dual-cert.md) (not a close)
- Admin-session MFA (already on main as [g17-admin-mfa.md](./g17-admin-mfa.md); not redone here)
- P1 `IsolationGuard` still uses demo `ALL_DEMO` headers; this slice does not rewire P1 tenant APIs onto P2 duty roles
- Product UI to assign the three duties
- Audit config persistence (endpoints refuse mutation; no retention-policy store)
- G4 GET traces remains open to any issued tenant context that does **not** hold a platform duty

## Tests

```bash
mvn -pl nexus-permission-core,nexus-bootstrap -am test \
  -Dtest=G17SeparationEngineTest,G17DutyAssignTest,G17AuditSurfaceTest,G17PlatformDutyApiTest,G17AuditAdminIT,G4AuditCoverageIT,G18BusinessPathIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

See [engineering/g17-separation-EVIDENCE.md](./engineering/g17-separation-EVIDENCE.md) for the recorded run.
