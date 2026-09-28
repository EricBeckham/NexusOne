package one.nexus.permission.pack;

import java.util.UUID;

/**
 * PLX1 C3: a request-bound capability to write into one named tenant from a platform context (e.g. an approved
 * activation). Implemented by the issuer — {@code PlatformActivationCommand} in the market module — never built from
 * HTTP input. {@link #assertActive()} must throw once the capability has left its issuing request.
 */
public interface ApprovedTenantWrite {
    UUID targetTenantId();

    UUID actorId();

    String traceId();

    void assertActive();
}
