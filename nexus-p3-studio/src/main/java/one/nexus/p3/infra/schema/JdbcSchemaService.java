package one.nexus.p3.infra.schema;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import one.nexus.common.module.NexusModule;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.api.TableDescriptor;
import one.nexus.p3.domain.LedgerDefinition;
import one.nexus.p3.domain.LedgerField;
import one.nexus.p3.domain.LedgerIdentifiers;
import one.nexus.p3.domain.LedgerStatus;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.domain.SchemaAst;
import one.nexus.p3.domain.SystemColumns;
import one.nexus.p3.infra.jdbc.JdbcTableRegistryWriter;
import one.nexus.p3.infra.jdbc.SearchPathConnections;
import one.nexus.p3.port.LedgerRepository;
import one.nexus.p3.schema.SchemaService;

/**
 * Only type allowed to execute dynamic ledger DDL. Renders from {@link SchemaAst};
 * never accepts caller-supplied SQL text.
 */
@Service
public class JdbcSchemaService implements SchemaService {
    public static final int ADVISORY_LOCK = 4006;

    private final DataSource dataSource;
    private final IsolationKernel kernel;
    private final JdbcTableRegistryWriter registryWriter;
    private final LedgerRepository ledgers;

    public JdbcSchemaService(
            DataSource dataSource,
            IsolationKernel kernel,
            JdbcTableRegistryWriter registryWriter,
            LedgerRepository ledgers) {
        this.dataSource = dataSource;
        this.kernel = kernel;
        this.registryWriter = registryWriter;
        this.ledgers = ledgers;
    }

    @Override
    public SchemaAst astOf(LedgerDefinition ledger, List<LedgerField> fields) {
        LedgerIdentifiers.requirePhysicalTable(ledger.physicalTable());
        List<SchemaAst.Field> astFields = fields.stream().map(LedgerField::toAst).toList();
        return new SchemaAst(ledger.physicalTable(), ledger.scope(), astFields);
    }

    @Override
    @Transactional
    public LedgerDefinition createPhysicalTable(LedgerDefinition ledger, List<LedgerField> fields) {
        SchemaAst ast = astOf(ledger, fields);
        Connection connection = SearchPathConnections.open(dataSource);
        try {
            lock(connection);
            if (registryWriter.tableExists(connection, ast.tableName())) {
                throw P3Exception.tableConflict(ast.tableName());
            }
            execute(connection, PostgresDialectRenderer.createTable(ast));
            LedgerDefinition ready = withReady(ledger, 1);
            registryWriter.upsert(connection, ready);
            registerRuntime(ready, fields);
            ledgers.update(ready);
            return ready;
        } catch (SQLException ex) {
            throw new IllegalStateException("SchemaService create failed for " + ledger.physicalTable(), ex);
        } finally {
            SearchPathConnections.release(dataSource, connection);
        }
    }

    @Override
    @Transactional
    public LedgerDefinition evolvePhysicalTable(
            LedgerDefinition ledger, List<LedgerField> currentFields, LedgerField added) {
        if (!ledger.ready()) {
            throw P3Exception.ledgerNotReady(ledger.id());
        }
        List<LedgerField> nextFields = new ArrayList<>(currentFields);
        nextFields.add(added);
        astOf(ledger, nextFields);
        Set<String> existing = new LinkedHashSet<>();
        currentFields.forEach(field -> existing.add(field.fieldCode()));
        if (existing.contains(added.fieldCode())) {
            throw P3Exception.illegalEvolution("field already exists", Map.of("field", added.fieldCode()));
        }
        Connection connection = SearchPathConnections.open(dataSource);
        try {
            lock(connection);
            if (!registryWriter.tableExists(connection, ledger.physicalTable())) {
                throw P3Exception.ledgerNotReady(ledger.id());
            }
            List<String> ddl = new ArrayList<>();
            ddl.add(PostgresDialectRenderer.addColumn(ledger.physicalTable(), added.toAst()));
            if (added.indexed()) {
                ddl.add(PostgresDialectRenderer.indexSql(ledger.physicalTable(), added.fieldCode()));
            }
            execute(connection, ddl);
            LedgerDefinition evolved = withReady(ledger, ledger.schemaVersion() + 1);
            registryWriter.upsert(connection, evolved);
            registerRuntime(evolved, nextFields);
            ledgers.update(evolved);
            return evolved;
        } catch (SQLException ex) {
            throw new IllegalStateException("SchemaService evolve failed for " + ledger.physicalTable(), ex);
        } finally {
            SearchPathConnections.release(dataSource, connection);
        }
    }

    @Override
    public void confirmPhysicalTable(LedgerDefinition ledger, List<LedgerField> fields) {
        astOf(ledger, fields);
        if (!ledger.ready()) {
            throw P3Exception.ledgerNotReady(ledger.id());
        }
        Connection connection = SearchPathConnections.open(dataSource);
        try {
            lock(connection);
            if (!registryWriter.tableExists(connection, ledger.physicalTable())) {
                throw P3Exception.ledgerNotReady(ledger.id());
            }
            registerRuntime(ledger, fields);
        } catch (SQLException ex) {
            throw new IllegalStateException("SchemaService confirm failed for " + ledger.physicalTable(), ex);
        } finally {
            SearchPathConnections.release(dataSource, connection);
        }
    }

    @Override
    public void registerRuntime(LedgerDefinition ledger, List<LedgerField> fields) {
        Set<String> columns = new LinkedHashSet<>(SystemColumns.ALL);
        fields.forEach(field -> columns.add(LedgerIdentifiers.requireFieldCode(field.fieldCode())));
        TableDescriptor descriptor = new TableDescriptor(
                ledger.physicalTable(),
                ledger.scope().tableScope(),
                columns,
                NexusModule.P3_STUDIO,
                ledger.schemaVersion());
        if (kernel.registry().snapshot().containsKey(descriptor.name())) {
            kernel.registry().replace(descriptor);
        } else {
            kernel.registry().register(descriptor);
        }
    }

    private static LedgerDefinition withReady(LedgerDefinition ledger, int schemaVersion) {
        Instant now = Instant.now();
        return new LedgerDefinition(
                ledger.id(),
                ledger.tenantId(),
                ledger.tenantCode(),
                ledger.applicationId(),
                ledger.ledgerCode(),
                ledger.displayName(),
                ledger.scope(),
                ledger.physicalTable(),
                schemaVersion,
                LedgerStatus.READY,
                ledger.createdAt(),
                ledger.createdBy(),
                now,
                ledger.updatedBy(),
                ledger.deletedAt(),
                ledger.version() + 1);
    }

    private static void lock(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT pg_advisory_xact_lock(" + ADVISORY_LOCK + ", 1)");
        }
    }

    private static void execute(Connection connection, List<String> statements) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }
}
