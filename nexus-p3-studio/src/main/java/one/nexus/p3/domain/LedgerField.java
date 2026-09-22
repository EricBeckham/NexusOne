package one.nexus.p3.domain;

import java.time.Instant;
import java.util.UUID;

public record LedgerField(
        UUID id,
        UUID tenantId,
        UUID ledgerId,
        String fieldCode,
        String displayName,
        FieldType fieldType,
        boolean required,
        boolean indexed,
        int sortOrder,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        Instant deletedAt,
        long version
) {
    public SchemaAst.Field toAst() {
        return new SchemaAst.Field(fieldCode, fieldType, required, indexed);
    }
}
