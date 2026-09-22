# G17 三权分立 leftover honesty — admin-force vs dual-control evidence

Recorded on this branch after rebase onto `origin/main` `e695290` (Merge #218 S0+S1 claim-list tip-sync after #220 G15 wx-dt/native-bridge remainder leftover honesty; includes #215 G1 BoundSql/COUNT/export FieldPolicy leftover honesty, #210 G17 Tongsuo/Nginx GMTLS handshake leftover honesty, #217 G5 PublishHook/DDL leftover honesty, #209 G9 Sentinel/Cluster remainder leftover honesty, #214 tip-sync, #188 G2 ops pg_dump leftover honesty, #205 MFA recovery/OTP leftover honesty, #197 G9 Redis-down invalidate remainder honesty, and #204 G2 H5/form.write leftover honesty). **Not** a full G17 close. **Not** a product SoD close. **Not** a product dual-control / maker-checker close. **Not** 等保 / 密评 / 密码机 / HSM. **Not** a G18 reopen. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned. #96 / #99 / #187 were **not** redone. P0/M0 were not redone.

See [docs/g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [g17-separation-of-duties.md](../g17-separation-of-duties.md) (#99), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), this leftover, [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g17-mfa-recovery-vue-otp-honesty.md](../g17-mfa-recovery-vue-otp-honesty.md) (#205), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g9-redis-down-invalidate-remainder.md](../g9-redis-down-invalidate-remainder.md) (#197), and the other leftover rows listed in the leftover doc.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Keycloak | not listening | loopback `:8080` / `:8180` `/realms/nexus` |
| Runtime | leftover honesty inventory only | No Docker / Compose. `#99` / `#187` scripts not re-run |

## Commands

```bash
./scripts/g17-separation-leftover-honesty.sh
./scripts/g17-separation-leftover-honesty.sh --verify-report <out>/report.env
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-live-admin-force-pass.env fail
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-live-dual-control-pass.env fail
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-g17-close.env fail
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-dual-control-close.env fail
./scripts/g17-separation-leftover-honesty.sh --verify-report \
  scripts/g17-separation-leftover-honesty/fixtures/fake-redo.env fail
```

Do **not** treat `./scripts/g17-separation-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Live output is gitignored under `.local/g17-separation-leftover-honesty/`. A sanitized copy is [`g17-separation-leftover-honesty/agent-vm.env`](./g17-separation-leftover-honesty/agent-vm.env) once recorded. Walk JSON: [`g17-separation-leftover-honesty/walk.json`](../g17-separation-leftover-honesty/walk.json).

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live admin-force / dual-control PASS, fake G17/SoD/dual-control close, and fake redo rejected |
| leftover #99 cells still honest | **PASS** | exclusive pairs / assign reject / `duty_conflict` stay on `main` |
| leftover #187 cells still honest | **PASS** | committed live SoD SKIP; `g17_sod_close=NOT_CLAIMED` |
| No admin-force bypass API | **true** | source scan; G2 SurfaceGuard still required |
| G4 deny-on-chain / same-TX | **PASS** | `appendNewTransaction` pair unchanged |
| Live admin force HTTP | **SKIP** (expected) | live JAR unset; IdP unreachable |
| Live dual-control (maker + checker) | **SKIP** (expected) | no observed two-person walk |
| Product dual-control UI | **OPEN** | absent |
| P1 `ALL_DEMO` / product duty UI | **OPEN** | still bypasses / still absent |
| Sibling leftover rows | kept | #99 / #187 + this leftover + #197 / #204 / #205 / #188 / #209 / #217 / #210 / #215 / #220 + all sibling leftover rows |
| #96/#99/#187 redo | **false** | sibling leftovers |
| Product G17 / SoD / dual-control close | **NOT_CLAIMED** | honesty gate |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR + live IdP admin force HTTP walk
- Live dual-control (maker + checker) walk
- Product dual-control / maker-checker UI and close
- Rewiring P1 tenant APIs off `ALL_DEMO` onto P2 duty roles
- Product UI to assign the three duties
- Qualified GM/T Provider, 密码机 / HSM, classified / 等保 / 密评 delivery
- Product G17 close
- Redo of #96 / #99 / #187
