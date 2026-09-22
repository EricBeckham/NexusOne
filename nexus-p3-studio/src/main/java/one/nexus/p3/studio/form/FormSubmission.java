package one.nexus.p3.studio.form;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FormSubmission(
        UUID submissionId,
        UUID formId,
        UUID tenantId,
        int runningVersion,
        Map<String, Object> values,
        Instant submittedAt,
        String submittedBy) {}
