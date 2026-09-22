# G10 leftover — Keycloak 8080 identity drift-fix dry-run evidence

Recorded on this branch after rebase onto `origin/main` `23656f3` (Merge #144). **Not** a product G10 close. **Not** a file-reconcile redo. **Not** SAML / LDAP / SMS MFA. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g10-identity-drift-dry-run.md](../g10-identity-drift-dry-run.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21.0.10+7-Ubuntu-124.04 | product baseline |
| Maven | pending this run | `$HOME/tools/apache-maven-3.8.7` when present |
| Spring Boot parent | **4.1.1** | product BOM |
| Keycloak | **26.5.2** native `kc.sh start-dev` (no Docker) | product issuer `http://127.0.0.1:8080/realms/nexus` |
| Runtime | local `mvn test` + leftover script | No Docker / Compose / OpenBao / KMS |

## Commands

```bash
export PATH="$HOME/tools/apache-maven-3.8.7/bin:$PATH"
./scripts/g10-identity-drift-dry-run.sh
```

Apply is refused on this leftover:

```bash
# expected FAIL (exit 2)
NEXUS_G10_APPLY=1 ./scripts/g10-identity-drift-dry-run.sh
```

## Results (agent VM)

Filled after `./scripts/g10-identity-drift-dry-run.sh` on this VM. Live output is gitignored under `.local/g10-identity-drift-dry-run/`.

| Check | Result | Notes |
|---|---|---|
| Stub detect + proposed `SYNC_P2` / `REBIND` / `PROVISION_IDP` | pending | `repaired=0`; P2 / IdP rows unchanged |
| HTTP GET preview returns `proposed` without write | pending | `IdentityApiTest` |
| `NEXUS_G10_APPLY=1` refused | pending | leftover stays dry-run |
| Live Keycloak **8080** dry-run | pending | SKIP if `kc.sh` / discovery missing |
| SAML / LDAP / SMS MFA | **NOT_CLAIMED** | honesty gate |
| File reconcile | **not redone** | [g10-file-reconcile.md](../g10-file-reconcile.md) |
| G18 / AuditPort | unchanged | not reopened |

## Honest leftovers

- Product G10 close (identity apply + file half already on `main`; this is dry-run evidence)
- SAML, LDAP / AD, SMS MFA, Keycloak Organizations
- Human laptop + live Keycloak CA / SPA verifier (G4)
- File metadata ↔ object store (other half; not this PR)
- Keycloak upgrade / rollback matrix (26.5.2 only)
- Docker / Compose as the IdP run path
