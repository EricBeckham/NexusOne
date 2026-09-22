# Linux / ARM Profile leftover: TARGET-ARM-01 / supply-chain-evidence honesty

S0+S1 remainder **after** the #105 collect/verify package ([linux-arm-profile-EVIDENCE.md](./engineering/linux-arm-profile-EVIDENCE.md)) and the #127 this-host install/upgrade/rollback/offline/SBOM leftover ([linux-arm-profile-lifecycle-EVIDENCE.md](./engineering/linux-arm-profile-lifecycle-EVIDENCE.md)). **Product target Linux/ARM Profile is NOT accepted.** This leftover does **not** redo #105 or #127. It does **not** rewrite P0-10 / M0-01～M0-12. It does **not** change `AuditPort`. **G18 stays closed.**

It is an **honesty inventory** of two P0-10 evidence files that must stay open:

| Artifact | Required status | Why |
|---|---|---|
| `TARGET-ARM-01` in [`poc/p0-10/deployment-matrix.csv`](../poc/p0-10/deployment-matrix.csv) | every result cell **`not-tested`** | No approved Linux + native **aarch64** CPU + JDK 21 + PostgreSQL 15.x delivery row was installed, upgraded, rolled back, or packaged offline. |
| [`poc/p0-10/supply-chain-evidence.csv`](../poc/p0-10/supply-chain-evidence.csv) | every component **`not-collected`** | No product SBOM + license + CVE + official digest + offline media board. A Maven CycloneDX / `dependency:tree` leftover (#127) is **not** that board. |

A this-host **x86_64** note from the committed #127 artifact is allowed. It must **not** be copied into an aarch64 `PASS`, and it must **not** flip `TARGET-ARM-01` or the supply-chain CSV.

## What this leftover covers

| Path | Behavior |
|---|---|
| CSV honesty | Read the two P0-10 CSVs. `TARGET-ARM-01` install / upgrade / rollback / offline / crypto cells stay `not-tested`. Every supply-chain row stays `not-collected`. |
| Committed #105 / #127 artifacts | Re-read only. `aarch64_*` stays `SKIP`. `profile_acceptance` stays `NOT_CLAIMED`. Scripts are **not** re-run. |
| Local x86 lifecycle note | Quote the committed #127 x86 leftover (`docs/engineering/linux-arm-profile/agent-vm-x86_64-lifecycle.txt`). That note is this-host only. |
| Honesty gate | `--verify-report` rejects `TARGET-ARM-01=pass`, a collected supply-chain row, `profile_acceptance=ACCEPTED`, or `aarch64_pass_claimed=true`. |

No Docker. No QEMU user-mode “ARM”. No `lifecycle.sh` / `collect-and-verify.sh` re-run.

## What this does not claim

| Leftover | Why |
|---|---|
| Product Linux/ARM Profile close | Explicitly `profile_acceptance=NOT_CLAIMED`. |
| Native aarch64 / ARM64 pass | This VM is `uname -m=x86_64` unless a future host says otherwise. x86 leftover is not ARM. |
| Changing `TARGET-ARM-01` from `not-tested` | No approved aarch64 delivery row. |
| Changing `supply-chain-evidence.csv` from `not-collected` | No product SBOM / license / CVE / official media. |
| Re-running #105 collect or #127 lifecycle | Those packages stay as committed. This leftover only reads them. |
| P0-10 / M0-01 redo | Inventory only. |
| G18 reopen / `AuditPort` | Closed / untouched. |

## How to run

```bash
./scripts/linux-arm-profile-honesty.sh
./scripts/linux-arm-profile-honesty.sh --verify-report <out>/report.env
./scripts/linux-arm-profile-honesty.sh --verify-report \
  scripts/linux-arm-profile-honesty/fixtures/honest.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/linux-arm-profile-honesty.sh --verify-report \
  scripts/linux-arm-profile-honesty/fixtures/fake-arm-tested.env
./scripts/linux-arm-profile-honesty.sh --verify-report \
  scripts/linux-arm-profile-honesty/fixtures/fake-supply-collected.env
./scripts/linux-arm-profile-honesty.sh --verify-report \
  scripts/linux-arm-profile-honesty/fixtures/fake-profile-close.env
./scripts/linux-arm-profile-honesty.sh --verify-report \
  scripts/linux-arm-profile-honesty/fixtures/fake-aarch64-pass.env
```

#105 / #127 stay the reproduction commands for collect and lifecycle. This leftover does not invoke them:

```bash
# do not run for this leftover
# ./scripts/linux-arm-profile/collect-and-verify.sh
# ./scripts/linux-arm-profile/lifecycle.sh
```

Java **21** / Boot **4.1.1**. No Docker. No live PostgreSQL required.

Sibling leftover honesty beyond this #157 contract (do **not** redo this leftover): [linux-arm-profile-leftover-honesty.md](./linux-arm-profile-leftover-honesty.md). Keep both leftover rows in [docs/README.md](./README.md).

See [engineering/linux-arm-profile-honesty-EVIDENCE.md](./engineering/linux-arm-profile-honesty-EVIDENCE.md).

## Honest leftovers (not claimed)

- Freeze an approved Linux + **aarch64** CPU + JDK 21 + PostgreSQL 15.x delivery row
- Native aarch64 install / upgrade / rollback / official offline media on **that** row
- Product SBOM + license + CVE + official digest recorded in `supply-chain-evidence.csv`
- Changing `TARGET-ARM-01` from `not-tested`
- Product Profile acceptance
