package one.nexus.permission.redis;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class InMemoryPolicyInvalidation implements PolicyInvalidationPort {
    private final Set<String> notified = ConcurrentHashMap.newKeySet();
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public InMemoryPolicyInvalidation() {}

    public InMemoryPolicyInvalidation(Consumer<String> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void addListener(Consumer<String> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void notifyTenant(String tenantId) {
        notified.add(tenantId);
        for (Consumer<String> listener : listeners) {
            listener.accept(tenantId);
        }
    }

    @Override
    public void onRemoteInvalidation(String tenantId) {
        notifyTenant(tenantId);
    }

    public boolean wasNotified(String tenantId) {
        return notified.contains(tenantId);
    }

    public int listenerCount() {
        return listeners.size();
    }

    @Override
    public void shutdown() {
        listeners.clear();
        notified.clear();
    }
}
