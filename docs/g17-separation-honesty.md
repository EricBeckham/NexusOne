# G17 leftover — 三权分立 (SoD) honesty beyond #99

S0+S1 remainder after the #99 three-power slice ([g17-separation-of-duties.md](./g17-separation-of-duties.md)). Rebased onto `origin/main` `cf8fc8b` (after #183 G4 90-day hot-purge honesty leftover, #182 S0+S1 claim-list tip, #184 G1 FieldPolicy mask / second-auth honesty leftover, #179 G10 identity drift-fix honesty leftover, #181 G15 native-bridge honesty leftover, #177 G4 SPA verifier honesty leftover, #180 S0 getenv snapshot leftover, and #171 G5 browser failed-publish leftover). **G18 stays closed.** This leftover does **not** redo #96 `AdminMfaPolicy`, #99 `PlatformDutyCatalog` / assign / evaluate / audit-admin ITs, #110 dual-cert, #118 HTTP TOTP, #129 dual-stack, #133 enrollment UX, #154 recovery/email contract, #158 Vue otp write-back, #160 Tongsuo handshake, or #172 recovery/email delivery honesty. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** claim 等保 / 密评 / 完整 G17 / product SoD close. G17 close is **NOT_CLAIMED**. `g17_sod_close=NOT_CLAIMED`.

Keep **both** leftover rows in [docs/README.md](./README.md): `g17-separation-of-duties` (#99) and this file. Also keep g17-* siblings (MFA / TOTP / Tongsuo / recovery / Vue otp), the #171 sibling [g5-browser-failed-publish.md](./g5-browser-failed-publish.md), the #177 sibling [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md), the #179 sibling [g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md), the #180 sibling [s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md), the #181 sibling [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md), the #183 sibling [g4-hot-purge-90d-honesty.md](./g4-hot-purge-90d-honesty.md), and the #184 sibling [g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md).

#99 already catalogues three mutually exclusive platform duties (`platform_system_admin` / `platform_security_admin` / `platform_audit_admin`) and rejects a second duty (`P2.PERM.0014` / `duty_conflict`). The leftover here is the **honesty inventory** for remaining SoD enforcement paths:

| Remaining path | Honesty |
|---|---|
| Live JAR + IdP admin HTTP boundary | admin cannot mutate audit. **PASS** only when observed. |
| Live JAR + IdP auditor HTTP boundary | auditor cannot operate tenant create / permission write. **PASS** only when observed. |
| Live JAR + IdP operator HTTP boundary | `tenant_member` operator cannot hold or exercise platform duties. **PASS** only when observed. |
| P1 `IsolationGuard` `ALL_DEMO` | Still stamps every duty. **OPEN**, not rewired onto P2 roles. |
| Product duty-assign UI | Still absent. **OPEN**. |

Missing live IdP / JAR is an honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Surface | Behavior |
|---|---|
| #99 catalog inventory | Three exclusive pairs + disjoint GRANT sets stay in `PlatformDutyCatalog`. `tenant_member` is **not** a platform duty (operator). Not a #99 IT redo. |
| P1 ALL_DEMO | `Actor.has` still treats `ALL_DEMO` as every duty. `DemoPlatformContextFilter` still stamps it. Status stays `OPEN`. |
| Product duty UI | No Vue page assigns `platform_system_admin` / duties. Status stays `OPEN`. |
| Live SoD walk | `PASS` only with reachable product JAR + reachable IdP + issued context + observed admin **and** auditor **and** operator HTTP boundaries. |
| Honesty gate | `--verify-report` rejects fake `live_sod_walk=PASS` without those proofs, rejects `g17_close=CLOSED` / `g17_sod_close=CLOSED`, rejects `p1_all_demo_rewired=true`, and rejects #96/#99/#110/#118/#129/#133/#154/#158/#160/#172 redo flags. |

`admin` = `platform_system_admin`. `auditor` = `platform_audit_admin`. `operator` = `tenant_member` (not a platform duty). Security-admin stays the #99 sibling duty and is not re-walked here.

## How to run

```bash
# Always-green #99 catalog inventory + live probe (honest SKIP when JAR/IdP down)
./scripts/g17-separation-honesty.sh

# Honesty gate only
./scripts/g17-separation-honesty.sh --verify-report \
  scripts/g17-separation-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g17-separation-honesty.sh --verify-report \
  scripts/g17-separation-honesty/fixtures/fake-live-pass.env
./scripts/g17-separation-honesty.sh --verify-report \
  scripts/g17-separation-honesty/fixtures/fake-g17-close.env
./scripts/g17-separation-honesty.sh --verify-report \
  scripts/g17-separation-honesty/fixtures/fake-p1-rewired.env
```

`PASS` for live SoD requires all of: reachable product JAR, reachable Keycloak issuer + OIDC discovery, issued context, and observed admin / auditor / operator HTTP boundaries. This leftover does not start the JAR or IdP. #99 ITs stay on that leftover:

```bash
# do not run for this leftover
# mvn -pl nexus-permission-core,nexus-bootstrap -am test -Dtest=G17SeparationEngineTest,...
```

If a live product JAR + IdP is later listening (native process, not Docker):

```bash
export NEXUS_BASE_URL=http://127.0.0.1:8080
export NEXUS_KEYCLOAK_ISSUER=http://127.0.0.1:8080/realms/nexus
export NEXUS_G17_SOD_LIVE_CONTEXT_ID=...   # issued X-Nexus-Context-Id; env / Secret only
./scripts/g17-separation-honesty.sh
```

See [engineering/g17-separation-honesty-EVIDENCE.md](./engineering/g17-separation-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/g17-separation-honesty/`. Walk JSON: [g17-separation-honesty/walk.json](./g17-separation-honesty/walk.json).

## Evidence

| Item | Value |
|---|---|
| JDK / Boot | OpenJDK **21** / Spring Boot **4.1.1** |
| Keycloak / product JAR | probe only. This VM: **not listening** |
| `#96` / `#99` / `#110` / `#118` / `#129` / `#133` / `#154` / `#158` / `#160` / `#172` | **not redone** |
| `AuditPort` | **untouched** |
| Docker | no |

| Check | Result |
|---|---|
| #99 catalog + exclusive pairs | always-green inventory |
| `tenant_member` is not a platform duty | always-green |
| P1 `ALL_DEMO` rewired onto P2 duties | **OPEN** (still bypasses) |
| Product duty-assign UI | **OPEN** (absent) |
| Live admin / auditor / operator HTTP | honest `SKIP` unless JAR + IdP + walks observed |
| Fake `PASS` / 等保 / 密评 / G17-close / SoD-close fixtures | rejected |
| Product G17 / SoD / 等保 / 密评 | **NOT_CLAIMED** |

## What this does not claim

| Leftover | Why |
|---|---|
| Full G17 / 等保 / 密评 | Explicitly `g17_close=NOT_CLAIMED`, `dengbao_claimed=false`, `mipin_claimed=false`. |
| Product SoD close | Explicitly `g17_sod_close=NOT_CLAIMED`. Live HTTP boundaries stay SKIP here. |
| #99 catalog / assign / audit-admin ITs | Sibling leftover. `redo_99=false`. |
| P1 tenant APIs on P2 duty roles | `ALL_DEMO` still grants every duty. |
| Product UI to assign the three duties | Still absent. |
| MFA / TOTP / Tongsuo / recovery / Vue otp siblings | Not redone. Rows kept. |
| G18 reopen / AuditPort / P0/M0 redo | Closed / untouched / out of scope. |
