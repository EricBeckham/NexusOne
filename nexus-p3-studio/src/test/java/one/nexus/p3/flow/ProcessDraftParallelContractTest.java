package one.nexus.p3.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p3.flow.persist.JdbcProcessDefinitionDraftStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * ZA14: the draft validate path on XML shaped exactly like the web serializer's output
 * (web/src/views/studio/processDraft.js serializeDraftXml): default + bpmn + xsi + flowable namespaces,
 * flowable:assignee on tasks, xsi:type="bpmn:tFormalExpression" conditions. Parallel and conditional
 * combinations go through ProcessDefinitionValidationService with a tenant-scoped stored draft.
 */
class ProcessDraftParallelContractTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID PERSON = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private final JdbcProcessDefinitionDraftStore store = Mockito.mock(JdbcProcessDefinitionDraftStore.class);
    private final ProcessDefinitionValidationService service = new ProcessDefinitionValidationService(
            new ProcessDefinitionDraftService(store), new ProcessDefinitionValidator());

    @BeforeEach
    void bind() {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), TENANT, UUID.randomUUID(), PERSON, "za14-test", Instant.now(), Instant.now().plusSeconds(60)));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private static String webXml(String key, String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:flowable=\"http://flowable.org/bpmn\""
                + " targetNamespace=\"http://nexus.one/process-drafts\">\n"
                + "  <process id=\"" + key + "\" name=\"" + key + "\" isExecutable=\"true\">\n" + body + "\n  </process>\n</definitions>";
    }

    private static String task(String id, String name) {
        return "    <userTask id=\"" + id + "\" name=\"" + name + "\" flowable:assignee=\"${assignee}\"/>\n";
    }

    private static String node(String type, String id, String name) {
        return "    <" + type + " id=\"" + id + "\" name=\"" + name + "\"/>\n";
    }

    private static String flow(String id, String source, String target) {
        return "    <sequenceFlow id=\"" + id + "\" sourceRef=\"" + source + "\" targetRef=\"" + target + "\">\n    </sequenceFlow>\n";
    }

    private static String conditional(String id, String source, String target, String expression) {
        return "    <sequenceFlow id=\"" + id + "\" sourceRef=\"" + source + "\" targetRef=\"" + target + "\">\n"
                + "      <conditionExpression xsi:type=\"bpmn:tFormalExpression\">" + expression + "</conditionExpression>\n    </sequenceFlow>\n";
    }

    private ProcessDefinitionValidationResult validate(String key, String body) {
        when(store.find(TENANT, key)).thenReturn(Optional.of(new ProcessDefinitionDraft(
                UUID.randomUUID(), TENANT, key, key, webXml(key, body), 1, PERSON, Instant.now(), Instant.now())));
        return service.validateDraft(key);
    }

    @Test
    void webZ9ParallelBlockIsValid() {
        // What addDraftNode(createBlankDraft(), "parallelGateway", { after: "approveTask" }) serializes.
        var result = validate("za14-block",
                node("startEvent", "start", "开始") + task("approveTask", "审批") + node("parallelGateway", "fork", "并行开始")
                        + task("parallel_task", "并行审批 1") + task("parallel_task_2", "并行审批 2") + node("parallelGateway", "join", "并行汇合")
                        + node("endEvent", "end", "结束")
                        + flow("flow_start_task", "start", "approveTask") + flow("flow_approveTask_fork", "approveTask", "fork")
                        + flow("flow_fork_parallel_task", "fork", "parallel_task") + flow("flow_fork_parallel_task_2", "fork", "parallel_task_2")
                        + flow("flow_parallel_task_join", "parallel_task", "join") + flow("flow_parallel_task_2_join", "parallel_task_2", "join")
                        + flow("flow_join_end", "join", "end"));
        assertTrue(result.valid(), result.errors().toString());
    }

    @Test
    void conditionalSplitAheadOfAForkIsValid() {
        var result = validate("za14-combo",
                node("startEvent", "start", "开始") + task("approveTask", "审批") + node("exclusiveGateway", "gateway", "条件分支")
                        + node("parallelGateway", "fork", "并行开始") + task("a", "并行审批 1") + task("b", "并行审批 2")
                        + node("parallelGateway", "join", "并行汇合") + node("endEvent", "end", "结束")
                        + flow("f0", "start", "approveTask") + flow("f1", "approveTask", "gateway")
                        + conditional("toFork", "gateway", "fork", "${outcome == 'APPROVE'}")
                        + conditional("toEnd", "gateway", "end", "${outcome == 'REJECT'}")
                        + flow("f2", "fork", "a") + flow("f3", "fork", "b") + flow("f4", "a", "join") + flow("f5", "b", "join")
                        + flow("f6", "join", "end"));
        assertTrue(result.valid(), result.errors().toString());
    }

    @Test
    void conditionalSplitInsideAParallelBranchIsLocatedAsUnsupported() {
        var result = validate("za14-nested",
                node("startEvent", "start", "开始") + node("parallelGateway", "fork", "并行开始") + node("exclusiveGateway", "x", "条件分支")
                        + task("a", "A") + task("b", "B") + task("c", "C") + node("parallelGateway", "join", "并行汇合") + node("endEvent", "end", "结束")
                        + flow("f0", "start", "fork") + flow("f1", "fork", "x")
                        + conditional("f2", "x", "a", "${outcome == 'APPROVE'}") + flow("f3", "x", "b")
                        + flow("f4", "a", "join") + flow("f5", "b", "join") + flow("f6", "fork", "c") + flow("f7", "c", "join")
                        + flow("f8", "join", "end"));
        assertFalse(result.valid());
        var codes = result.errors().stream().map(ProcessDefinitionValidationResult.ValidationError::code).toList();
        assertTrue(codes.contains("PARALLEL_STRUCTURE_UNSUPPORTED"), codes.toString());
        assertTrue(result.errors().stream().anyMatch(e -> "PARALLEL_STRUCTURE_UNSUPPORTED".equals(e.code()) && "x".equals(e.nodeId())));
    }

    @Test
    void branchesThatEndSeparatelyAreLocatedByForkAndExitFlow() {
        var result = validate("za14-unjoined",
                node("startEvent", "start", "开始") + node("parallelGateway", "fork", "并行开始") + task("a", "A") + task("b", "B")
                        + node("endEvent", "e1", "结束一") + node("endEvent", "e2", "结束二")
                        + flow("f0", "start", "fork") + flow("fa", "fork", "a") + flow("fb", "fork", "b") + flow("f1", "a", "e1") + flow("f2", "b", "e2"));
        var located = result.errors().stream()
                .filter(e -> "PARALLEL_BRANCH_NOT_JOINED".equals(e.code()))
                .map(e -> e.nodeId() + ":" + e.flowId()).sorted().toList();
        assertEquals(List.of("fork:fa", "fork:fb"), located, result.errors().toString());
    }

    @Test
    void branchesMeetingAtASharedEndAreAnEarlyMergeThere() {
        var result = validate("za14-shared-end",
                node("startEvent", "start", "开始") + node("parallelGateway", "fork", "并行开始") + task("a", "A") + task("b", "B")
                        + node("endEvent", "end", "结束")
                        + flow("f0", "start", "fork") + flow("fa", "fork", "a") + flow("fb", "fork", "b") + flow("f1", "a", "end") + flow("f2", "b", "end"));
        var codes = result.errors().stream().map(e -> e.code() + "@" + e.nodeId()).toList();
        assertEquals(List.of("PARALLEL_EARLY_MERGE@end"), codes);
    }
}