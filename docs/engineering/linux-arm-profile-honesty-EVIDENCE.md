# Linux / ARM Profile leftover: TARGET-ARM-01 / supply-chain-evidence honesty — evidence

Recorded on this branch after rebase onto `origin/main` `8e5cdc3` (Merge #152). Operator: cloud agent. Live output: `.local/linux-arm-profile-honesty/` (gitignored). Committed copy: [`linux-arm-profile/agent-vm-x86_64-honesty.txt`](./linux-arm-profile/agent-vm-x86_64-honesty.txt) (filled after the first honesty run on this branch).

**Product target Linux/ARM Profile is NOT accepted.** This leftover does **not** redo #105 collect/verify or #127 lifecycle. It does **not** rewrite P0-10 / M0-01～M0-12. It does **not** change `AuditPort`. **G18 stays closed.**

See [docs/linux-arm-profile-honesty.md](../linux-arm-profile-honesty.md).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline; recorded from this host |
| Spring Boot parent | **4.1.1** | product BOM |
| Host | this agent VM | `uname -m` filled from the honesty script |
| Runtime | honesty inventory only | No Docker / Compose. `#105` / `#127` scripts not re-run |

## Commands

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

## Results (agent VM)

Filled after the honesty script on this VM. Do **not** treat an x86_64 leftover note as ARM.

| Check | Result | Notes |
|---|---|---|
| `TARGET-ARM-01` install / upgrade / rollback / offline / crypto | **not-tested** | `poc/p0-10/deployment-matrix.csv` |
| `supply-chain-evidence.csv` | **not-collected** | every component row |
| Committed #105 aarch64 build/test | **SKIP / SKIP** | `docs/engineering/linux-arm-profile/agent-vm-x86_64.txt`; script not re-run |
| Committed #127 aarch64 install/upgrade/rollback/offline/SBOM | **SKIP** | `docs/engineering/linux-arm-profile/agent-vm-x86_64-lifecycle.txt`; script not re-run |
| Local x86 lifecycle note | this-host only | quote of the committed #127 artifact; **not** an aarch64 PASS |
| Product Profile acceptance | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| #105 / #127 re-run | **false** | leftover reads committed artifacts only |
| G18 / AuditPort / P0-M0 | unchanged | not reopened / not rewritten |

## Local x86 lifecycle note (not aarch64)

This agent VM is not ARM. The committed #127 leftover artifact already records:

- `host_uname_m=x86_64`
- `aarch64_install=SKIP` / `aarch64_upgrade=SKIP` / `aarch64_rollback=SKIP` / `aarch64_offline=SKIP` / `aarch64_sbom=SKIP`
- `profile_acceptance=NOT_CLAIMED`

That is a **this-host x86 leftover note**. Copying those cells onto `TARGET-ARM-01` or calling them an aarch64 pass is rejected by the honesty gate.

## Honest leftovers

- Approved Linux + native aarch64 CPU + JDK 21 + PostgreSQL 15.x delivery row
- Native aarch64 install / upgrade / rollback / official offline media
- Product SBOM + license + CVE + official digest in `supply-chain-evidence.csv`
- Changing `TARGET-ARM-01` from `not-tested`
- Product Profile acceptance
