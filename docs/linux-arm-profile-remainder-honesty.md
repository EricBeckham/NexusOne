# Linux / ARM Profile leftover — platform-matrix remainder honesty beyond #193/#157

S0+S1 remainder **after** the #193 leftover honesty leftover ([linux-arm-profile-leftover-honesty.md](./linux-arm-profile-leftover-honesty.md)), the #157 honesty leftover ([linux-arm-profile-honesty.md](./linux-arm-profile-honesty.md)), the #127 lifecycle leftover, and the #105 collect package. Rebased onto `origin/main` `3081fa7` (Merge #243 G10 file/object reconcile leftover honesty after #241 SecretPort rotate/getenv leftover honesty / #235 G5 PublishHook/DDL leftover honesty / #233 G17 MFA/OTP leftover honesty / #238 G1 FieldPolicy mask leftover honesty / #239 G7 expense/travel query leftover honesty / #237 G9 Redis-down / invalidate leftover honesty / #232 G2 H5 / form.write leftover honesty / #212 G7 withdraw browser-evidence honesty / #230 G4 hot-purge / P1 staging leftover honesty / #231 S0+S1 claim-list tip / #229 G9 live multi-JVM / replica remainder leftover honesty / #226 G3 Outbox consume-idempotent leftover honesty / #228 G10 leftover honesty remainder / #221 G2 report/physical-dump leftover honesty). **Product target Linux/ARM Profile is NOT accepted.** This leftover does **not** redo #105 collect/verify, #127 lifecycle, #157 honesty, or #193 leftover honesty product code. It does **not** rewrite P0-10 / M0-01～M0-12. It does **not** change `AuditPort.append` / `appendNewTransaction` same-TX rules. It does **not** boot a product JAR. **G18 stays closed.** `profile_acceptance=NOT_CLAIMED`.

Keep **ALL** sibling leftover rows in [docs/README.md](./README.md), including [linux-arm-profile-honesty](./linux-arm-profile-honesty.md) (#157), [linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md) (#193), [g10-file-object-reconcile-remainder-honesty](./g10-file-object-reconcile-remainder-honesty.md) (#243), [s0-secret-rotate-getenv-leftover-honesty](./s0-secret-rotate-getenv-leftover-honesty.md) (#241), [g5-publishhook-ddl-remainder-honesty](./g5-publishhook-ddl-remainder-honesty.md) (#235), [g17-mfa-recovery-vue-otp-remainder-honesty](./g17-mfa-recovery-vue-otp-remainder-honesty.md) (#233), [g1-fieldpolicy-mask-second-auth-remainder-honesty](./g1-fieldpolicy-mask-second-auth-remainder-honesty.md) (#238), [g7-query-expense-travel-honesty-remainder](./g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty](./g9-redis-down-invalidate-remainder-honesty.md) (#237), [g2-h5-every-page-form-write-remainder-honesty](./g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty](./g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty](./g4-hot-purge-staging-leftover-honesty.md) (#230), [g9-live-multi-jvm-replica-remainder-honesty](./g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty](./g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder](./g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty](./g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty](./g6-recovery-port-dry-run-remainder-honesty.md) (#225), [s0-secret-rotate-getenv-remainder-honesty](./s0-secret-rotate-getenv-remainder-honesty.md) (#223), [g4-pkce-spa-verifier-remainder-honesty](./g4-pkce-spa-verifier-remainder-honesty.md) (#222), [g17-separation-leftover-honesty](./g17-separation-leftover-honesty.md) (#216), [g15-wx-dt-native-bridge-remainder](./g15-wx-dt-native-bridge-remainder.md) (#220), [g1-queryast-export-honesty](./g1-queryast-export-honesty.md) (#215), [g17-gm-tls-nginx-handshake-honesty](./g17-gm-tls-nginx-handshake-honesty.md) (#210), [g5-publishhook-ddl-honesty](./g5-publishhook-ddl-honesty.md) (#217), [g9-sentinel-cluster-remainder-honesty](./g9-sentinel-cluster-remainder-honesty.md) (#209), and every other leftover row already listed. Keep this file.

#157 already locked `TARGET-ARM-01` as `not-tested` and `supply-chain-evidence.csv` as `not-collected`. #193 already is the leftover honesty gate beyond that: parent cells stay honest; native aarch64 install / upgrade / rollback / official offline / product SBOM is honest **SKIP** on this-host x86. The leftover here is the **remainder honesty gate beyond those two**: parent #157/#193 stub/inventory harness fixtures stay always-green **PASS**. The whole P0-10 platform matrix (`LOCAL-WIN-X64-POC` / `TARGET-X86-01` / `TARGET-ARM-01`) is inventoried. A live ARM/Linux (or other non-x86) **product-JAR** walk is **PASS** only on a native non-x86 host **and** when that walk was observed. This agent VM is `uname -m=x86_64`. An x86 leftover note, QEMU user-mode, or Docker “ARM” is **not** a live ARM/Linux product-JAR `PASS`. Missing native ARM / non-x86 hardware is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #157 / #193 stub/inventory harness | Already on `main`. Parent `--verify-report` honest SKIP fixtures stay **PASS**. `leftover_157_harness=PASS`. `leftover_193_harness=PASS`. Do **not** redo. |
| Platform-matrix inventory | Read `poc/p0-10/deployment-matrix.csv`. All three rows stay inventoried. `platform_matrix_inventory=PASS`. `TARGET-ARM-01` and `TARGET-X86-01` stay `not-tested`. `LOCAL-WIN-X64-POC` install / upgrade / rollback / offline stay `not-tested`. |
| Supply-chain csv | Every component stays `not-collected`. Do not flip the board. |
| Live ARM/Linux product-JAR walk | `live_arm_linux_product_jar=SKIP` unless this host is native aarch64 **and** that walk was observed. This leftover does **not** boot a product JAR. |
| Live non-x86 product-JAR walk | `live_non_x86_product_jar=SKIP` unless `uname -m` is non-x86 **and** that walk was observed. |
| This-host x86 leftover | Quote committed #127 / #157 / #193 x86 artifacts. `x86_note_extrapolated=false`. `x86_agent_vm_is_not_target_profile=true`. `x86_agent_vm_is_not_arm_linux_jar=true`. QEMU / Docker is not ARM. |
| Sibling leftover rows | `docs/README.md` keeps #157 / #193 and this leftover, plus #243 / #241 / #235 / #233 / #238 / #239 / #237 / #232 / #212 / #230 / #229 / #226 / #228 / #221 / #225 / #223 / #222 and **ALL** other leftover rows. |
| Honesty gate | `--verify-report` rejects fake live ARM/Linux product-JAR PASS, fake non-x86 PASS, QEMU/Docker-as-ARM, x86-as-ARM, `profile_acceptance=ACCEPTED` / `CLOSED`, and #105/#127/#157/#193 redo flags. |

No Docker. No QEMU user-mode “ARM”. No `lifecycle.sh` / `collect-and-verify.sh` / `linux-arm-profile-honesty.sh` / `linux-arm-profile-leftover-honesty.sh` product re-run. Parent `--verify-report` on already-committed honesty fixtures is allowed as harness evidence.

## What this does not claim

| Leftover | Why |
|---|---|
| Product Linux/ARM Profile close | Explicitly `profile_acceptance=NOT_CLAIMED`. Honesty gate rejects `ACCEPTED` / `CLOSED`. |
| Live ARM/Linux product-JAR walk | This VM is `uname -m=x86_64`. Walk not observed. Honest SKIP. |
| Live non-x86 product-JAR walk | No native non-x86 host. Honest SKIP. |
| Changing `TARGET-ARM-01` / `TARGET-X86-01` from `not-tested` | No approved Linux + CPU + JDK 21 + PostgreSQL 15.x delivery row. |
| Changing `supply-chain-evidence.csv` from `not-collected` | No product SBOM / license / CVE / official media. |
| Treating this x86 agent VM as `TARGET-X86-01` | Agent VM leftover is not the approved Linux x86 target. |
| QEMU / Docker as ARM | Forbidden. `qemu_is_not_arm=true`, `docker_is_not_arm=true`. |
| Redo of #105 / #127 / #157 / #193 | Those packages stay as committed. This leftover only reads them. |
| P0-10 / M0-01 redo | Inventory only. |
| G18 reopen / `AuditPort` | Closed / untouched. |

## How to run

```bash
./scripts/linux-arm-profile-remainder-honesty.sh
./scripts/linux-arm-profile-remainder-honesty.sh --verify-report <out>/report.env
./scripts/linux-arm-profile-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/linux-arm-profile-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-remainder-honesty/fixtures/fake-live-arm-jar-pass.env fail
./scripts/linux-arm-profile-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-remainder-honesty/fixtures/fake-live-non-x86-pass.env fail
./scripts/linux-arm-profile-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-remainder-honesty/fixtures/fake-qemu-as-arm.env fail
./scripts/linux-arm-profile-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-remainder-honesty/fixtures/fake-x86-as-arm.env fail
./scripts/linux-arm-profile-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-remainder-honesty/fixtures/fake-profile-close.env fail
./scripts/linux-arm-profile-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-remainder-honesty/fixtures/fake-redone.env fail
```

#105 / #127 / #157 / #193 stay the sibling reproduction commands. This leftover does not invoke them as a product redo:

```bash
# do not run as this leftover
# ./scripts/linux-arm-profile/collect-and-verify.sh
# ./scripts/linux-arm-profile/lifecycle.sh
# ./scripts/linux-arm-profile-honesty.sh
# ./scripts/linux-arm-profile-leftover-honesty.sh
```

Java **21** / Boot **4.1.1**. No Docker. No live PostgreSQL required. No product JAR boot.

See [engineering/linux-arm-profile-remainder-honesty-EVIDENCE.md](./engineering/linux-arm-profile-remainder-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/linux-arm-profile-remainder-honesty/`.

## Honest leftovers (not claimed)

- Freeze an approved Linux + **aarch64** (or other non-x86) CPU + JDK 21 + PostgreSQL 15.x delivery row
- Native ARM/Linux or non-x86 **product-JAR** walk on **that** row
- Native aarch64 install / upgrade / rollback / official offline media
- Product SBOM + license + CVE + official digest recorded in `supply-chain-evidence.csv`
- Changing `TARGET-ARM-01` / `TARGET-X86-01` from `not-tested`
- Product Profile acceptance
- Redo of #105 / #127 / #157 / #193
