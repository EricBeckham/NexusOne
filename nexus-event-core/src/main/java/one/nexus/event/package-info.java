/**
 * M0-09 outbox, dispatcher, idempotency and reconciliation.
 * Tenant context is always {@link one.nexus.isolation.context.RequestIsolationPolicy}
 * / isolation-kernel — this module does not define a second holder.
 */
package one.nexus.event;
