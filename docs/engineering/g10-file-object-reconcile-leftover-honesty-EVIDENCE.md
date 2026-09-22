# G10 leftover — file-reconcile / object-reconcile PASS/SKIP leftover honesty beyond #243/#228/#202/#169/#179/#141 — evidence

Recorded on this branch after rebase onto `origin/main` `e10be1f` (Merge #259 G9 leftover honesty remainder after #249 G17 SoD/admin-force leftover honesty / #240 G15 H5/PKCE leftover honesty / #254 G2 H5/form.write/report leftover honesty / #236 Linux/ARM profile leftover honesty). **Not** a product G10 close. **Not** a redo of #141 / #169 / #179 / #202 / #228 / #243. **Not** identity apply. **Not** SAML / LDAP / SMS MFA. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. P0/M0 were not redone.

See [docs/g10-file-object-reconcile-leftover-honesty.md](../g10-file-object-reconcile-leftover-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md), including [g10-file-object-reconcile-remainder-honesty.md](../g10-file-object-reconcile-remainder-honesty.md) (#243), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g10-leftover-honesty.md](../g10-leftover-honesty.md) (#202), [g2-h5-every-page-form-write-report-remainder-honesty.md](../g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [g15-h5-pkce-miniprogram-remainder-honesty.md](../g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g17-separation-leftover-honesty-remainder.md](../g17-separation-leftover-honesty-remainder.md) (#249), [g9-leftover-honesty-remainder.md](../g9-leftover-honesty-remainder.md) (#259), [linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty.md](../g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), and every leftover-style sibling already on `origin/main`.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| MinIO | not listening on `127.0.0.1:19000` / `:9000` | `S3_*` unset |
| Keycloak | not listening on `127.0.0.1:8080` | OIDC discovery not reachable |
| Runtime | leftover honesty remainder inventory only | No Docker / Compose. `#141` / `#169` / `#179` / `#202` / `#228` / `#243` scripts not re-run |

## Commands

```bash
./scripts/g10-file-object-reconcile-leftover-honesty.sh
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report <out>/report.env
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/fake-live-minio-pass.env fail
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/fake-live-keycloak-pass.env fail
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/fake-live-apply-pass.env fail
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/fake-g10-close.env fail
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/fake-redone.env fail
./scripts/g10-file-object-reconcile-leftover-honesty.sh --verify-report \
  scripts/g10-file-object-reconcile-leftover-honesty/fixtures/fake-harness-as-live.env fail
```

Do **not** treat `./scripts/g10-file-object-reconcile-remainder-honesty.sh` or `./scripts/g10-leftover-honesty-remainder.sh` as this leftover. Those scripts are the #243 / #228 contracts. Parent `--verify-report` on honest fixtures is harness evidence. `#243` harness PASS is **not** a live MinIO / object-store walk.

## Results (agent VM)

Filled after the leftover honesty remainder script on this VM. Live output is gitignored under `.local/g10-file-object-reconcile-leftover-honesty/`. A sanitized copy is [`g10-file-object-reconcile-leftover-honesty/agent-vm.env`](./g10-file-object-reconcile-leftover-honesty/agent-vm.env).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live MinIO / object / Keycloak / apply PASS, fake `g10_close=CLOSED`, fake #243 redo, and fake #243-harness-as-live rejected |
| leftover #169 cells still honest | **PASS** | counting preview/apply + tenant-prefix stay PASS; committed live MinIO SKIP |
| leftover #179 cells still honest | **PASS** | dry-run default no-write; committed live Keycloak / apply SKIP |
| leftover #202 cells still honest | **PASS** | parent leftover honesty fixtures stay PASS; not a live MinIO walk |
| leftover #228 cells still honest | **PASS** | parent remainder honesty fixtures stay PASS; not a live MinIO / object walk |
| leftover #243 cells still honest | **PASS** | parent file/object remainder honesty fixtures stay PASS; not a live MinIO / object walk |
| Counting preview vs apply | **PASS** | from committed #169 artifact; this leftover does not re-run it |
| Tenant-prefix guard | **PASS** | from committed #169 artifact |
| Live MinIO / object store | **SKIP** (expected) | `S3_ENDPOINT` unset; nothing on `:19000` / `:9000` |
| Live object reconcile | **SKIP** (expected) | same walk; #243 harness PASS is not that walk |
| Live Keycloak 8080 dry-run | **SKIP** (expected) | nothing on `:8080`; OIDC discovery not reachable |
| live_preview_observed / live_dry_run_observed | **false** | honesty gate requires `false` on SKIP |
| Live apply | **SKIP** | product apply not redone |
| Sibling leftover rows | kept | #141 / #169 / #179 / #202 / #228 / #243 + this leftover + #254 G2 remainder / #240 G15 H5/PKCE remainder / #249 G17 SoD remainder / #259 G9 leftover remainder / #236 Linux/ARM remainder / #251 G4 remainder and every leftover-style sibling |
| #141/#169/#179/#202/#228/#243 redo | **false** | sibling leftovers |
| SAML / LDAP / SMS MFA | **false** | honesty gate |
| Product G10 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live MinIO / object-store preview + apply + tenant-prefix walk
- Treating #243 / #228 / #202 harness PASS as a live MinIO / object-store walk
- Live Keycloak 26.5.2 `:8080` detect + proposed-fix dry-run
- Product identity apply (`POST {"apply":true}`)
- Product G10 close
- SAML / LDAP / AD / SMS MFA / Keycloak Organizations
- Redo of #141 / #169 / #179 / #202 / #228 / #243

## Observed

Agent VM leftover honesty collect at `2026-09-22T22:50:05Z` after rebase onto `e10be1f`. Live MinIO / object / Keycloak **SKIP**. `g10_close=NOT_CLAIMED`. `leftover_259_kept=true`. `leftover_249_kept=true`. `leftover_240_kept=true`. `g18_status=closed_unchanged`.
