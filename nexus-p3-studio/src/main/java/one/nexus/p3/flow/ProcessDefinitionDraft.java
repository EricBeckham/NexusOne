package one.nexus.p3.flow;

import java.time.Instant;
import java.util.UUID;

public record ProcessDefinitionDraft(
        UUID id,
        UUID tenantId,
        String key,
        String name,
        String bpmnXml,
        int version,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt) {}
