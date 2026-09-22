package one.nexus.p3.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaAstTest {
    @Test
    void rejectsDuplicateAndReservedFields() {
        assertThrows(P3Exception.class, () -> new SchemaAst(
                "l_alpha_orders",
                LedgerScope.TENANT,
                List.of(
                        new SchemaAst.Field("title", FieldType.TEXT, true, true),
                        new SchemaAst.Field("title", FieldType.TEXT, false, false))));
        assertThrows(P3Exception.class, () -> new SchemaAst.Field("owner_id", FieldType.TEXT, false, false));
    }

    @Test
    void keepsFieldOrder() {
        SchemaAst ast = new SchemaAst(
                "l_alpha_orders",
                LedgerScope.TENANT,
                List.of(
                        new SchemaAst.Field("title", FieldType.TEXT, true, true),
                        new SchemaAst.Field("amount", FieldType.NUMERIC, false, false)));
        assertEquals("title", ast.fields().get(0).name());
        assertEquals("numeric", ast.fields().get(1).type().sqlType());
    }
}
