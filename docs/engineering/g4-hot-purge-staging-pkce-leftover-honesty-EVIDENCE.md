# G4 leftover — 90-day hot-purge / P1 staging / PKCE SPA leftover honesty beyond #251/#230/#222 — evidence

Recorded on this branch after rebase onto `origin/main` `2ac6326` (Merge #263 Linux/ARM leftover remainder honesty after #262 G10 leftover honesty / #259 G9 leftover honesty remainder / #249 G17 SoD leftover honesty remainder / #240 G15 leftover honesty / #254 G2 leftover honesty / #236 Linux/ARM leftover honesty / #251 G4 leftover honesty). **Not** a product G4 close. **Not** a redo of #123 / #147 / #156 / #177 / #183 / #190 / #222 / #230 / #251. **Not** a live `:5432` / JAR prefix DELETE. **Not** a live Keycloak token exchange. **Not** a switch of `GET /tenants/{id}/audit-events` off staging. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. `platform.audit_record` was **not** dropped. P0 / M0 were not redone.

See [docs/g4-hot-purge-staging-pkce-leftover-honesty.md](../g4-hot-purge-staging-pkce-leftover-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g4-hot-purge-staging-pkce-remainder-honesty.md](../g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty-remainder.md](../g17-separation-leftover-honesty-remainder.md) (#249), [g9-leftover-honesty-remainder.md](../g9-leftover-honesty-remainder.md) (#259), [g10-file-object-reconcile-leftover-honesty.md](../g10-file-object-reconcile-leftover-honesty.md) (#262), [linux-arm-profile-leftover-remainder-honesty.md](../linux-arm-profile-leftover-remainder-honesty.md) (#263), [g15-h5-pkce-miniprogram-remainder-honesty.md](../g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g2-h5-every-page-form-write-report-remainder-honesty.md](../g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md) (#236), and the other leftover rows already on `main`.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | leftover does not start the JAR |
| PostgreSQL `:5432` | not observed | #251/#230 collect is not a live purge PASS |
| Keycloak | not observed | #251/#222 collect is not a live token exchange |
| Runtime | leftover honesty inventory only | No Docker / Compose. `#251` / `#230` / `#222` scripts not re-run |

## Commands

```bash
./scripts/g4-hot-purge-staging-pkce-leftover-honesty.sh
./scripts/g4-hot-purge-staging-pkce-leftover-honesty.sh --verify-report <out>/report.env
./scripts/g4-hot-purge-staging-pkce-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g4-hot-purge-staging-pkce-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-leftover-honesty/fixtures/fake-live-purge-pass.env
./scripts/g4-hot-purge-staging-pkce-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-leftover-honesty/fixtures/fake-live-staging-pass.env
./scripts/g4-hot-purge-staging-pkce-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-leftover-honesty/fixtures/fake-live-pkce-pass.env
./scripts/g4-hot-purge-staging-pkce-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-leftover-honesty/fixtures/fake-g4-close.env
./scripts/g4-hot-purge-staging-pkce-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-leftover-honesty/fixtures/fake-redone.env
./scripts/g4-hot-purge-staging-pkce-leftover-honesty.sh --verify-report \
  scripts/g4-hot-purge-staging-pkce-leftover-honesty/fixtures/fake-251-as-live.env
```

Do **not** treat `./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh`, `./scripts/g4-hot-purge-staging-leftover-honesty.sh`, or `./scripts/g4-pkce-spa-verifier-remainder-honesty.sh` as this leftover. Those scripts are the #251 / #230 / #222 contracts.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Live output is gitignored under `.local/g4-hot-purge-staging-pkce-leftover-honesty/`. A sanitized copy is [`g4-hot-purge-staging-pkce-leftover-honesty/agent-vm.env`](./g4-hot-purge-staging-pkce-leftover-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | PASS / expected FAIL | leftover #251/#230/#222 cells PASS; fake close / fake live PASS / fake redo / fake #251-as-live rejected |
| leftover #251 hot-purge / staging / PKCE remainder cells | PASS | parent leftover honesty fixtures stay honest; not a live walk; not re-run |
| leftover #230 hot-purge / staging cells | PASS | parent leftover honesty fixtures stay honest; not re-run |
| leftover #222 PKCE SPA remainder cells | PASS | parent leftover honesty fixtures stay honest; not re-run |
| leftover #183 / #190 / #177 cells | PASS | sibling leftover cells stay honest |
| Window policy / staging-like / SPA mint | PASS | always-green leftover cells |
| Live `:5432` / JAR 90-day prefix DELETE | SKIP | no live PG / JAR; #251/#230 collect is not a live purge PASS |
| Live Quartz `audit.hot.purge` | SKIP | schedule defaults off |
| Live MinIO cold drill | SKIP | no live MinIO on `:9000` |
| Live `:5432` staging query+export | SKIP | listener / walk missing |
| Live JAR / issued context | SKIP | leftover does not start the JAR |
| Live Keycloak SPA PKCE | SKIP | discovery unreachable; #251/#222 collect is not a live token exchange |
| Live CA trust / laptop click | SKIP | not observed |
| Sibling leftover rows | kept | #251 / #230 / #222 + this leftover + #249 G17 SoD remainder + #240 G15 remainder + #254 G2 remainder + #236 Linux/ARM remainder and other siblings |
| #251/#230/#222/#183/#190/#177/#147 redo | **false** | sibling leftovers |
| Product G4 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Live product `:5432` 90-day prefix DELETE
- Live Quartz / MinIO / staging query+export / JAR walk
- Live Keycloak SPA-held token exchange / trusted laptop CA click
- Product G4 close
- Treating #251 / #230 / #222 harness PASS as a live walk
- Redo of #251 / #230 / #222
