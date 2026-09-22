# Linux / ARM Profile leftover: install / upgrade / rollback / offline / SBOM

Recorded on this agent VM against `origin/main` `3be6d11` (Merge #126). Operator: cloud agent. Live output: `.local/linux-arm-profile-lifecycle/` (gitignored). Committed copy: [`docs/engineering/linux-arm-profile/agent-vm-x86_64-lifecycle.txt`](./linux-arm-profile/agent-vm-x86_64-lifecycle.txt) (filled after the first `--out` run on this branch).

**Product target Linux/ARM Profile is NOT accepted.** This is the leftover called out after the #105 collect/verify package in [linux-arm-profile-EVIDENCE.md](./linux-arm-profile-EVIDENCE.md), [团队领取任务清单](../planning/团队领取任务清单_V1.1.md), and [M0-01](./m0-01-engineering-foundation.md). It adds a reproducible install / upgrade / rollback / offline-bundle / SBOM drill that this host can actually run. It is **not** P0-10 / M0-01 redone, **not** GaussDB, **not** a G18 reopen, **does not** change `AuditPort`, and is orthogonal to #114 / #115 / #119 / #121 / #122 / #123 / #126.

G18 stays closed (`docs/g18-business-path.md`). `TARGET-ARM-01` in `poc/p0-10/deployment-matrix.csv` stays `not-tested`. `poc/p0-10/supply-chain-evidence.csv` stays `not-collected`. A this-host x86 leftover note is **not** an aarch64 PASS. The honesty leftover that locks those two cells (without re-running this lifecycle package) is [linux-arm-profile-honesty.md](../linux-arm-profile-honesty.md).

## Verdict

| Claim | Result |
|---|---|
| Reproducible lifecycle script | **PASS** — [`scripts/linux-arm-profile/lifecycle.sh`](../../scripts/linux-arm-profile/lifecycle.sh) |
| Honest PASS/SKIP matrix | **PASS** — x86_64 host cannot emit aarch64 `PASS` |
| Agent VM native x86_64 leftover (this host) | filled after the first run; see the committed artifact |
| Native aarch64 / ARM64 hardware on the agent VM | **SKIP** — `uname -m=x86_64`; no aarch64 CPU; Docker/QEMU user-mode not used (Docker forbidden) |
| Product Profile acceptance (approved Linux + ARM CPU + JDK 21 + PG 15 install/upgrade/rollback/offline media/SBOM/CVE) | **NOT_CLAIMED** |
| CI cross-check | [`.github/workflows/linux-arm-profile.yml`](../../.github/workflows/linux-arm-profile.yml) — lifecycle honesty fixtures + optional x86/ARM jobs; still not Profile close |

## What this leftover actually does

| Step | This-host meaning | Not claimed |
|---|---|---|
| Install | Package the #105 minimal module set (`isolation-kernel`, `nexus-common`, `nexus-crypto-core`) and record SHA-256. Apply bootstrap Flyway **V001** on a **dedicated** PostgreSQL 15 cluster (`127.0.0.1:55442`, never `:5432`). | systemd/OS package install on an approved Profile row; full `nexus-bootstrap` start; Keycloak / Flowable / MinIO / Redis install |
| Upgrade | Apply bootstrap **V002 + V003** on that same dedicated database and check `platform.runtime_meta` / `table_registry`. | Flyway 13.7.0 full-app migrate; PostgreSQL major-version `pg_upgrade`; Keycloak / Flowable / MinIO upgrade |
| Rollback | `pg_dump -Fc` after V001, then drop/create + `pg_restore` and check V003 objects are gone while the four logical schemas remain. | Product RPO/RTO; old-program-reads-new-schema; G6 `RecoveryPort` |
| Offline bundle | Copy built JARs + runtime deps, then `mvn -o` compile of the same minimal set using this host's local Maven cache. | Official offline media; signed OS/JDK/PG packages; air-gapped install of the whole product |
| SBOM | CycloneDX Maven plugin when it can produce a BOM; otherwise `mvn dependency:tree`. | Product SBOM + license + CVE disposition; changing `supply-chain-evidence.csv` from `not-collected` |

## Script and artifact path

```bash
./scripts/linux-arm-profile/lifecycle.sh
./scripts/linux-arm-profile/lifecycle.sh --out .local/linux-arm-profile-lifecycle/manual
./scripts/linux-arm-profile/lifecycle.sh --verify-report <out>/report.env
```

| Path | Role |
|---|---|
| `.local/linux-arm-profile-lifecycle/<utc>/` | Live run (gitignored) |
| [`docs/engineering/linux-arm-profile/agent-vm-x86_64-lifecycle.txt`](./linux-arm-profile/agent-vm-x86_64-lifecycle.txt) | Sanitized committed copy of the agent-VM `report.env` + matrix |
| `<out>/install/v1/` | Packaged JARs + `SHA256SUMS` |
| `<out>/schema/` | V001 dump + install/upgrade/rollback checks |
| `<out>/offline-bundle/` | JAR + runtime-dep snapshot (Maven cache, not official media) |
| `<out>/sbom/` | CycloneDX JSON and/or `dependency-tree-*.txt` |

`--verify-report` rejects a fake `aarch64_install=PASS` (or upgrade/rollback/offline/SBOM PASS) on `host_uname_m=x86_64` (fixture `scripts/linux-arm-profile/fixtures/x86-fake-arm-lifecycle.env`).

## Agent VM environment (this run)

Filled from the collector after the first lifecycle run. Passwords were never set and are not in the artifact.

| Item | Value |
|---|---|
| Host | see committed artifact |
| CPU | **not** ARM (this VM is x86_64 unless the artifact says otherwise) |
| JDK | product baseline **21** |
| Boot coordinates | `org.springframework.boot:spring-boot-starter-parent:4.1.1` |
| Dedicated PG | `127.0.0.1:55442` when server binaries exist; otherwise schema cells are SKIP |
| Docker | not used |
| G18 / AuditPort | unchanged |

## PASS / SKIP matrix

Values are copied from the committed artifact after `lifecycle.sh`. Do not treat an x86_64 `PASS` as ARM. Do not treat a Maven SBOM/tree as product supply-chain close.

| Check | Result | Notes |
|---|---|---|
| OS / arch | this host only | see artifact |
| JAR install | this host only | minimal module set; not `nexus-bootstrap` |
| Schema install / upgrade / rollback | this host only, or **SKIP** if PG 15 `initdb` is missing | dedicated cluster; not `:5432` |
| Offline bundle + `mvn -o` | this host only, or **SKIP** if Maven is missing | local `~/.m2`, not official media |
| SBOM | this host only, or **SKIP** if Maven cannot produce a graph | Maven leftover, not CVE/license board |
| aarch64 install/upgrade/rollback/offline/SBOM | **SKIP** on x86_64 | no aarch64 CPU; Docker forbidden |
| Product Profile acceptance | **NOT_CLAIMED** | |

## What would still be required to close the Profile

All of the following remain open. This leftover does not perform them.

1. Freeze a delivery row: approved Linux distribution + **aarch64** CPU model + JDK 21 build + PostgreSQL 15.x + install method + package digest.
2. Run install, upgrade, rollback, and **official** offline media on **that** row (and a separate x86_64 row). Do not copy this VM's results across architectures.
3. Record product SBOM / license / CVE / offline media. `poc/p0-10/supply-chain-evidence.csv` stays `not-collected`.
4. Native aarch64 `lifecycle.sh` on real ARM hardware (or a provisioned ARM runner whose `uname -m` is `aarch64`). GitHub `ubuntu-24.04-arm` is a CI cross-check only.

## Explicitly out of scope

- Docker / Compose / QEMU user-mode “ARM” results
- Rewriting P0 leaves or M0-01～M0-12 product modules
- G18 请假 path (already closed)
- `AuditPort` same-TX append semantics
- GaussDB, 信创 OS/CPU matrix, Keycloak/Flowable/MinIO upgrade-rollback
- Capacity thresholds, 1000-tenant freeze, official offline media
- #114 APP WebView, #115 DataScope AST, #119 QueryCompiler bypass CI, #121 G9 failover remainder, #122 SurfaceGuard bypass CI, #123 G4 90-day hot purge, #126 G15 H5 browser PKCE

## CI cross-check

`.github/workflows/linux-arm-profile.yml`:

- `honesty-fixtures` — fake ARM lifecycle PASS on x86 must fail
- `lifecycle-x86_64` — `ubuntu-latest` + JDK 21; schema cells SKIP if PG 15 server binaries are absent
- `lifecycle-aarch64` — `ubuntu-24.04-arm` with `continue-on-error: true`. If the runner is missing, that is SKIP, not an invented PASS. A green ARM job still does **not** close the product Profile.
