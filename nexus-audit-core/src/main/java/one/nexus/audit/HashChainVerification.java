package one.nexus.audit;

import java.util.Optional;

public record HashChainVerification(boolean intact, long checked, String failure) {
    public static HashChainVerification ok(long checked) {
        return new HashChainVerification(true, checked, null);
    }

    public static HashChainVerification broken(long checked, String failure) {
        return new HashChainVerification(false, checked, failure);
    }

    public Optional<String> failureMessage() {
        return Optional.ofNullable(failure);
    }
}
