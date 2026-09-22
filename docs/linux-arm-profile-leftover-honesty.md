# Linux / ARM Profile leftover: TARGET-ARM-01 / supply-chain csv honesty beyond #157

S0+S1 remainder **after** the #157 honesty leftover ([linux-arm-profile-honesty.md](./linux-arm-profile-honesty.md)). Rebased onto `origin/main` `25c3454` (Merge #190 G4 P1 staging audit-events leftover honesty; includes #194 claim-list tip / #186 G6 RecoveryPort dry-run leftover honesty / #185 live Redis/MinIO isolation leftover honesty / #189 claim-list tip / #187 G17 SoD leftover honesty / #183 G4 hot-purge leftover honesty / #184 G1 FieldPolicy leftover honesty). **Product target Linux/ARM Profile is NOT accepted.** This leftover does **not** redo #105 collect/verify, #127 lifecycle, or #157 honesty product code. It does **not** rewrite P0-10 / M0-01～M0-12. It does **not** change `AuditPort`. **G18 stays closed.**

Keep **all** sibling leftover rows in [docs/README.md](./README.md): [linux-arm-profile-honesty.md](./linux-arm-profile-honesty.md) (#157) and this file. Also keep newly landed sibling leftovers [g4-p1-staging-audit-events-honesty.md](./g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](./g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](./s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](./g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](./g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](./g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty.md](./g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](./g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](./g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](./s0-secret-getenv-snapshot-honesty.md) (#180), and [g5-browser-failed-publish.md](./g5-browser-failed-publish.md) (#171). This file is the PASS/SKIP honesty leftover beside #157.

#157 already locked `TARGET-ARM-01` as `not-tested` and `supply-chain-evidence.csv` as `not-collected`. The leftover here is the **honesty gate** beyond that: those two cells stay honest; native aarch64 install / upgrade / rollback / official offline / product SBOM is **PASS** only on a native aarch64 host **and** when that walk was observed. A this-host **x86_64** leftover note from #127 / #157 is **not** an aarch64 `PASS`. Missing native ARM hardware is honest **SKIP**. It never fabricates `PASS`.

## What this leftover covers

| Path | Behavior |
|---|---|
| #157 CSV honesty | Already on `main`. `TARGET-ARM-01` install / upgrade / rollback / offline / crypto stay `not-tested`. Every supply-chain row stays `not-collected`. `leftover_157_cells_honest=PASS`. Do not redo. |
| Native aarch64 walk | `native_aarch64_*=SKIP` unless `uname -m` is aarch64 **and** `NEXUS_ARM_NATIVE_WALK_OBSERVED=true`. This leftover still does **not** flip `TARGET-ARM-01` to tested. |
| Local x86 leftover note | Quote committed #127 / #157 x86 artifacts. `x86_note_extrapolated=false`. Copying those cells onto aarch64 PASS is rejected. |
| Sibling leftover rows | `docs/README.md` keeps #157 and this leftover, plus #183 / #185 / #186 / #187 / #190 and the other leftover rows listed above. |
| Honesty gate | `--verify-report` rejects `TARGET-ARM-01=pass`, a collected supply-chain row, `profile_acceptance=ACCEPTED`, `aarch64_pass_claimed=true`, `x86_note_extrapolated=true`, and #105/#127/#157 redo flags. |

No Docker. No QEMU user-mode “ARM”. No `lifecycle.sh` / `collect-and-verify.sh` / `linux-arm-profile-honesty.sh` re-run.

## What this does not claim

| Leftover | Why |
|---|---|
| Product Linux/ARM Profile close | Explicitly `profile_acceptance=NOT_CLAIMED`. Honesty gate rejects `ACCEPTED`. |
| Native aarch64 / ARM64 pass | This VM is `uname -m=x86_64` unless a future host says otherwise. x86 leftover is not ARM. |
| Changing `TARGET-ARM-01` from `not-tested` | No approved aarch64 delivery row. |
| Changing `supply-chain-evidence.csv` from `not-collected` | No product SBOM / license / CVE / official media. |
| Re-running #105 collect / #127 lifecycle / #157 honesty | Those packages stay as committed. This leftover only reads them. |
| P0-10 / M0-01 redo | Inventory only. |
| G18 reopen / `AuditPort` | Closed / untouched. |

## How to run

```bash
./scripts/linux-arm-profile-leftover-honesty.sh
./scripts/linux-arm-profile-leftover-honesty.sh --verify-report <out>/report.env
./scripts/linux-arm-profile-leftover-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/linux-arm-profile-leftover-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-honesty/fixtures/fake-arm-tested.env
./scripts/linux-arm-profile-leftover-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-honesty/fixtures/fake-supply-collected.env
./scripts/linux-arm-profile-leftover-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-honesty/fixtures/fake-profile-close.env
./scripts/linux-arm-profile-leftover-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-honesty/fixtures/fake-aarch64-pass.env
```

#105 / #127 / #157 stay the sibling reproduction commands. This leftover does not invoke them:

```bash
# do not run for this leftover
# ./scripts/linux-arm-profile/collect-and-verify.sh
# ./scripts/linux-arm-profile/lifecycle.sh
# ./scripts/linux-arm-profile-honesty.sh
```

Java **21** / Boot **4.1.1**. No Docker. No live PostgreSQL required.

Sibling remainder honesty beyond this #193 contract (do **not** redo this leftover): [linux-arm-profile-remainder-honesty.md](./linux-arm-profile-remainder-honesty.md). Keep leftover rows in [docs/README.md](./README.md).

See [engineering/linux-arm-profile-leftover-honesty-EVIDENCE.md](./engineering/linux-arm-profile-leftover-honesty-EVIDENCE.md). Live `report.env` is gitignored under `.local/linux-arm-profile-leftover-honesty/`.

## Honest leftovers (not claimed)

- Freeze an approved Linux + **aarch64** CPU + JDK 21 + PostgreSQL 15.x delivery row
- Native aarch64 install / upgrade / rollback / official offline media on **that** row
- Product SBOM + license + CVE + official digest recorded in `supply-chain-evidence.csv`
- Changing `TARGET-ARM-01` from `not-tested`
- Product Profile acceptance
- Redo of #105 / #127 / #157
