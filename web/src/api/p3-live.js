/**
 * Thin live P3 client. Paths match the dual-tenant SKIP placeholders.
 * M0-06/07/08 have not shipped these routes; callers will see HTTP 404
 * when VITE_NEXUS_P3_USE_MOCK=false. Tenant scope stays on X-Nexus-Context-Id.
 */
import { api } from "./client";

const wrap = (data) => ({ ...data, _source: "LIVE" });

export async function listLedgers() {
  return wrap({ items: (await api("/api/v1/p3/ledgers")) || [] });
}

export function getLedger(id) {
  return api(`/api/v1/p3/ledgers/${id}`);
}

export function listFields(ledgerId) {
  return api(`/api/v1/p3/ledgers/${ledgerId}/fields`);
}

export function addField(ledgerId, body) {
  return api(`/api/v1/p3/ledgers/${ledgerId}/fields`, { method: "POST", body });
}

export function updateField(ledgerId, fieldId, body) {
  return api(`/api/v1/p3/ledgers/${ledgerId}/fields/${fieldId}`, { method: "PUT", body });
}

export function listRecords(ledgerId) {
  return api(`/api/v1/p3/ledgers/${ledgerId}/records`);
}

export async function listForms() {
  return wrap({ items: (await api("/api/v1/p3/forms")) || [] });
}

export function getForm(id) {
  return api(`/api/v1/p3/forms/${id}`);
}

export function fillForm(id, body) {
  return api(`/api/v1/p3/forms/${id}/fill`, { method: "POST", body });
}

export function validateForm(id) {
  return api(`/api/v1/p3/forms/${id}/validate`, { method: "POST", body: {} });
}

export function publishForm(id) {
  return api(`/api/v1/p3/forms/${id}/publish`, { method: "POST", body: {} });
}

export function simulatePublishFailure(id) {
  return api(`/api/v1/p3/forms/${id}/publish`, {
    method: "POST",
    body: { simulate_failure: true },
  });
}

export async function listTodos(box = "inbox") {
  return wrap({ items: (await api(`/api/v1/p3/todos?box=${encodeURIComponent(box)}`)) || [] });
}

export function getTodo(id) {
  return api(`/api/v1/p3/todos/${id}`);
}

export function approveTodo(id, comment) {
  return api(`/api/v1/p3/todos/${id}/approve`, { method: "POST", body: { comment } });
}

export function rejectTodo(id, comment) {
  return api(`/api/v1/p3/todos/${id}/reject`, { method: "POST", body: { comment } });
}

export function withdrawTodo(id, comment) {
  return api(`/api/v1/p3/todos/${id}/withdraw`, { method: "POST", body: { comment } });
}

export function resetMock() {
  throw new Error("LIVE adapter has no mock store");
}
