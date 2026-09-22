# G17 leftover — 三权分立 admin-force vs dual-control honesty remainder beyond #216/#187/#99 — evidence

Recorded on this branch after rebase onto `origin/main` `8f759c1` (Merge #240 G15 H5/PKCE leftover honesty after #254 G2 H5/form.write/report leftover honesty after #236 Linux/ARM remainder leftover honesty / #251 G4 hot-purge/staging/PKCE leftover honesty after #243 G10 file/object reconcile leftover honesty / #241 SecretPort rotate/getenv leftover honesty / #235 G5 PublishHook/DDL remainder leftover honesty / #233 G17 MFA recovery / Vue OTP remainder leftover honesty / #238 G1 FieldPolicy mask leftover honesty / #239 G7 expense/travel query leftover honesty / #237 G9 Redis-down/invalidate leftover honesty / #232 G2 H5 every-page / form.write leftover honesty). **Not** a full G17 close. **Not** a product SoD close. **Not** a product dual-control / maker-checker close. **Not** 等保 / 密评 / 密码机 / HSM. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. #96 / #99 / #187 / #216 were **not** redone. P0/M0 were not redone.

See [docs/g17-separation-leftover-honesty-remainder.md](../g17-separation-leftover-honesty-remainder.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g17-separation-of-duties.md](../g17-separation-of-duties.md) (#99), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), this leftover, [g15-h5-pkce-miniprogram-remainder-honesty.md](../g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g2-h5-every-page-form-write-report-remainder-honesty.md](../g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), [linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md) (#236), [g4-hot-purge-staging-pkce-remainder-honesty.md](../g4-hot-purge-staging-pkce-remainder-honesty.md) (#251), [g10-file-object-reconcile-remainder-honesty.md](../g10-file-object-reconcile-remainder-honesty.md) (#243), [g17-mfa-recovery-vue-otp-remainder-honesty.md](../g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [s0-secret-rotate-getenv-leftover-honesty.md](../s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g5-publishhook-ddl-remainder-honesty.md](../g5-publishhook-ddl-remainder-honesty.md) (#235), [g1-fieldpolicy-mask-second-auth-remainder-honesty.md](../g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), and the other leftover rows already on `main`.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Keycloak | not listening | loopback `:8080` / `:8180` `/realms/nexus` |
| Runtime | leftover honesty inventory only | No Docker / Compose. `#99` / `#187` / `#216` scripts not re-run |

## Commands

```bash
./scripts/g17-separation-leftover-honesty-remainder.sh
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report <out>/report.env
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/fake-live-admin-force-pass.env fail
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/fake-live-dual-control-pass.env fail
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/fake-g17-close.env fail
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/fake-dual-control-close.env fail
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/fake-redo.env fail
./scripts/g17-separation-leftover-honesty-remainder.sh --verify-report \
  scripts/g17-separation-leftover-honesty-remainder/fixtures/fake-harness-as-live.env fail
```

Do **not** treat `./scripts/g17-separation-leftover-honesty.sh` or `./scripts/g17-separation-honesty.sh` as this leftover. Those scripts are the #216 / #187 contracts.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Live output is gitignored under `.local/g17-separation-leftover-honesty-remainder/`. A sanitized copy is [`g17-separation-leftover-honesty-remainder/agent-vm.env`](./g17-separation-leftover-honesty-remainder/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | PASS / expected FAIL | leftover #99/#187/#216 cells PASS; fake close / fake live PASS / fake redo / fake harness-as-live rejected |
| leftover #99 catalog | PASS | exclusive pairs / `duty_conflict` stay; not a #99 IT redo |
| leftover #187 honesty cells | PASS | parent leftover honesty fixtures stay honest; not re-run |
| leftover #216 honesty cells | PASS | parent leftover honesty fixtures stay honest; harness PASS is not a live walk |
| Live admin-force | SKIP | `NEXUS_BASE_URL` unset; nothing on `:8080` / `:8088`; live IdP unreachable |
| Live dual-control | SKIP | not observed |
| Sibling leftover rows | kept | #99 / #187 / #216 + this leftover + #233 `g17-mfa-recovery-vue-otp-remainder-honesty` + #241 `s0-secret-rotate-getenv-leftover-honesty` + #235 `g5-publishhook-ddl-remainder-honesty` + #238 `g1-fieldpolicy-mask-second-auth-remainder-honesty` + #239 `g7-query-expense-travel-honesty-remainder` + #237 `g9-redis-down-invalidate-remainder-honesty` + #232 `g2-h5-every-page-form-write-remainder-honesty` and other siblings |
| #96/#99/#187/#216 redo | **false** | sibling leftovers |
| Product G17 / SoD / dual-control close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redesigned / not redone |

## Honest leftovers

- Live admin-force HTTP walk
- Live dual-control (maker + checker) walk
- Product SoD / dual-control / 等保 / 密评 / 完整 G17 close
- Redo of #96 / #99 / #187 / #216
