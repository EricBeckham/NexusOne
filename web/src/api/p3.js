/**
 * P3 studio API facade (ledger / form / todo).
 *
 * Always uses live HTTP against M0-06/07/08. MOCK adapters are retired.
 * Tenant scope stays on X-Nexus-Context-Id (ARCH-024). Never X-Tenant-Id.
 */
import * as live from "./p3-live";

export function p3Source() {
  return "LIVE";
}

export const p3 = {
  source: p3Source,
  listLedgers: (...args) => live.listLedgers(...args),
  getLedger: (...args) => live.getLedger(...args),
  listFields: (...args) => live.listFields(...args),
  addField: (...args) => live.addField(...args),
  updateField: (...args) => live.updateField(...args),
  listRecords: (...args) => live.listRecords(...args),
  listForms: (...args) => live.listForms(...args),
  getForm: (...args) => live.getForm(...args),
  fillForm: (...args) => live.fillForm(...args),
  validateForm: (...args) => live.validateForm(...args),
  publishForm: (...args) => live.publishForm(...args),
  simulatePublishFailure: (...args) => live.simulatePublishFailure(...args),
  listTodos: (...args) => live.listTodos(...args),
  getTodo: (...args) => live.getTodo(...args),
  approveTodo: (...args) => live.approveTodo(...args),
  rejectTodo: (...args) => live.rejectTodo(...args),
  withdrawTodo: (...args) => live.withdrawTodo(...args),
};
