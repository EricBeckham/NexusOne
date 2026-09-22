# Linux / ARM Profile leftover: platform-matrix remainder honesty beyond #193/#157 — evidence

Recorded on this branch after rebase onto `origin/main` `c800294` (Merge #239 G7 expense/travel query leftover honesty after #237 G9 Redis-down / invalidate leftover honesty / #232 G2 H5 / form.write leftover honesty / #212 G7 withdraw browser-evidence honesty / #230 G4 hot-purge / P1 staging leftover honesty / #231 S0+S1 claim-list tip / #229 G9 live multi-JVM / replica remainder leftover honesty / #226 G3 Outbox consume-idempotent leftover honesty / #228 G10 leftover honesty remainder). Operator: cloud agent. Live output: `.local/linux-arm-profile-remainder-honesty/` (gitignored). A sanitized copy is [`linux-arm-profile-remainder-honesty/agent-vm.env`](./linux-arm-profile-remainder-honesty/agent-vm.env) once recorded.

**Product target Linux/ARM Profile is NOT accepted.** This leftover does **not** redo #105 collect/verify, #127 lifecycle, #157 honesty, or #193 leftover honesty. It does **not** rewrite P0-10 / M0-01～M0-12. It does **not** change `AuditPort`. It does **not** boot a product JAR. **G18 stays closed.**

See [docs/linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [linux-arm-profile-honesty.md](../linux-arm-profile-honesty.md) (#157), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), this leftover, plus [g7-query-expense-travel-honesty-remainder.md](../g7-query-expense-travel-honesty-remainder.md) (#239), [g9-redis-down-invalidate-remainder-honesty.md](../g9-redis-down-invalidate-remainder-honesty.md) (#237), [g2-h5-every-page-form-write-remainder-honesty.md](../g2-h5-every-page-form-write-remainder-honesty.md) (#232), [g7-withdraw-browser-evidence-honesty.md](../g7-withdraw-browser-evidence-honesty.md) (#212), [g4-hot-purge-staging-leftover-honesty.md](../g4-hot-purge-staging-leftover-honesty.md) (#230), [g9-live-multi-jvm-replica-remainder-honesty.md](../g9-live-multi-jvm-replica-remainder-honesty.md) (#229), [g3-outbox-consume-idempotent-honesty.md](../g3-outbox-consume-idempotent-honesty.md) (#226), [g10-leftover-honesty-remainder.md](../g10-leftover-honesty-remainder.md) (#228), [g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md) (#221), [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225), and every other leftover row already listed.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Host | this agent VM | `uname -m` filled from the leftover honesty script |
| Runtime | leftover honesty inventory only | No Docker / Compose / QEMU. `#105` / `#127` / `#157` / `#193` scripts not re-run as a product redo |

## Commands

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

Do **not** treat `./scripts/linux-arm-profile-honesty.sh` or `./scripts/linux-arm-profile-leftover-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Do **not** treat an x86_64 leftover note, QEMU, or Docker as a live ARM/Linux product-JAR walk.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** (`2026-09-22T22:25:09Z`) | honest SKIP accepted; fake live ARM/Linux JAR PASS / non-x86 PASS / QEMU-as-ARM / x86-as-ARM / Profile close / redo rejected |
| #157 / #193 stub/inventory harness | **PASS** | parent `--verify-report` only; not a product redo |
| Platform-matrix inventory | **PASS** | `LOCAL-WIN-X64-POC` / `TARGET-X86-01` / `TARGET-ARM-01` |
| `TARGET-ARM-01` / `TARGET-X86-01` | **not-tested** | `poc/p0-10/deployment-matrix.csv` |
| `supply-chain-evidence.csv` | **not-collected** | 11 component rows |
| Live ARM/Linux product-JAR walk | **SKIP** | this host is `x86_64`; walk not observed; product JAR not booted |
| Live non-x86 product-JAR walk | **SKIP** | no native non-x86 host |
| x86 leftover extrapolated to ARM | **false** | #127 / #157 / #193 this-host note only |
| QEMU / Docker used as ARM | **false** | honesty gate |
| Sibling leftover rows | kept | includes #157 / #193 / #239 / #237 / #232 / #212 / #230 / #229 / #226 / #228 / #221 / #225 and ALL leftover rows |
| #105/#127/#157/#193 redo | **false** | sibling leftovers |
| Product Profile acceptance | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not rewritten |

## Honest leftovers

- Approved Linux + native aarch64 (or other non-x86) CPU + JDK 21 + PostgreSQL 15.x delivery row
- Live ARM/Linux or non-x86 product-JAR walk on that row
- Native aarch64 install / upgrade / rollback / official offline media
- Product SBOM + license + CVE + official digest in `supply-chain-evidence.csv`
- Changing `TARGET-ARM-01` / `TARGET-X86-01` from `not-tested`
- Product Profile acceptance
- Redo of #105 / #127 / #157 / #193
