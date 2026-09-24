package one.nexus.p2.idp;

import one.nexus.p2.domain.Model;

@FunctionalInterface
public interface IdpConnectionTester {
    /** Returns only success/failure; diagnostics must not contain credentials or response payloads. */
    boolean test(Model.IdpConfiguration provider, byte[] clientSecret);
}
