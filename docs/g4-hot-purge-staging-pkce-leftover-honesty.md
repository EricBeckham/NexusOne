# G4 leftover — 90-day hot-purge / P1 staging / PKCE SPA leftover honesty beyond #251/#230/#222

S0+S1 remainder **after** the #251 90-day hot-purge / P1 staging / PKCE SPA remainder leftover honesty ([g4-hot-purge-staging-pkce-remainder-honesty.md](./g4-hot-purge-staging-pkce-remainder-honesty.md)), the #230 90-day hot-purge / P1 staging leftover honesty ([g4-hot-purge-staging-leftover-honesty.md](./g4-hot-purge-staging-leftover-honesty.md)), and the #222 live Keycloak CA / SPA PKCE remainder leftover ([g4-pkce-spa-verifier-remainder-honesty.md](./g4-pkce-spa-verifier-remainder-honesty.md)). Rebased onto `origin/main` `2ac6326` (Merge #263 Linux/ARM leftover remainder honesty after #262 G10 leftover honesty / #259 G9 leftover honesty remainder / #249 G17 SoD leftover honesty remainder / #240 G15 leftover honesty / #254 G2 leftover honesty / #236 Linux/ARM leftover honesty / #251 G4 leftover honesty). **G18 stays closed.** This leftover does **not** redo #123 / #147 / #156 / #177 / #183 / #190 / #222 / #230 / #251 product code. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** DROP `platform.audit_record`. It does **not** switch `GET /tenants/{id}/audit-events` off staging. It does **not** start `kc.sh`. It does **not** redo P0 / M0. It does **not** claim product G4 close. `g4_close=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [g4-hot-purge-staging-pkce-remainder-honesty](./g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g4-hot-purge-staging-leftover-honesty](./g4-hot-purge-staging-leftover-honesty.md) (#230), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty-remainder](./g17-separation-leftover-honesty-remainder.md) (#249), [g9-leftover-honesty-remainder](./g9-leftover-honesty-remainder.md) (#259), [g10-file-object-reconcile-leftover-honesty](./g10-file-object-reconcile-leftover-honesty.md) (#262), [linux-arm-profile-leftover-remainder-honesty](./linux-arm-profile-leftover-remainder-honesty.md) (#263), [g15-h5-pkce-miniprogram-remainder-honesty](./g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g2-h5-every-page-form-write-report-remainder-honesty](./g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty](./linux-arm-profile-remainder-honesty.md) (#236), this file, and every leftover-style sibling already on `main`. Do **not** drop the #249 `g17-separation-leftover-honesty-remainder` row, the #259 `g9-leftover-honesty-remainder` row, the #262 `g10-file-object-reconcile-leftover-honesty` row, the #263 `linux-arm-profile-leftover-remainder-honesty` row, the #240 `g15-h5-pkce-miniprogram-remainder-honesty` row, or any other leftover honesty row.

#251 already inventories leftover #230/#222 cells as always-green **PASS** and live `:5432` / JAR / Quartz / MinIO / Keycloak as honest **SKIP**. That #251 harness **PASS** is **not** a live purge, **not** a live staging walk, **not** a live Keycloak token exchange, and **not** product G4 (`leftover_251_is_not_live_purge=true`, `combined_251_230_222_is_not_product_g4=true`). The leftover here is the **honesty remainder beyond those three**: those cells stay honest; parent leftover honesty fixtures stay **PASS**. Live purge / staging / JAR / Keycloak / CA / laptop click are **PASS** only when a live path is present **and** the walk was observed. Missing live path is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #251 leftover cells | Window policy **PASS**, staging-like **PASS**, SPA mint **PASS**, live purge / Quartz / MinIO / staging / JAR / Keycloak **SKIP**, `g4_close=NOT_CLAIMED`. `leftover_251_cells_honest=PASS`. `leftover_251_honesty_gate=PASS`. `#251` harness is **not** a live walk. Do not redo. |
| #230 leftover cells | Window policy **PASS**, staging-like **PASS**, live purge **SKIP**, `g4_close=NOT_CLAIMED`. `leftover_230_cells_honest=PASS`. Do not redo. |
| #222 leftover cells | SPA mint **PASS**, #177 harness **PASS**, live Keycloak **SKIP**, `g4_close=NOT_CLAIMED`. `leftover_222_cells_honest=PASS`. Do not redo. |
| Combined harness vs product G4 | `combined_251_230_222_is_not_product_g4=true`. |
| Live `:5432` / JAR 90-day prefix DELETE | `live_purge_window=PASS` only when live PG + product JAR + observed prefix DELETE. Missing live path is honest **SKIP**. |
| Live Quartz / MinIO / staging / JAR / Keycloak / CA / laptop | **PASS** only when those walks were observed. Missing path is honest **SKIP**. |
| Switching P1 GET off staging | Still **OPEN**. `switched_off_staging=false`. |
| Product G4 close | **NOT_CLAIMED**. |
| Sibling leftover rows | `docs/README.md` keeps #251 / #230 / #222 / #249 G17 SoD remainder and this leftover, plus every leftover-style sibling already on `main`. |
| Honesty gate | `--verify-report` rejects fake live PASS, rejects treating #251 as a live walk, rejects `g4_close=CLOSED`, rejects `hot_table_dropped=true`, rejects `switched_off_staging=true`, rejects #251/#230/#222/#183/#190/#177/#147 redo flags, and requires leftover rows kept (including #249 / #240 / #254 / #236 / #251). |

No Docker. No product-JAR start. No `kc.sh`. `#251` / `#230` / `#222` stay the sibling reproduction commands. Parent `--verify-report` on already-committed honesty fixtures is allowed as harness evidence.

## What this does not claim

| Leftover | Why |
|---|---|
| Product G4 close | Explicitly `g4_close=NOT_CLAIMED`. Honesty gate rejects `CLOSED`. Combining #251+#230+#222 harness PASS is not product G4. |
| #251 remainder as live purge / staging / Keycloak | `leftover_251_is_not_live_purge=true`. Remainder honesty is not a live walk. |
| Live `:5432` / JAR 90-day prefix DELETE | Honest SKIP when live path is missing. #251/#230 collect is not a live walk. |
| Live Quartz / MinIO / staging / Keycloak / laptop CA click | Honest SKIP when unobserved. |
| Switching P1 GET off staging | `switched_off_staging=false`. Still open. |
| #123 / #147 / #156 / #177 / #183 / #190 / #222 / #230 / #251 | Sibling leftovers on `main`. `redo_251=false`. `redo_230=false`. `redo_222=false`. |
| G18 reopen / `AuditPort` / P0/M0 redo | Closed / untouched / out of scope. |

## How to run

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

#251 / #230 / #222 stay the sibling reproduction commands. This leftover does **not** invoke them:

```bash
# do not run for this leftover
# ./scripts/g4-hot-purge-staging-pkce-remainder-honesty.sh
# ./scripts/g4-hot-purge-staging-leftover-honesty.sh
# ./scripts/g4-pkce-spa-verifier-remainder-honesty.sh
```

Parent `--verify-report` on already-committed #251 / #230 / #222 honesty fixtures is harness evidence.

Java 21 / Boot **4.1.1**. No Docker. Live product-JAR / `:5432` / Keycloak are **not** required.

See [engineering/g4-hot-purge-staging-pkce-leftover-honesty-EVIDENCE.md](./engineering/g4-hot-purge-staging-pkce-leftover-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g4-hot-purge-staging-pkce-leftover-honesty/`.

## Honest leftovers (not claimed)

- Live product `:5432` 90-day prefix DELETE + suffix `verifyChain()`
- Live Quartz `audit.hot.purge` at 03:00 UTC
- Live MinIO / object-store cold drill as a purge prerequisite
- Switching P1 GET off staging onto M0-11
- Live `:5432` query+export on a host that actually has `platform.audit_event`
- Live Keycloak Authorization Code + PKCE with an SPA-held verifier
- Human laptop click that trusts the loopback CA
- Product-wide G4 close
- Treating #251 / #230 / #222 harness PASS as a live walk
- Redo of #251 / #230 / #222 / #183 / #190 / #177 / #147
