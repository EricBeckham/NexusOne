# Linux / ARM Profile collect + verify

S0+S1 remainder for the **target Linux/ARM Profile** leftover (团队领取任务清单 / M0-01 未宣称项). This is an evidence collector, **not** a product Profile close.

`poc/p0-10/collect-profile.sh` stays a read-only version probe. This script adds Boot 4.1.1 coordinates, PostgreSQL 15 client/server probes, a minimal module build/test, and an honesty gate so an x86_64 host cannot record a fake aarch64 PASS.

## What it records

| Item | Source |
|---|---|
| OS / `uname -m` / CPU | host |
| JDK 21 + `os.arch` | `java -XshowSettings:properties` |
| Boot **4.1.1** coordinates | root `pom.xml` parent `org.springframework.boot:spring-boot-starter-parent:4.1.1` |
| PostgreSQL 15 client | `psql` / `/usr/lib/postgresql/15/bin/psql` |
| PostgreSQL 15 live server | optional `SHOW server_version` via `NEXUS_DB_*` or `127.0.0.1:5432` (3s timeout) |
| PostgreSQL 15 test-time server | Zonky `embedded-postgres-binaries-bom:15.18.0` in `isolation-kernel` |
| Minimal module set | `isolation-kernel`, `nexus-common`, `nexus-crypto-core` |

Default modules stay small on purpose: isolation kernel (Java 21 + Zonky PG 15.18), shared contracts, CryptoPort. This is **not** a P0/M0 foundation redo and does **not** start `nexus-bootstrap`.

## Honesty rules

- Product `profile_acceptance` is always `NOT_CLAIMED`.
- If `uname -m` is `x86_64` / `amd64`, `aarch64_build` and `aarch64_test` **must** be `SKIP` with a reason. No Docker / QEMU user-mode stand-in.
- If the host is `aarch64` / `arm64`, `x86_64_*` is `SKIP` for the same reason.
- G18 stays `closed_unchanged`. `audit_port_changed=false`.
- Passwords are never written to the report.

## Run

No Docker. JDK 21 and Maven on PATH. PostgreSQL 15 client is optional (SKIP with reason if absent). Live server is optional.

```bash
./scripts/linux-arm-profile/collect-and-verify.sh
./scripts/linux-arm-profile/collect-and-verify.sh --require-build --out .local/linux-arm-profile/manual
./scripts/linux-arm-profile/collect-and-verify.sh --verify-report .local/linux-arm-profile/manual/report.env
```

Output (gitignored under `.local/`):

- `report.env` — machine-readable keys
- `matrix.md` — PASS/SKIP table
- `collect-profile.txt` — wrapped P0-10 inventory
- `build-<module>.log` — Maven logs
- `summary.txt`

A sanitized copy of one agent-VM run is committed at `docs/engineering/linux-arm-profile/agent-vm-x86_64.txt`.

CI: `.github/workflows/linux-arm-profile.yml` (x86_64 required; `ubuntu-24.04-arm` is a cross-check and must not be treated as Profile acceptance).

Evidence: [docs/engineering/linux-arm-profile-EVIDENCE.md](../../docs/engineering/linux-arm-profile-EVIDENCE.md).

## Install / upgrade / rollback / offline / SBOM leftover

After #105. Same honesty rules. **Not** a Profile close.

```bash
./scripts/linux-arm-profile/lifecycle.sh
./scripts/linux-arm-profile/lifecycle.sh --out .local/linux-arm-profile-lifecycle/manual
./scripts/linux-arm-profile/lifecycle.sh --verify-report .local/linux-arm-profile-lifecycle/manual/report.env
```

On `uname -m=x86_64`, every `aarch64_*` cell is `SKIP` with a reason. Schema cells need PostgreSQL 15 `initdb`/`pg_ctl`/`psql`/`pg_dump`/`pg_restore` on a dedicated port (default `55442`, never `5432`). Missing binaries are SKIP, not a fake PASS. SBOM is CycloneDX when Maven can produce a BOM, otherwise `dependency:tree`. `poc/p0-10/supply-chain-evidence.csv` stays `not-collected`.

A sanitized agent-VM copy is `docs/engineering/linux-arm-profile/agent-vm-x86_64-lifecycle.txt`.

Evidence: [docs/engineering/linux-arm-profile-lifecycle-EVIDENCE.md](../../docs/engineering/linux-arm-profile-lifecycle-EVIDENCE.md).

## TARGET-ARM-01 / supply-chain-evidence honesty leftover

After #105 / #127. Does **not** re-run the scripts above. `TARGET-ARM-01` stays `not-tested`. `poc/p0-10/supply-chain-evidence.csv` stays `not-collected`. A this-host x86 leftover note is **not** an aarch64 PASS and is **not** Profile close.

```bash
./scripts/linux-arm-profile-honesty.sh
```

Evidence: [docs/linux-arm-profile-honesty.md](../../docs/linux-arm-profile-honesty.md).

## TARGET-ARM-01 / supply-chain leftover honesty beyond #157

After #157. Does **not** re-run collect, lifecycle, or the #157 honesty script. `TARGET-ARM-01` stays `not-tested`. `poc/p0-10/supply-chain-evidence.csv` stays `not-collected`. Native aarch64 walks are honest SKIP unless observed on aarch64. A this-host x86 leftover note is **not** an aarch64 PASS and is **not** Profile close.

```bash
./scripts/linux-arm-profile-leftover-honesty.sh
```

Evidence: [docs/linux-arm-profile-leftover-honesty.md](../../docs/linux-arm-profile-leftover-honesty.md).
