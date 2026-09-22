package one.nexus.task;

import one.nexus.isolation.boundary.IsolatedEnvelope;

public interface TaskHandler {
    boolean supports(String taskType);

    void execute(IsolatedEnvelope envelope);
}
