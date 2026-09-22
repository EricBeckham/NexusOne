package one.nexus.isolation.api;

public enum TableScope {
    TENANT,
    PLATFORM,
    APP_SHARED;

    public boolean tenantRequired() {
        return this != PLATFORM;
    }
}
