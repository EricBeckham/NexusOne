# Linux / ARM Profile leftover remainder honesty beyond #236/#193/#157 — evidence

Recorded on this branch after rebase onto `origin/main` `f53e062` (Merge #262 G10 file/object leftover honesty after #259 G9 leftover honesty remainder after #249 G17 SoD leftover after #240 / #254 / #236). Operator: cloud agent. Live output: `.local/linux-arm-profile-leftover-remainder-honesty/` (gitignored). A sanitized copy is [`linux-arm-profile-leftover-remainder-honesty/agent-vm.env`](./linux-arm-profile-leftover-remainder-honesty/agent-vm.env) once recorded.

**Product target Linux/ARM Profile is NOT accepted.** This leftover does **not** redo #105 collect/verify, #127 lifecycle, #157 honesty, #193 leftover honesty, or #236 remainder honesty. It does **not** rewrite P0-10 / M0-01～M0-12. It does **not** change `AuditPort`. It does **not** boot a product JAR. **G18 stays closed.**

See [docs/linux-arm-profile-leftover-remainder-honesty.md](../linux-arm-profile-leftover-remainder-honesty.md). Keep **ALL** sibling leftover rows in [docs/README.md](../README.md): [linux-arm-profile-honesty.md](../linux-arm-profile-honesty.md) (#157), [linux-arm-profile-leftover-honesty.md](../linux-arm-profile-leftover-honesty.md) (#193), [linux-arm-profile-remainder-honesty.md](../linux-arm-profile-remainder-honesty.md) (#236), this leftover, plus [g17-separation-leftover-honesty-remainder.md](../g17-separation-leftover-honesty-remainder.md) (#249), [g9-leftover-honesty-remainder.md](../g9-leftover-honesty-remainder.md) (#259), [g10-file-object-reconcile-leftover-honesty.md](../g10-file-object-reconcile-leftover-honesty.md) (#262), [g15-h5-pkce-miniprogram-remainder-honesty.md](../g15-h5-pkce-miniprogram-remainder-honesty.md) (#240), [g2-h5-every-page-form-write-report-remainder-honesty.md](../g2-h5-every-page-form-write-report-remainder-honesty.md) (#254), and every other leftover row already listed.

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK **21** | product baseline; leftover does not boot the JAR |
| Spring Boot parent | **4.1.1** | product BOM |
| Host | this agent VM | `uname -m` filled from the leftover honesty script |
| Runtime | leftover honesty inventory only | No Docker / Compose / QEMU. `#105` / `#127` / `#157` / `#193` / `#236` scripts not re-run as a product redo |

## Commands

```bash
./scripts/linux-arm-profile-leftover-remainder-honesty.sh
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report <out>/report.env
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-live-arm-jar-pass.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-live-non-x86-pass.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-qemu-as-arm.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-x86-as-arm.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-profile-close.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-redone.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-236-as-live.env fail
./scripts/linux-arm-profile-leftover-remainder-honesty.sh --verify-report \
  scripts/linux-arm-profile-leftover-remainder-honesty/fixtures/fake-236-as-profile-close.env fail
```

Do **not** treat `./scripts/linux-arm-profile-honesty.sh`, `./scripts/linux-arm-profile-leftover-honesty.sh`, or `./scripts/linux-arm-profile-remainder-honesty.sh` as this leftover.

## Results (agent VM)

Filled after the leftover honesty script on this VM. Do **not** treat an x86_64 leftover note, QEMU, Docker, or #236/#193/#157 harness PASS as a live ARM/Linux product-JAR walk.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | honest SKIP accepted; fake live ARM/Linux JAR PASS / non-x86 PASS / QEMU-as-ARM / x86-as-ARM / #236-as-live / #236-as-Profile-close / Profile close / redo rejected |
| #157 / #193 / #236 leftover harness | **PASS** | parent `--verify-report` only; not a product redo; not a live ARM walk |
| Platform-matrix inventory | **PASS** | `LOCAL-WIN-X64-POC` / `TARGET-X86-01` / `TARGET-ARM-01` |
| `TARGET-ARM-01` / `TARGET-X86-01` | **not-tested** | `poc/p0-10/deployment-matrix.csv` |
| `supply-chain-evidence.csv` | **not-collected** | leftover requires not-collected |
| Live ARM/Linux product-JAR walk | **SKIP** | this host is `x86_64`; walk not observed; product JAR not booted |
| Live non-x86 product-JAR walk | **SKIP** | no native non-x86 host |
| x86 leftover extrapolated to ARM | **false** | #127 / #157 / #193 / #236 this-host note only |
| QEMU / Docker used as ARM | **false** | honesty gate |
| Sibling leftover rows | kept | includes #157 / #193 / #236 / #249 G17 SoD remainder / #240 G15 remainder / #254 G2 remainder and ALL leftover rows |
| #105/#127/#157/#193/#236 redo | **false** | sibling leftovers |
| Product Profile acceptance | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not rewritten |

## Honest leftovers

- Approved Linux + native aarch64 (or other non-x86) CPU + JDK 21 + PostgreSQL 15.x delivery row
- Live ARM/Linux or non-x86 product-JAR walk on that row
- Native aarch64 install / upgrade / rollback / official offline media
- Product SBOM + license + CVE + official digest in `supply-chain-evidence.csv`
- Changing `TARGET-ARM-01` / `TARGET-X86-01` from `not-tested`
- Product Profile acceptance
- Treating #236 / #193 / #157 harness PASS as a live ARM walk
- Redo of #105 / #127 / #157 / #193 / #236
