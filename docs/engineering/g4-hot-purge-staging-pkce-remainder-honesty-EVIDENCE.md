# G4 leftover — 90-day hot-purge / P1 staging / PKCE SPA remainder honesty beyond #230/#222 — evidence

Recorded on this branch after rebase onto `origin/main` `3081fa7` (Merge #243 G10 file/object reconcile leftover honesty after #241 SecretPort rotate/getenv leftover honesty / #235 G5 PublishHook/DDL leftover honesty remainder / #233 G17 MFA/OTP leftover honesty remainder / #238 G1 FieldPolicy mask leftover honesty / #239 G7 expense/travel query leftover honesty remainder / #237 G9 Redis-down leftover honesty remainder / #232 G2 H5/form.write leftover honesty remainder / #212 G7 withdraw browser-evidence leftover honesty / #230 G4 hot-purge/P1 staging leftover honesty / #222 G4 SPA PKCE remainder leftover honesty). **Not** a product G4 close. **Not** a redo of #123 / #147 / #156 / #177 / #183 / #190 / #222 / #230. **Not** a live `:5432` / JAR prefix DELETE. **Not** a live Keycloak token exchange. **Not** a switch of `GET /tenants/{id}/audit-events` off staging. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `platform.audit_record` was **not** dropped. P0 / M0 were not redone.

See [docs/g4-hot-purge-staging-pkce-remainder-honesty.md](../g4-hot-purge-staging-pkce-remainder-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g5-publishhook-ddl-remainder-honesty.md](../g5-publishhook-ddl-remainder-honesty.md) (#235), [s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g10-file-object-reconcile-remainder-honesty.md](../g10-file-object-reconcile-remainder-honesty.md) (#243), [g17-mfa-recovery-vue-otp-remainder-honesty.md](../g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](../g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), and the other leftover rows already on `main`.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | leftover does not start the JAR |
| PostgreSQL `:5432` | not observed | #230 collect is not a live purge PASS |
| Keycloak | not observed | #222 collect is not a live token exchange |
| Runtime | leftover honesty inventory only | No Docker / Compose. `#230` / `#222` scripts not re-run |

## Commands

```bash
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report <out>/report.env
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-remainder-honesty/fixtures/fake-live-purge-pass.env
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-remainder-honesty/fixtures/fake-live-staging-pass.env
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-remainder-honesty/fixtures/fake-live-pkce-pass.env
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-remainder-honesty/fixtures/fake-g4-close.env
./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-remainder-honesty/fixtures/fake-redone.env
```

Do **not** treat `./scripts/g4-hot-purge-staging-leftover-honesty.sh` or `./scripts/g4-pkce-spa-verifier-remainder-honesty.sh` as this leftover. Those scripts are the #230 / #222 contracts.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Live output is gitignored under `.local/g4-hot-purge-staging-pkce-remainder-honesty/`. A sanitized copy is [`g4-hot-purge-staging-pkce-remainder-honesty/agent-vm.env`](./g4-hot-purge-staging-pkce-remainder-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | PASS / expected FAIL | leftover #230/#222 cells PASS; fake close / fake live PASS / fake redo rejected |
| leftover #230 hot-purge / staging cells | PASS | parent leftover honesty fixtures stay honest; not re-run |
| leftover #222 PKCE SPA remainder cells | PASS | parent leftover honesty fixtures stay honest; not re-run |
| leftover #183 / #190 / #177 cells | PASS | sibling leftover cells stay honest |
| Window policy / staging-like / SPA mint | PASS | always-green leftover cells |
| Live `:5432` / JAR 90-day prefix DELETE | SKIP | no live PG / JAR; #230 collect is not a live purge PASS |
| Live Quartz `audit.hot.purge` | SKIP | schedule defaults off |
| Live MinIO cold drill | SKIP | no live MinIO on `:9000` |
| Live `:5432` staging query+export | SKIP | listener / walk missing |
| Live JAR / issued context | SKIP | leftover does not start the JAR |
| Live Keycloak SPA PKCE | SKIP | discovery unreachable; #222 collect is not a live token exchange |
| Live CA trust / laptop click | SKIP | not observed |
| Sibling leftover rows | kept | #230 / #222 + this leftover + #243 `g10-file-object-reconcile-remainder-honesty` + #241 `s0-secret-rotate-getenv-leftover-honesty` + #235 `g5-publishhook-ddl-remainder-honesty` + #233 `g17-mfa-recovery-vue-otp-remainder-honesty` + #238 `g1-fieldpolicy-mask-second-auth-remainder-honesty` + #239 and other siblings |
| #230/#222/#183/#190/#177/#147 redo | **false** | sibling leftovers |
| Product G4 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Live product `:5432` 90-day prefix DELETE
- Live Quartz / MinIO / staging query+export / JAR walk
- Live Keycloak SPA-held token exchange / trusted laptop CA click
- Product G4 close
- Redo of #230 / #222
