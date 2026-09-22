package one.nexus.p3.infra.schema;

import java.util.ArrayList;
import java.util.List;
import one.nexus.p3.domain.LedgerIdentifiers;
import one.nexus.p3.domain.SchemaAst;
import one.nexus.p3.domain.SystemColumns;

/**
 * Renders Schema AST to parameterized-identifier PostgreSQL DDL. Does not execute.
 * Only {@link JdbcSchemaService} may submit the returned statements.
 */
public final class PostgresDialectRenderer {
    public static final String PHYSICAL_SCHEMA = "business_data";

    private PostgresDialectRenderer() {}

    public static String quoteIdent(String ident) {
        if (ident == null || !ident.matches("[a-z_][a-z0-9_]*")) {
            throw one.nexus.p3.domain.P3Exception.invalidIdentifier(ident);
        }
        return '"' + ident + '"';
    }

    public static String qualifiedTable(String table) {
        return quoteIdent(PHYSICAL_SCHEMA) + '.' + quoteIdent(LedgerIdentifiers.requirePhysicalTable(table));
    }

    public static List<String> createTable(SchemaAst ast) {
        String table = qualifiedTable(ast.tableName());
        StringBuilder create = new StringBuilder();
        create.append("CREATE TABLE ").append(table).append(" (");
        create.append(quoteIdent(SystemColumns.ID)).append(" uuid PRIMARY KEY, ");
        create.append(quoteIdent(SystemColumns.TENANT_ID)).append(" text NOT NULL, ");
        create.append(quoteIdent(SystemColumns.OWNER_ID)).append(" text, ");
        create.append(quoteIdent(SystemColumns.DEPARTMENT_ID)).append(" text, ");
        create.append(quoteIdent(SystemColumns.CREATED_AT)).append(" timestamptz NOT NULL, ");
        create.append(quoteIdent(SystemColumns.CREATED_BY)).append(" text NOT NULL, ");
        create.append(quoteIdent(SystemColumns.UPDATED_AT)).append(" timestamptz NOT NULL, ");
        create.append(quoteIdent(SystemColumns.UPDATED_BY)).append(" text NOT NULL, ");
        create.append(quoteIdent(SystemColumns.DELETED_AT)).append(" timestamptz, ");
        create.append(quoteIdent(SystemColumns.VERSION)).append(" bigint NOT NULL DEFAULT 1, ");
        create.append(quoteIdent(SystemColumns.SCHEMA_VERSION)).append(" integer NOT NULL, ");
        create.append(quoteIdent(SystemColumns.EXT_DATA)).append(" jsonb NOT NULL DEFAULT '{}'::jsonb");
        for (SchemaAst.Field field : ast.fields()) {
            create.append(", ").append(quoteIdent(field.name())).append(' ').append(field.type().sqlType());
            if (field.required()) {
                create.append(" NOT NULL");
            }
        }
        create.append(')');
        List<String> statements = new ArrayList<>();
        statements.add(create.toString());
        statements.add("CREATE INDEX " + quoteIdent(ast.tableName() + "_tenant_deleted_idx")
                + " ON " + table + " (" + quoteIdent(SystemColumns.TENANT_ID) + ", "
                + quoteIdent(SystemColumns.DELETED_AT) + ")");
        for (SchemaAst.Field field : ast.fields()) {
            if (field.indexed()) {
                statements.add(indexSql(ast.tableName(), field.name()));
            }
        }
        return List.copyOf(statements);
    }

    public static String addColumn(String tableName, SchemaAst.Field field) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(qualifiedTable(tableName))
                .append(" ADD COLUMN ").append(quoteIdent(field.name()))
                .append(' ').append(field.type().sqlType());
        if (field.required()) {
            sql.append(" NOT NULL");
        }
        return sql.toString();
    }

    public static String indexSql(String tableName, String fieldName) {
        return "CREATE INDEX " + quoteIdent(tableName + "_" + fieldName + "_idx")
                + " ON " + qualifiedTable(tableName) + " (" + quoteIdent(fieldName) + ")";
    }
}
