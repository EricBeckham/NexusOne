package one.nexus.p3;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.p3")
public class P3Properties {
    /** Test-only demo header binding. Production must use M0-02/M0-04 issued context. */
    private boolean demoContext;

    public boolean isDemoContext() {
        return demoContext;
    }

    public void setDemoContext(boolean demoContext) {
        this.demoContext = demoContext;
    }
}
