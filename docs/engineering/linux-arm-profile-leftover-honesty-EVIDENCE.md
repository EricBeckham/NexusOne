# Linux / ARM Profile leftover: TARGET-ARM-01 / supply-chain csv honesty beyond #157 — evidence

Recorded on this branch after rebase onto `origin/main` `25c3454` (Merge #190 G4 P1 staging audit-events leftover honesty; includes #194 claim-list tip / #186 G6 RecoveryPort dry-run leftover honesty / #185 live Redis/MinIO isolation leftover honesty / #189 claim-list tip / #187 G17 SoD leftover honesty / #183 G4 hot-purge leftover honesty / #184 G1 FieldPolicy leftover honesty). Operator: cloud agent. Live output: `.local/linux-arm-profile-leftover-honesty/` (gitignored). A sanitized copy is [`linux-arm-profile-leftover-honesty/agent-vm.env`](./linux-arm-profile-leftover-honesty/agent-vm.env) once recorded.

**Product target Linux/ARM Profile is NOT accepted.** This leftover does **not** redo #105 collect/verify, #127 lifecycle, or #157 honesty. It does **not** rewrite P0-10 / M0-01～M0-12. It does **not** change `AuditPort`. **G18 stays closed.**

See [docs/linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md). Keep **all** sibling leftover rows in [docs/README.md](../README.md): [linux-arm-profile-honesty.md](../linux-arm-profile-honesty.md) (#157) and this leftover, plus [g4-p1-staging-audit-events-honesty.md](../g4-p1-staging-audit-events-honesty.md) (#190), [g6-recovery-port-dry-run-honesty.md](../g6-recovery-port-dry-run-honesty.md) (#186), [s0s1-live-redis-minio-isolation-honesty.md](../s0s1-live-redis-minio-isolation-honesty.md) (#185), [g17-separation-honesty.md](../g17-separation-honesty.md) (#187), [g4-hot-purge-90d-honesty.md](../g4-hot-purge-90d-honesty.md) (#183), [g1-fieldpolicy-mask-second-auth-honesty.md](../g1-fieldpolicy-mask-second-auth-honesty.md) (#184), [g10-identity-drift-honesty.md](../g10-identity-drift-honesty.md) (#179), [g15-native-bridge-honesty.md](../g15-native-bridge-honesty.md) (#181), [g4-pkce-spa-verifier-honesty.md](../g4-pkce-spa-verifier-honesty.md) (#177), [s0-secret-getenv-snapshot-honesty.md](../s0-secret-getenv-snapshot-honesty.md) (#180), and [g5-browser-failed-publish.md](../g5-browser-failed-publish.md) (#171).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21.0.10** | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Host | this agent VM | `uname -m` filled from the leftover honesty script |
| Runtime | leftover honesty inventory only | No Docker / Compose. `#105` / `#127` / `#157` scripts not re-run |

## Commands

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

Do **not** treat `./scripts/linux-arm-profile-honesty.sh` as this leftover. That script is the #157 contract.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Do **not** treat an x86_64 leftover note as ARM.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake TARGET-ARM-01 pass / collected supply-chain / Profile close / aarch64 PASS rejected |
| leftover #157 cells still honest | **PASS** | `TARGET-ARM-01` still `not-tested`; supply-chain still `not-collected` |
| `TARGET-ARM-01` install / upgrade / rollback / offline / crypto | **not-tested** | `poc/p0-10/deployment-matrix.csv` |
| `supply-chain-evidence.csv` | **not-collected** | 11 component rows |
| Native aarch64 install/upgrade/rollback/offline/SBOM | **SKIP** | this host is `x86_64`; walk not observed |
| x86 leftover extrapolated to aarch64 | **false** | #127 / #157 this-host note only |
| Sibling leftover rows | kept | `linux-arm-profile-honesty` + this leftover + #183 / #185 / #186 / #187 / #190 siblings |
| #105/#127/#157 redo | **false** | sibling leftovers |
| Product Profile acceptance | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not rewritten |

## Honest leftovers

- Approved Linux + native aarch64 CPU + JDK 21 + PostgreSQL 15.x delivery row
- Native aarch64 install / upgrade / rollback / official offline media
- Product SBOM + license + CVE + official digest in `supply-chain-evidence.csv`
- Changing `TARGET-ARM-01` from `not-tested`
- Product Profile acceptance
- Redo of #105 / #127 / #157
