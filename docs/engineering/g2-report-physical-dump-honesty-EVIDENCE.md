# G2 leftover: report query/export + physical dump SurfaceGuard honesty evidence

Recorded on this branch after rebase onto `origin/main` `a6c86e7` (Merge #225 G6 RecoveryPort dry-run remainder leftover after #224 / #223 / #222). **Not** a product G2 close. **Not** a redo of #122 / #124 / #137 / #163 / #173 / #204. **Not** a G18 reopen. **Not** a P0/M0 redo. `AuditPort.append` / `appendNewTransaction` same-TX rules were not redesigned.

See [docs/g2-report-physical-dump-honesty.md](../g2-report-physical-dump-honesty.md). Keep **all sibling leftover rows** in [docs/README.md](../README.md), including [g2-report-surface-guard.md](../g2-report-surface-guard.md) (#124), [g2-physical-dump-surface.md](../g2-physical-dump-surface.md) (#137), [g2-surfaceguard-bypass-ci.md](../g2-surfaceguard-bypass-ci.md) (#122), [g2-h5-every-page-form-write-honesty.md](../g2-h5-every-page-form-write-honesty.md) (#204), [g2-ops-pg-dump-honesty.md](../g2-ops-pg-dump-honesty.md) (#188), [g9-sentinel-cluster-remainder-honesty.md](../g9-sentinel-cluster-remainder-honesty.md) (#209), [g5-publishhook-ddl-honesty.md](../g5-publishhook-ddl-honesty.md) (#217), [g17-gm-tls-nginx-handshake-honesty.md](../g17-gm-tls-nginx-handshake-honesty.md) (#210), [g1-queryast-export-honesty.md](../g1-queryast-export-honesty.md) (#215), [g15-wx-dt-native-bridge-remainder.md](../g15-wx-dt-native-bridge-remainder.md) (#220), [g17-separation-leftover-honesty.md](../g17-separation-leftover-honesty.md) (#216), [g4-pkce-spa-verifier-remainder-honesty.md](../g4-pkce-spa-verifier-remainder-honesty.md) (#222), and [s0-secret-rotate-getenv-remainder-honesty.md](../s0-secret-rotate-getenv-remainder-honesty.md) (#223), and [g6-recovery-port-dry-run-remainder-honesty.md](../g6-recovery-port-dry-run-remainder-honesty.md) (#225).

## Versions

| Component | Exact version | Notes |
|---|---|---|
| JDK | OpenJDK 21 | product baseline |
| Maven | 3.8.7 when present | `$HOME/tools/apache-maven-3.8.7` |
| Spring Boot parent | **4.1.1** | product BOM |
| Product JAR | not listening on `127.0.0.1:8080` / `:8088` | `NEXUS_BASE_URL` unset |
| Runtime | collect script + honesty fixtures | No Docker / Compose |

## Commands

```bash
./scripts/g2-report-physical-dump-honesty.sh
./scripts/g2-report-physical-dump-honesty.sh --verify-report <out>/report.env
./scripts/g2-report-physical-dump-honesty.sh --verify-report \
  scripts/g2-report-physical-dump-honesty/fixtures/honest-skip.env
```

Honesty fixtures (must reject fakes):

```bash
# expected FAIL
./scripts/g2-report-physical-dump-honesty.sh --verify-report \
  scripts/g2-report-physical-dump-honesty/fixtures/fake-g2-close.env fail
./scripts/g2-report-physical-dump-honesty.sh --verify-report \
  scripts/g2-report-physical-dump-honesty/fixtures/fake-live-jar-pass.env fail
./scripts/g2-report-physical-dump-honesty.sh --verify-report \
  scripts/g2-report-physical-dump-honesty/fixtures/fake-redone.env fail
./scripts/g2-report-physical-dump-honesty.sh --verify-report \
  scripts/g2-report-physical-dump-honesty/fixtures/fake-report-is-dump.env fail
```

Do **not** treat `./scripts/g2-report-surface-guard.sh` or `./scripts/g2-physical-dump-surface.sh` as this leftover.

## Results (agent VM)

Filled after the collect script on this VM. Live output is gitignored under `.local/g2-report-physical-dump-honesty/`. A sanitized copy is [`g2-report-physical-dump-honesty/agent-vm.env`](./g2-report-physical-dump-honesty/agent-vm.env) once recorded.

| Check | Result | Notes |
|---|---|---|
| Honesty fixtures | **PASS** | fake live PASS / `g2_close=CLOSED` / redo / report-as-dump rejected |
| #124 report SurfaceGuard harness | **PASS** (gate) | not rewritten |
| #137 dump SurfaceGuard harness | **PASS** (gate) | not rewritten |
| leftover rows kept | **true** | #124/#137/#122 + H5 form.write + #217 + #222 + #223 + #225 + all sibling leftover rows |
| Live JAR / issued context | **SKIP** (expected) | `NEXUS_BASE_URL` unset; nothing on `:8080` / `:8088` |
| live report walk | **SKIP** (expected) | no live JAR / issued context |
| live dump walk | **SKIP** (expected) | no live JAR / issued context |
| #122/#124/#137/#163/#173/#204 redo | **false** | sibling leftovers |
| Product G2 close | **NOT_CLAIMED** | honesty gate enforces `NOT_CLAIMED` |
| G18 / AuditPort / P0/M0 | unchanged | not reopened / not redone |

## Honest leftovers

- Live product-JAR + issued-context report query/export walk
- Live product-JAR + issued-context physical dump / raw export walk
- Product G2 close
- Redo of #122 / #124 / #137 / H5 form.write leftovers
- Ops `pg_dump` through SurfaceGuard
