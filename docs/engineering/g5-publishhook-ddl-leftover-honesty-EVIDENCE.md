# G5 leftover — PublishHook / real SchemaService DDL leftover honesty remainder beyond #235/#217 — evidence

Recorded on this branch after rebase onto `origin/main` `2ac6326` (Merge #263 Linux/ARM leftover honesty remainder after #262 G10 leftover honesty remainder / #259 G9 leftover honesty remainder / #249 G17 SoD/admin-force leftover honesty / #240 G15 H5/PKCE leftover honesty / #254 G2 H5/form.write/report leftover honesty / #236 Linux/ARM profile leftover honesty remainder / #251 G4 hot-purge/staging/PKCE leftover honesty / #243 G10 file/object reconcile leftover honesty / #241 SecretPort rotate/getenv leftover honesty / #235 G5 PublishHook/DDL leftover honesty remainder / #217 G5 PublishHook/DDL abort leftover honesty). **Not** a product G5 close. **Not** a redo of #94 / #145 / #171 / #196 / #217 / #235. **Not** a live PG 15 CREATE/evolve publish. **Not** a live product JAR PublishHook publish. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. SchemaService abort semantics already on `main` were not rewritten. P0 / M0 were not redone.

See [docs/g5-publishhook-ddl-leftover-honesty.md](../g5-publishhook-ddl-leftover-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g5-publishhook-ddl-remainder-honesty.md](../g5-publishhook-ddl-remainder-honesty.md) (#235), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g15-h5-pkce-miniprogram-remainder-honesty.md](../g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g9-leftover-honesty-remainder.md](../g9-leftover-honesty-remainder.md) (#259), [g10-file-object-reconcile-leftover-honesty.md](../g10-file-object-reconcile-leftover-honesty.md) (#262), [linux-arm-profile-leftover-remainder-honesty.md](../linux-arm-profile-leftover-remainder-honesty.md) (#263), [g17-separation-leftover-honesty-remainder.md](../g17-separation-leftover-honesty-remainder.md) (#249), [g2-h5-every-page-form-write-report-remainder-honesty.md](../g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty.md](../g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty.md](../g10-file-object-reconcile-remainder-honesty.md) (#243), [s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md) (#241), and the other leftover rows already on `main`.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Live PG 15 / JAR / IdP / browser | not required | honest SKIP when unavailable |
| Runtime | leftover honesty inventory only | No Docker / Compose. `#235` / `#217` scripts not re-run |

## Commands

```bash
./scripts/g5-publishhook-ddl-leftover-honesty.sh
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report <out>/report.env
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/fake-live-ddl-publish-pass.env
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/fake-publishhook-publish-pass.env
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/fake-235-as-live-publish.env
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/fake-217-abort-as-publish.env
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/fake-g5-close.env
./scripts/g5-publishhook-ddl-leftover-honesty.sh --verify-report \
  scripts/g5-publishhook-ddl-leftover-honesty/fixtures/fake-redone.env
```

Do **not** treat `./scripts/g5-publishhook-ddl-remainder-honesty.sh` or `./scripts/g5-publishhook-ddl-honesty.sh` as this leftover. Those scripts are the #235 / #217 contracts.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Live output is gitignored under `.local/g5-publishhook-ddl-leftover-honesty/`. A sanitized copy is [`g5-publishhook-ddl-leftover-honesty/agent-vm.env`](./g5-publishhook-ddl-leftover-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | PASS / expected FAIL | leftover #235/#217 cells PASS; fake close / fake live PASS / fake redo rejected |
| leftover #235 publish remainder cells | PASS | parent leftover honesty fixtures stay honest; not re-run |
| leftover #217 abort honesty cells | PASS | parent leftover honesty fixtures stay honest; not re-run |
| leftover #196 mid-publish cells | PASS | sibling leftover cells stay honest |
| #217 abort SKIP is live publish | false | honesty gate |
| #235 remainder SKIP is live publish | false | honesty gate |
| Memory / confirm-only / Zonky as live publish | false | honesty gate |
| Live real-DDL publish | SKIP | no live PG 15 / observed CREATE/evolve publish; #235 collect is not a live publish PASS |
| Live PublishHook publish | SKIP | product `SchemaServicePublishHook` → `ensurePhysicalTable` publish not observed |
| Sibling leftover rows | kept | #235 / #217 + this leftover + #259 `g9-leftover-honesty-remainder` + #262 `g10-file-object-reconcile-leftover-honesty` + #263 `linux-arm-profile-leftover-remainder-honesty` + #249 `g17-separation-leftover-honesty-remainder` + #240 `g15-h5-pkce-miniprogram-remainder-honesty` + #254 `g2-h5-every-page-form-write-report-remainder-honesty` + #236 `linux-arm-profile-remainder-honesty` + #251 `g4-hot-purge-staging-pkce-remainder-honesty` + #243 `g10-file-object-reconcile-remainder-honesty` + #241 `s0-secret-rotate-getenv-leftover-honesty` and other siblings |
| #235/#217/#196/#171/#145/#94 redo | **false** | sibling leftovers |
| Product G5 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Live PG 15 + product `JdbcSchemaService` CREATE / evolve publish through `SchemaServicePublishHook`
- Live product JAR PublishHook → real SchemaService DDL publish
- Live JAR + IdP + browser fail-click of `data-testid=form-publish`
- Product G5 close
- Redo of #235 / #217
