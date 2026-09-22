package one.nexus.isolation.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Tenant-scoped request/async context. Constructed only by {@link one.nexus.isolation.context.TenantContextIssuer}.
 */
public final class TenantContext implements AccessContext {
    private final String tenantId;
    private final Actor actor;
    private final String membershipId;
    private final String traceId;
    private final Instant issuedAt;

    /** Server issuance only. Business modules must not call this; CI forbids {@code new TenantContext}. */
    public TenantContext(String tenantId, Actor actor, String membershipId, String traceId, Instant issuedAt) {
        this.tenantId = Actor.requireToken(tenantId, "tenantId");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.membershipId = Actor.requireToken(membershipId, "membershipId");
        this.traceId = Actor.requireToken(traceId, "traceId");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
    }

    public String tenantId() {
        return tenantId;
    }

    public String membershipId() {
        return membershipId;
    }

    @Override
    public Actor actor() {
        return actor;
    }

    @Override
    public String traceId() {
        return traceId;
    }

    @Override
    public Instant issuedAt() {
        return issuedAt;
    }
}
