package one.nexus.p1.config;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.p1")
public class P1Properties {

    private final DemoContext demoContext = new DemoContext();
    private final DefaultPack defaultPack = new DefaultPack();
    private final Quota quota = new Quota();
    private final PlatformTenant platformTenant = new PlatformTenant();

    public DemoContext getDemoContext() {
        return demoContext;
    }

    public DefaultPack getDefaultPack() {
        return defaultPack;
    }

    public Quota getQuota() {
        return quota;
    }

    public PlatformTenant getPlatformTenant() {
        return platformTenant;
    }

    public static class DemoContext {
        /** Must stay false when M0-01/M0-02 entry is live. */
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class DefaultPack {
        private String code = "nexus.default.m0";
        private String version = "0.1.0";

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    public static class Quota {
        private int appLimit = 5;
        private int customLedgerLimit = 10;
        private int warnPercent = 80;

        public int getAppLimit() {
            return appLimit;
        }

        public void setAppLimit(int appLimit) {
            this.appLimit = appLimit;
        }

        public int getCustomLedgerLimit() {
            return customLedgerLimit;
        }

        public void setCustomLedgerLimit(int customLedgerLimit) {
            this.customLedgerLimit = customLedgerLimit;
        }

        public int getWarnPercent() {
            return warnPercent;
        }

        public void setWarnPercent(int warnPercent) {
            this.warnPercent = warnPercent;
        }
    }

    public static class PlatformTenant {
        private UUID id = UUID.fromString("01900000-0000-7000-8000-000000000001");
        private String code = "platform";
        private String name = "NexusOne Platform";

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
