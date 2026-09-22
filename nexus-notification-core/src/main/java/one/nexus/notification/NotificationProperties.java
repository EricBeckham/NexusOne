package one.nexus.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.notify")
public class NotificationProperties {
    /** memory | jdbc */
    private String persistence = "memory";
    /** auto | record | immediate — auto uses M0-09 OutboxPort when present */
    private String outbox = "auto";
    private boolean demoContext = false;

    public String getPersistence() {
        return persistence;
    }

    public void setPersistence(String persistence) {
        this.persistence = persistence;
    }

    public String getOutbox() {
        return outbox;
    }

    public void setOutbox(String outbox) {
        this.outbox = outbox;
    }

    public boolean isDemoContext() {
        return demoContext;
    }

    public void setDemoContext(boolean demoContext) {
        this.demoContext = demoContext;
    }
}
