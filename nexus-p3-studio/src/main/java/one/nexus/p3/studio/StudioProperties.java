package one.nexus.p3.studio;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.studio")
public class StudioProperties {
    /** memory | jdbc */
    private String persistence = "jdbc";
    /** Test-only TenantContext from X-Nexus-Demo-* headers. Not a production tenant source. */
    private boolean demoContext = false;
    /** Seed the in-memory metadata double when M0-06 JDBC repositories are absent. */
    private boolean seedDemoMetadata = true;

    public String getPersistence() {
        return persistence;
    }

    public void setPersistence(String persistence) {
        this.persistence = persistence;
    }

    public boolean isDemoContext() {
        return demoContext;
    }

    public void setDemoContext(boolean demoContext) {
        this.demoContext = demoContext;
    }

    public boolean isSeedDemoMetadata() {
        return seedDemoMetadata;
    }

    public void setSeedDemoMetadata(boolean seedDemoMetadata) {
        this.seedDemoMetadata = seedDemoMetadata;
    }
}
