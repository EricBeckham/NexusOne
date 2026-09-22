# Linux / ARM Profile evidence (S0+S1 remainder)

Recorded **2026-09-22T17:11:54Z** on this agent VM after rebase onto `origin/main` `1377a0a` (#107). Operator: cloud agent. Live output: `.local/linux-arm-profile/agent-vm-x86_64-rebased/` (gitignored). Committed copy: [`docs/engineering/linux-arm-profile/agent-vm-x86_64.txt`](./linux-arm-profile/agent-vm-x86_64.txt).

**Product target Linux/ARM Profile is NOT accepted.** This package is the leftover called out in [团队领取任务清单](../planning/团队领取任务清单_V1.1.md) and [M0-01](./m0-01-engineering-foundation.md): a reproducible collect/verify script, a PASS/SKIP matrix, and what this agent VM can actually run. It is **not** P0-10 / M0-01 redone, **not** GaussDB, **not** a G18 reopen, and **does not** change `AuditPort`. Install / upgrade / rollback / offline / SBOM leftover (still **not** Profile close) is [linux-arm-profile-lifecycle-EVIDENCE.md](./linux-arm-profile-lifecycle-EVIDENCE.md).

G18 stays closed (`docs/g18-business-path.md`). `TARGET-ARM-01` in `poc/p0-10/deployment-matrix.csv` stays `not-tested`. `poc/p0-10/supply-chain-evidence.csv` stays `not-collected`. The honesty leftover that locks those two cells (without re-running this collect package) is [linux-arm-profile-honesty.md](../linux-arm-profile-honesty.md).

## Verdict

| Claim | Result |
|---|---|
| Reproducible collect/verify script | **PASS** — [`scripts/linux-arm-profile/collect-and-verify.sh`](../../scripts/linux-arm-profile/collect-and-verify.sh) (wraps [`poc/p0-10/collect-profile.sh`](../../poc/p0-10/collect-profile.sh)) |
| Honest PASS/SKIP matrix | **PASS** — x86_64 host cannot emit aarch64 `PASS` |
| Agent VM native x86_64 inventory + minimal module build/test | **PASS** — isolation-kernel 42, nexus-common 14, nexus-crypto-core 18; 0 fail |
| Native aarch64 / ARM64 hardware on the agent VM | **SKIP** — `uname -m=x86_64`; no aarch64 CPU; Docker/QEMU user-mode not used (Docker forbidden) |
| Product Profile acceptance (approved Linux + ARM CPU + JDK 21 + PG 15 install/upgrade/rollback/offline) | **NOT_CLAIMED** |
| CI cross-check | [`.github/workflows/linux-arm-profile.yml`](../../.github/workflows/linux-arm-profile.yml) — x86_64 required; `ubuntu-24.04-arm` is optional and still not Profile close |

## Script and artifact path

```bash
./scripts/linux-arm-profile/collect-and-verify.sh --require-build
./scripts/linux-arm-profile/collect-and-verify.sh --verify-report <out>/report.env
```

| Path | Role |
|---|---|
| `.local/linux-arm-profile/<utc>/` | Live run (gitignored) |
| [`docs/engineering/linux-arm-profile/agent-vm-x86_64.txt`](./linux-arm-profile/agent-vm-x86_64.txt) | Sanitized committed copy of the agent-VM `report.env` + matrix |
| `<out>/collect-profile.txt` | Wrapped P0-10 inventory |
| `<out>/build-<module>.log` | Maven logs for the minimal set |

`--verify-report` rejects a fake `aarch64_build=PASS` on `host_uname_m=x86_64` (fixture `scripts/linux-arm-profile/fixtures/x86-fake-arm-pass.env`).

## Agent VM environment (this run)

Filled from the collector. Passwords were never set and are not in the artifact.

| Item | Value |
|---|---|
| Host | Ubuntu 24.04.4 LTS, `Linux cursor 6.12.94+`, `uname -m=x86_64` |
| CPU | Intel Xeon (KVM), `GenuineIntel` — **not** ARM |
| JDK | OpenJDK **21** (`21.0.10+7-Ubuntu-124.04`), `java.home=/usr/lib/jvm/java-21-openjdk-amd64`, `os.arch=amd64` |
| Boot coordinates | `org.springframework.boot:spring-boot-starter-parent:4.1.1` from root `pom.xml` |
| Flyway pin (recorded only) | `13.7.0` |
| Maven | 3.8.7 |
| PostgreSQL 15 client | **PASS** `psql (PostgreSQL) 15.19 (Ubuntu 15.19-1.pgdg24.04+2)` |
| PostgreSQL 15 live server | **SKIP** — `psql` could not connect (no listener / unset `NEXUS_DB_*`) |
| PostgreSQL 15 test-time | Zonky `io.zonky.test.postgres:embedded-postgres-binaries-bom:15.18.0` |
| Minimal modules | `isolation-kernel`, `nexus-common`, `nexus-crypto-core` |
| Docker | not used |
| G18 / AuditPort | unchanged |

## PASS / SKIP matrix

Values below are copied from the committed artifact after `collect-and-verify.sh --require-build`. Do not treat an x86_64 `PASS` as ARM.

| Check | Result | Notes |
|---|---|---|
| OS / arch | **PASS** x86_64 | Ubuntu 24.04.4 LTS, `uname -m=x86_64` |
| JDK 21 | **PASS** | OpenJDK `21.0.10`; `os.arch=amd64` |
| Boot 4.1.1 coordinates | **PASS** | `org.springframework.boot:spring-boot-starter-parent:4.1.1` |
| PostgreSQL 15 client | **PASS** | `15.19 (Ubuntu 15.19-1.pgdg24.04+2)` |
| PostgreSQL 15 live server | **SKIP** | no listener; `NEXUS_DB_*` unset |
| PostgreSQL 15 Zonky test BOM | recorded | `embedded-postgres-binaries-bom:15.18.0` — not a target-host install |
| x86_64 build/test (minimal set) | **PASS / PASS** | isolation-kernel **42**, nexus-common **14**, nexus-crypto-core **18** |
| aarch64 build/test | **SKIP / SKIP** | `uname -m=x86_64`; no aarch64 CPU; Docker/QEMU user-mode not used |
| Product Profile acceptance | **NOT_CLAIMED** | x86 PASS + ARM SKIP is not Profile close |

## What would still be required to close the Profile

All of the following remain open. This remainder does not perform them.

1. Freeze a delivery row: approved Linux distribution + **aarch64** CPU model + JDK 21 build + PostgreSQL 15.x + install method + package digest.
2. Run install, upgrade, rollback, and official offline media on **that** row (and a separate x86_64 row). Do not copy this VM's results across architectures. A this-host leftover drill lives in [linux-arm-profile-lifecycle-EVIDENCE.md](./linux-arm-profile-lifecycle-EVIDENCE.md) and still does not close the Profile.
3. Record product SBOM / license / CVE / offline media. `poc/p0-10/supply-chain-evidence.csv` stays `not-collected`.
4. Native aarch64 `collect-and-verify.sh --require-build` on real ARM hardware (or a provisioned ARM runner whose `uname -m` is `aarch64`). GitHub `ubuntu-24.04-arm` is a CI cross-check only.

## Explicitly out of scope

- Docker / Compose / QEMU user-mode “ARM” results
- Rewriting P0 leaves or M0-01～M0-12 product modules
- G18 请假 path (already closed)
- `AuditPort` same-TX append semantics
- GaussDB, 信创 OS/CPU matrix, Keycloak/Flowable/MinIO upgrade-rollback
- Capacity thresholds, 1000-tenant freeze, offline media

## CI cross-check

`.github/workflows/linux-arm-profile.yml`:

- `honesty-fixtures` — fake ARM PASS on x86 must fail
- `collect-x86_64` — `ubuntu-latest` + JDK 21 + PG 15 client + `--require-build`; asserts `aarch64_build=SKIP` and `profile_acceptance=NOT_CLAIMED`
- `collect-aarch64` — `ubuntu-24.04-arm` with `continue-on-error: true`. If the runner is missing, that is SKIP, not a invented PASS. A green ARM job still does **not** close the product Profile.
