package one.nexus.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.cache")
public class CacheProperties {
    /** Isolation-kernel env token. Must not contain ':'. */
    private String env = "dev";
    /** auto | memory | redis */
    private String mode = "auto";
    private String host = "127.0.0.1";
    private int port = 6379;

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
