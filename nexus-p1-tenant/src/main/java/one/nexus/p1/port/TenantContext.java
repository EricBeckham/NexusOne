package one.nexus.p1.port;

import java.util.UUID;

/**
 * Server-issued tenant context. M0-02 signs this after login/tenant switch; P1 never trusts
 * a client-supplied tenant_id as identity.
 */
public record TenantContext(UUID tenantId, String tenantCode, boolean platformScope) {}
