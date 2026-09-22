package one.nexus.event;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.execution")
public class ExecutionProperties {
    private Duration pollInterval = Duration.ofMillis(500);
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration lagWarnAfter = Duration.ofSeconds(30);
    private int batchSize = 16;
    private int maxAttempts = 5;
    private boolean dispatcherEnabled = true;
    private boolean workerEnabled = true;
    private boolean quartzEnabled = true;
    private boolean quartzAutoStartup = true;

    public ExecutionSettings toSettings() {
        return new ExecutionSettings(pollInterval, leaseDuration, lagWarnAfter, batchSize, maxAttempts);
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getLagWarnAfter() {
        return lagWarnAfter;
    }

    public void setLagWarnAfter(Duration lagWarnAfter) {
        this.lagWarnAfter = lagWarnAfter;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isDispatcherEnabled() {
        return dispatcherEnabled;
    }

    public boolean dispatcherEnabled() {
        return dispatcherEnabled;
    }

    public void setDispatcherEnabled(boolean dispatcherEnabled) {
        this.dispatcherEnabled = dispatcherEnabled;
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public boolean workerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public boolean isQuartzEnabled() {
        return quartzEnabled;
    }

    public boolean quartzEnabled() {
        return quartzEnabled;
    }

    public void setQuartzEnabled(boolean quartzEnabled) {
        this.quartzEnabled = quartzEnabled;
    }

    public boolean isQuartzAutoStartup() {
        return quartzAutoStartup;
    }

    public boolean quartzAutoStartup() {
        return quartzAutoStartup;
    }

    public void setQuartzAutoStartup(boolean quartzAutoStartup) {
        this.quartzAutoStartup = quartzAutoStartup;
    }
}
