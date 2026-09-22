package one.nexus.p3.flow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProcessRecords {
    private ProcessRecords() {}

    public record StartCommand(String processKey, String businessKey, AssigneeType assigneeType, String assigneeRef) {}

    public record ProcessInstanceView(
            UUID id,
            UUID tenantId,
            String processKey,
            String businessKey,
            UUID initiatorPersonId,
            AssigneeType assigneeType,
            String assigneeRef,
            String flowableInstanceId,
            ProcessStatus status,
            Instant startedAt,
            Instant endedAt
    ) {}

    public record TodoView(
            UUID id,
            UUID tenantId,
            UUID instanceId,
            String flowableTaskId,
            String taskName,
            UUID assigneePersonId,
            List<UUID> candidatePersonIds,
            TodoStatus status,
            String outcome,
            UUID actedByPersonId,
            Instant actedAt,
            Instant createdAt,
            String businessKey,
            ProcessStatus instanceStatus
    ) {}
}
