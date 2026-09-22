package one.nexus.permission.redis;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class InMemoryPolicyInvalidation implements PolicyInvalidationPort {
    private final Set<String> notified = ConcurrentHashMap.newKeySet();
    private final Consumer<String> listener;

    public InMemoryPolicyInvalidation() {
        this(ignored -> {});
    }

    public InMemoryPolicyInvalidation(Consumer<String> listener) {
        this.listener = listener == null ? ignored -> {} : listener;
    }

    @Override
    public void notifyTenant(String tenantId) {
        notified.add(tenantId);
        listener.accept(tenantId);
    }

    public boolean wasNotified(String tenantId) {
        return notified.contains(tenantId);
    }
}
