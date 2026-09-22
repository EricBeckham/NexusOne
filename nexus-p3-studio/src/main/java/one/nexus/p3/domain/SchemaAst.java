package one.nexus.p3.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Restricted schema AST. SchemaService is the only consumer that may render/execute it. */
public record SchemaAst(String tableName, LedgerScope scope, List<Field> fields) {
    public SchemaAst {
        tableName = LedgerIdentifiers.requirePhysicalTable(tableName);
        scope = Objects.requireNonNullElse(scope, LedgerScope.TENANT);
        List<Field> copy = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Field field : fields == null ? List.<Field>of() : fields) {
            Objects.requireNonNull(field, "field");
            if (!seen.add(field.name())) {
                throw P3Exception.illegalEvolution("duplicate field", java.util.Map.of("field", field.name()));
            }
            copy.add(field);
        }
        fields = List.copyOf(copy);
    }

    public record Field(String name, FieldType type, boolean required, boolean indexed) {
        public Field {
            name = LedgerIdentifiers.requireFieldCode(name);
            type = Objects.requireNonNull(type, "type");
        }
    }
}
