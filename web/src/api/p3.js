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
  listApplications: (...args) => live.listApplications(...args),
  createApplication: (...args) => live.createApplication(...args),
  listLedgers: (...args) => live.listLedgers(...args),
  createLedger: (...args) => live.createLedger(...args),
  getLedger: (...args) => live.getLedger(...args),
  listFields: (...args) => live.listFields(...args),
  addField: (...args) => live.addField(...args),
  updateField: (...args) => live.updateField(...args),
  listRecords: (...args) => live.listRecords(...args),
  getRecord: (...args) => live.getRecord(...args),
  createRecord: (...args) => live.createRecord(...args),
  updateRecord: (...args) => live.updateRecord(...args),
  deleteRecord: (...args) => live.deleteRecord(...args),
  listForms: (...args) => live.listForms(...args),
  createForm: (...args) => live.createForm(...args),
  updateForm: (...args) => live.updateForm(...args),
  getForm: (...args) => live.getForm(...args),
  fillForm: (...args) => live.fillForm(...args),
  submitStart: (...args) => live.submitStart(...args),
  validateForm: (...args) => live.validateForm(...args),
  publishForm: (...args) => live.publishForm(...args),
  simulatePublishFailure: (...args) => live.simulatePublishFailure(...args),
  listTodos: (...args) => live.listTodos(...args),
  getTodo: (...args) => live.getTodo(...args),
  approveTodo: (...args) => live.approveTodo(...args),
  listProductApplications: (...args) => live.listProductApplications(...args),
  productApplicationForm: (...args) => live.productApplicationForm(...args),
  createProductApplication: (...args) => live.createProductApplication(...args),
  rejectTodo: (...args) => live.rejectTodo(...args),
  withdrawTodo: (...args) => live.withdrawTodo(...args),
  listBusinessTypes: (...args) => live.listBusinessTypes(...args),
  provisionBusinessType: (...args) => live.provisionBusinessType(...args),
  reportDecision: (...args) => live.reportDecision(...args),
  queryReport: (...args) => live.queryReport(...args),
  exportReport: (...args) => live.exportReport(...args),
  dumpDecision: (...args) => live.dumpDecision(...args),
  exportDump: (...args) => live.exportDump(...args),
  exportWizardDecision: (...args) => live.exportWizardDecision(...args),
  beginExportWizard: (...args) => live.beginExportWizard(...args),
  completeExportWizard: (...args) => live.completeExportWizard(...args),
  listProcessDefinitions: (...args) => live.listProcessDefinitions(...args),
  getProcessDefinition: (...args) => live.getProcessDefinition(...args),
  listProcessDrafts: (...args) => live.listProcessDrafts(...args),
  getProcessDraft: (...args) => live.getProcessDraft(...args),
  saveProcessDraft: (...args) => live.saveProcessDraft(...args),
  validateProcessDraft: (...args) => live.validateProcessDraft(...args),
  getProcessPublication: (...args) => live.getProcessPublication(...args),
  publishProcessDraft: (...args) => live.publishProcessDraft(...args),
  listPublishCenter: (...args) => live.listPublishCenter(...args),
  getPublishCenter: (...args) => live.getPublishCenter(...args),
};
