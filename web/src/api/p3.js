/**
 * P3 studio API facade (ledger / form / todo).
 *
 * Default source is MOCK until M0-06/07/08 HTTP APIs exist.
 * Swap:
 *   1. VITE_NEXUS_P3_USE_MOCK=false   (build / Vite)
 *   2. sessionStorage nexus.m012.p3UseMock = false  (runtime)
 * Live module talks to /api/v1/p3/* via the shared client (no X-Tenant-Id).
 * MOCK responses are not G18 evidence.
 */
import { loadSettings, saveSettings } from "./client";
import * as live from "./p3-live";
import * as mock from "./mocks/p3-mock";

export function envWantsMock() {
  const env = import.meta.env.VITE_NEXUS_P3_USE_MOCK;
  if (env === "0" || env === "false") {
    return false;
  }
  return true;
}

export function useP3Mock() {
  const stored = loadSettings().p3UseMock;
  if (stored === false) {
    return false;
  }
  if (stored === true) {
    return true;
  }
  return envWantsMock();
}

export function setP3UseMock(flag) {
  const next = { ...loadSettings(), p3UseMock: Boolean(flag) };
  saveSettings(next);
}

export function p3Source() {
  return useP3Mock() ? "MOCK" : "LIVE";
}

function adapter() {
  return useP3Mock() ? mock : live;
}

export const p3 = {
  source: p3Source,
  listLedgers: (...args) => adapter().listLedgers(...args),
  getLedger: (...args) => adapter().getLedger(...args),
  listFields: (...args) => adapter().listFields(...args),
  addField: (...args) => adapter().addField(...args),
  updateField: (...args) => adapter().updateField(...args),
  listRecords: (...args) => adapter().listRecords(...args),
  listForms: (...args) => adapter().listForms(...args),
  getForm: (...args) => adapter().getForm(...args),
  fillForm: (...args) => adapter().fillForm(...args),
  validateForm: (...args) => adapter().validateForm(...args),
  publishForm: (...args) => adapter().publishForm(...args),
  simulatePublishFailure: (...args) => adapter().simulatePublishFailure(...args),
  listTodos: (...args) => adapter().listTodos(...args),
  getTodo: (...args) => adapter().getTodo(...args),
  approveTodo: (...args) => adapter().approveTodo(...args),
  rejectTodo: (...args) => adapter().rejectTodo(...args),
  withdrawTodo: (...args) => adapter().withdrawTodo(...args),
  resetMock: () => adapter().resetMock?.(),
};
