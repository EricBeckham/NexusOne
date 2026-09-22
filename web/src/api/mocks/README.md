# P3 MOCK adapters (M0-12 UI only)

Ledger / form / todo pages talk to `web/src/api/p3.js`. That facade picks **MOCK** or **LIVE**.

This is **not** G18. MOCK rows are fixtures for the 台账 / 表单 / 待办 shells. M0-06/07/08 HTTP APIs are on `main`; the UI still defaults to MOCK.

## Markers

- Source module: `p3-mock.js` (header comment starts with `MOCK`).
- Entity ids use the UUIDv7-shaped group `mock` (example `0196c0a0-mock-7000-8000-00000000led1`).
- Every payload includes `_source: "MOCK"`.
- Pages render a `MOCK` banner (`NxSourceBanner`).
- sessionStorage key: `nexus.m012.p3.mock`.

## Toggle MOCK → real APIs

**Default: MOCK on.** Dual-tenant HTTP SKIPs live `/api/v1/p3/*` lists (404 or TenantContext 401/403). Do not treat MOCK as G18.

### 1. Build / Vite env (preferred)

```bash
# stay on fixtures (default)
cd web && npm run dev

# call live HTTP instead (needs TenantContext from M0-06/07/08)
VITE_NEXUS_P3_USE_MOCK=false npm run dev
```

`false` / `0` turn mocks off. Any other value (including unset) keeps mocks on.

### 2. Runtime (no rebuild)

On a P3 page, use **尝试真实 API** / **回到 MOCK** on the banner, or in DevTools:

```js
const key = "nexus.m012.session";
const cur = JSON.parse(sessionStorage.getItem(key) || "{}");
cur.p3UseMock = false; // or true
sessionStorage.setItem(key, JSON.stringify(cur));
location.reload();
```

Runtime override wins over the env flag.

### 3. Code swap

When wiring the live adapter, keep method names in `p3.js` and map them in `p3-live.js` to the M0-06/07/08 routes. Then set `VITE_NEXUS_P3_USE_MOCK=false`. MOCK remains the default until a real G18 walkthrough exists.

Do **not** send `X-Tenant-Id`. Live calls reuse `api()` from `client.js` (ARCH-024).

## Reset fixtures

`p3.resetMock()` or the banner **重置 MOCK** clears `nexus.m012.p3.mock` and reseeds the expense-claim demo.
