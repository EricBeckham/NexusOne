package one.nexus.p3.flyway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import one.nexus.p3.flow.ProcessDefinitionValidationResult;
import one.nexus.p3.flow.persist.JdbcProcessDefinitionDraftStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * ZA12: the ZA wave (ZA4 snap, ZA7–ZA9 conditions, ZA11 fork/join pairing) needs no Flyway migration.
 * Gateways and conditions live inside the draft's bpmn_xml text; pairing findings travel in the existing
 * validation result shape and are never stored; snap is constants only (ZA4, #556).
 */
class ParallelSemanticsSchemaContractTest {
    private static final String FORK_JOIN_XML = "<definitions><process id='p'><startEvent id='s'/>"
            + "<parallelGateway id='fork' name='并行开始'/><userTask id='a' name='A'/><userTask id='b' name='B'/>"
            + "<parallelGateway id='join' name='并行汇合'/><exclusiveGateway id='x' name='判断'/><endEvent id='e'/>"
            + "<sequenceFlow id='f1' sourceRef='fork' targetRef='a'/>"
            + "<sequenceFlow id='f2' sourceRef='x' targetRef='e'><conditionExpression>${outcome == 'APPROVE'}</conditionExpression></sequenceFlow>"
            + "</process></definitions>";

    @Test
    void pairingFindingsFitTheExistingValidationResultShape() {
        var components = Arrays.stream(ProcessDefinitionValidationResult.ValidationError.class.getRecordComponents())
                .map(component -> component.getName()).toList();
        assertEquals(List.of("code", "message", "nodeId", "flowId"), components,
                "ZA11 reuses code/message/node/flow locators; a new field would be an API change, not a schema change");
    }

    @Test
    void forkJoinAndConditionsAreStoredVerbatimInTheBpmnXmlColumn() {
        JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
        UUID tenant = UUID.randomUUID();
        when(jdbc.query(anyString(), Mockito.<RowMapper<Object>>any(), Mockito.<Object[]>any())).thenReturn(List.of());
        var store = new JdbcProcessDefinitionDraftStore(jdbc);
        try {
            store.save(tenant, UUID.randomUUID(), "p", "并行", FORK_JOIN_XML);
        } catch (IllegalStateException expected) {
            // the mocked read-back is empty; only the bound write matters here
        }
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("bpmn_xml"), "the draft XML goes into the existing bpmn_xml column");
        assertEquals(FORK_JOIN_XML, args.getValue()[4], "gateways and conditions are bound verbatim, no extra columns");
        assertEquals(6, args.getValue().length, "id, tenant, key, name, bpmn_xml, actor — unchanged binding");
    }

    @Test
    void noMigrationAddsGatewayPairingSnapOrValidationStorage() throws Exception {
        Pattern forbidden = Pattern.compile("\\b(gateway\\w*|parallel\\w*|fork_\\w*|pairing\\w*|snap_\\w*|validation_error\\w*|condition_dsl\\w*)\\b");
        List<Path> roots = Stream.of("src/main/resources/db/migration", "../nexus-bootstrap/src/main/resources/db/migration")
                .map(Path::of).filter(Files::isDirectory).toList();
        assertEquals(2, roots.size(), "both migration roots must be scanned");
        for (Path root : roots) {
            try (var files = Files.list(root)) {
                for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".sql")).toList()) {
                    String sql = Files.readString(file).toLowerCase();
                    var match = forbidden.matcher(sql);
                    boolean hit = match.find();
                    assertFalse(hit, () -> file.getFileName() + " mentions " + match.group());
                }
            }
        }
    }
}