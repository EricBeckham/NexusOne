/**
 * M0-05 owns runtime permission merge. P0-02 ({@code poc/p0-02/}) is the semantic
 * source of truth. This module consumes M0-04 {@code TenantContext} / identity APIs
 * and hooks the M0-02 TenantInjector / DataScopeInjector seams without stripping
 * {@code tenant_id}.
 */
package one.nexus.permission;
