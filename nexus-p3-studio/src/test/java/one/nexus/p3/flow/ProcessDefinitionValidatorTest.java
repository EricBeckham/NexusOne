package one.nexus.p3.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProcessDefinitionValidatorTest {
    private final ProcessDefinitionValidator validator = new ProcessDefinitionValidator();

    @Test
    void acceptsConnectedStartUserTaskAndEndWithAssignee() {
        var result = validator.validate(xml(
                "<startEvent id='start' name='Start'/><userTask id='task' name='Approve' flowable:assignee='${manager}'/>"
                        + "<endEvent id='end' name='Done'/>",
                "<sequenceFlow id='a' sourceRef='start' targetRef='task'/><sequenceFlow id='b' sourceRef='task' targetRef='end'/>") );
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void reportsMissingStartEndDanglingFlowAndRequiredUserTaskFields() {
        var result = validator.validate(xml(
                "<userTask id='task' name=''/><endEvent id='end'/>",
                "<sequenceFlow id='dangling' sourceRef='missing' targetRef='end'/>") );
        assertFalse(result.valid());
        Set<String> codes = result.errors().stream().map(ProcessDefinitionValidationResult.ValidationError::code)
                .collect(Collectors.toSet());
        assertEquals(Set.of("START_REQUIRED", "USER_TASK_NAME_REQUIRED", "USER_TASK_ASSIGNEE_REQUIRED", "DANGLING_FLOW"), codes);
        var noEnd = validator.validate(xml(
                "<startEvent id='start'/><userTask id='task' name='Review' flowable:assignee='${reviewer}'/>",
                "<sequenceFlow id='toTask' sourceRef='start' targetRef='task'/>") );
        assertTrue(noEnd.errors().stream().anyMatch(error -> "END_REQUIRED".equals(error.code())));
    }

    @Test
    void reportsUnreachableNodesAndMalformedXmlWithoutThrowing() {
        var disconnected = validator.validate(xml(
                "<startEvent id='start'/><userTask id='lost' name='Review' flowable:assignee='${reviewer}'/><endEvent id='end'/>",
                "<sequenceFlow id='finish' sourceRef='start' targetRef='end'/>") );
        assertTrue(disconnected.errors().stream().anyMatch(error -> "UNREACHABLE_NODE".equals(error.code()) && "lost".equals(error.nodeId())));
        var malformed = validator.validate("<not-xml");
        assertFalse(malformed.valid());
        assertEquals("INVALID_BPMN_XML", malformed.errors().getFirst().code());
    }

    @Test
    void rejectsDocumentTypeAndExternalEntityPayloads() {
        var result = validator.validate("<!DOCTYPE definitions [<!ENTITY x SYSTEM 'file:///windows/win.ini'>]>"
                + "<definitions><process id='p'><startEvent id='start' name='&x;'/></process></definitions>");
        assertFalse(result.valid());
        assertEquals("INVALID_BPMN_XML", result.errors().getFirst().code());
    }

    private static String xml(String nodes, String flows) {
        return "<definitions xmlns='http://www.omg.org/spec/BPMN/20100524/MODEL' xmlns:flowable='http://flowable.org/bpmn'>"
                + "<process id='test'>" + nodes + flows + "</process></definitions>";
    }

    @Test
    void acceptsParallelForkAndJoin() {
        var result = validator.validate(xml("<startEvent id='s'/><parallelGateway id='fork'/><parallelGateway id='join'/><endEvent id='e'/>",
                "<sequenceFlow id='in' sourceRef='s' targetRef='fork'/>"
                + "<sequenceFlow id='a' sourceRef='fork' targetRef='join'/><sequenceFlow id='b' sourceRef='fork' targetRef='join'/>"
                + "<sequenceFlow id='out' sourceRef='join' targetRef='e'/>"));
        assertTrue(result.valid(), result.errors().toString());
    }

    @Test
    void rejectsParallelConditionsDefaultAndPassThrough() {
        var result = validator.validate(xml("<startEvent id='s'/><parallelGateway id='g' default='out'/><endEvent id='e'/>",
                "<sequenceFlow id='in' sourceRef='s' targetRef='g'/>"
                + "<sequenceFlow id='out' sourceRef='g' targetRef='e'><conditionExpression>${approved}</conditionExpression></sequenceFlow>"));
        var codes = result.errors().stream().map(ProcessDefinitionValidationResult.ValidationError::code).collect(Collectors.toSet());
        assertEquals(Set.of("PARALLEL_DEFAULT_FLOW_UNSUPPORTED", "PARALLEL_BRANCH_REQUIRED", "PARALLEL_FLOW_CONDITION_UNSUPPORTED"), codes);
        assertTrue(result.errors().stream().anyMatch(e -> "out".equals(e.flowId()) && "g".equals(e.nodeId())));
    }

    @Test
    void acceptsExclusiveBranchesWithOpaqueConditionsAndUnconditionalDefault() {
        var result = validator.validate(xml("<startEvent id='s'/><exclusiveGateway id='g' default='fallback'/><endEvent id='e'/>",
                "<sequenceFlow id='in' sourceRef='s' targetRef='g'/>"
                + "<sequenceFlow id='yes' sourceRef='g' targetRef='e'><conditionExpression><![CDATA[${amount > 100}]]></conditionExpression></sequenceFlow>"
                + "<sequenceFlow id='fallback' sourceRef='g' targetRef='e'/>"));
        assertTrue(result.valid(), result.errors().toString());
    }

    @Test
    void locatesEmptyConditionInvalidDefaultAndDisconnectedGateway() {
        var result = validator.validate(xml("<startEvent id='s'/><exclusiveGateway id='g' default='missing'/><endEvent id='e'/>",
                "<sequenceFlow id='empty' sourceRef='g' targetRef='e'><conditionExpression> </conditionExpression></sequenceFlow>"));
        assertTrue(result.errors().stream().anyMatch(e -> "FLOW_CONDITION_EMPTY".equals(e.code()) && "empty".equals(e.flowId())));
        assertTrue(result.errors().stream().anyMatch(e -> "GATEWAY_DEFAULT_FLOW_INVALID".equals(e.code()) && "g".equals(e.nodeId())));
        assertTrue(result.errors().stream().anyMatch(e -> "GATEWAY_CONNECTION_REQUIRED".equals(e.code())));
    }

    // ---- ZA11: thin fork/join pairing ----

    private static String task(String id) {
        return "<userTask id='" + id + "' name='" + id + "' flowable:assignee='${assignee}'/>";
    }

    private static String flow(String id, String source, String target) {
        return "<sequenceFlow id='" + id + "' sourceRef='" + source + "' targetRef='" + target + "'/>";
    }

    private static Set<String> codes(ProcessDefinitionValidationResult result) {
        return result.errors().stream().map(ProcessDefinitionValidationResult.ValidationError::code).collect(Collectors.toSet());
    }

    @Test
    void acceptsSequentialBranchesIntoTheSameJoin() {
        var result = validator.validate(xml(
                "<startEvent id='s'/><parallelGateway id='fork'/>" + task("a1") + task("a2") + task("b") + "<parallelGateway id='join'/><endEvent id='e'/>",
                flow("f0", "s", "fork") + flow("f1", "fork", "a1") + flow("f2", "a1", "a2") + flow("f3", "a2", "join")
                        + flow("f4", "fork", "b") + flow("f5", "b", "join") + flow("f6", "join", "e")));
        assertTrue(result.valid(), result.errors().toString());
    }

    @Test
    void rejectsMixedGatewayWithLocator() {
        var result = validator.validate(xml(
                "<startEvent id='s'/><parallelGateway id='fork'/>" + task("a") + task("b") + "<parallelGateway id='mix'/>" + task("c") + task("d")
                        + "<parallelGateway id='join'/><endEvent id='e'/>",
                flow("f0", "s", "fork") + flow("f1", "fork", "a") + flow("f2", "fork", "b") + flow("f3", "a", "mix") + flow("f4", "b", "mix")
                        + flow("f5", "mix", "c") + flow("f6", "mix", "d") + flow("f7", "c", "join") + flow("f8", "d", "join") + flow("f9", "join", "e")));
        assertTrue(result.errors().stream().anyMatch(e -> "PARALLEL_GATEWAY_MIXED".equals(e.code()) && "mix".equals(e.nodeId())), result.errors().toString());
    }

    @Test
    void rejectsBranchThatEndsWithoutJoining() {
        var result = validator.validate(xml(
                "<startEvent id='s'/><parallelGateway id='fork'/>" + task("a") + task("b") + "<endEvent id='e1'/><endEvent id='e2'/>",
                flow("f0", "s", "fork") + flow("f1", "fork", "a") + flow("f2", "a", "e1") + flow("f3", "fork", "b") + flow("f4", "b", "e2")));
        assertTrue(result.errors().stream().anyMatch(e -> "PARALLEL_BRANCH_NOT_JOINED".equals(e.code()) && "fork".equals(e.nodeId()) && "f1".equals(e.flowId())), result.errors().toString());
        assertTrue(result.errors().stream().anyMatch(e -> "PARALLEL_BRANCH_NOT_JOINED".equals(e.code()) && "f3".equals(e.flowId())));
    }

    @Test
    void rejectsEarlyMergeIntoATask() {
        var result = validator.validate(xml(
                "<startEvent id='s'/><parallelGateway id='fork'/>" + task("a") + task("b") + task("m") + "<endEvent id='e'/>",
                flow("f0", "s", "fork") + flow("f1", "fork", "a") + flow("f2", "fork", "b") + flow("f3", "a", "m") + flow("f4", "b", "m") + flow("f5", "m", "e")));
        assertTrue(result.errors().stream().anyMatch(e -> "PARALLEL_EARLY_MERGE".equals(e.code()) && "m".equals(e.nodeId())), result.errors().toString());
    }

    @Test
    void rejectsNestedSplitAndLoopInsideABranch() {
        var nested = validator.validate(xml(
                "<startEvent id='s'/><parallelGateway id='fork'/><exclusiveGateway id='x'/>" + task("a") + task("b") + task("c")
                        + "<parallelGateway id='join'/><endEvent id='e'/>",
                flow("f0", "s", "fork") + flow("f1", "fork", "x") + flow("f2", "x", "a") + flow("f3", "x", "b") + flow("f4", "a", "join")
                        + flow("f5", "b", "join") + flow("f6", "fork", "c") + flow("f7", "c", "join") + flow("f8", "join", "e")));
        assertTrue(nested.errors().stream().anyMatch(e -> "PARALLEL_STRUCTURE_UNSUPPORTED".equals(e.code()) && "x".equals(e.nodeId())), nested.errors().toString());

        var loop = validator.validate(xml(
                "<startEvent id='s'/><parallelGateway id='fork'/>" + task("a") + task("b") + task("c") + "<parallelGateway id='join'/><endEvent id='e'/>",
                flow("f0", "s", "fork") + flow("f1", "fork", "a") + flow("f2", "a", "b") + flow("f3", "b", "a")
                        + flow("f4", "fork", "c") + flow("f5", "c", "join") + flow("f6", "join", "e")));
        assertTrue(codes(loop).contains("PARALLEL_STRUCTURE_UNSUPPORTED") || codes(loop).contains("PARALLEL_EARLY_MERGE"), loop.errors().toString());
        assertFalse(loop.valid());
    }

    @Test
    void rejectsBranchesThatReachDifferentJoins() {
        var result = validator.validate(xml(
                "<startEvent id='s'/><parallelGateway id='fork'/>" + task("a") + task("b") + task("c") + task("d")
                        + "<parallelGateway id='j1'/><parallelGateway id='j2'/><endEvent id='e1'/><endEvent id='e2'/>",
                flow("f0", "s", "fork") + flow("f1", "fork", "a") + flow("f2", "fork", "b") + flow("f3", "fork", "c") + flow("f3b", "fork", "d")
                        + flow("f4", "a", "j1") + flow("f5", "b", "j1") + flow("f6", "c", "j2") + flow("f6b", "d", "j2")
                        + flow("f7", "j1", "e1") + flow("f8", "j2", "e2")));
        assertTrue(result.errors().stream().anyMatch(e -> "PARALLEL_JOIN_MISMATCH".equals(e.code()) && "fork".equals(e.nodeId()) && "f3".equals(e.flowId())), result.errors().toString());
    }

    @Test
    void rejectsExtraInputIntoAJoinAndAnUnpairedJoin() {
        var extra = validator.validate(xml(
                "<startEvent id='s'/><exclusiveGateway id='x'/><parallelGateway id='fork'/>" + task("a") + task("b")
                        + "<parallelGateway id='join'/><endEvent id='e'/>",
                flow("f0", "s", "x") + flow("f1", "x", "fork") + flow("f2", "x", "join") + flow("f3", "fork", "a") + flow("f4", "fork", "b")
                        + flow("f5", "a", "join") + flow("f6", "b", "join") + flow("f7", "join", "e")));
        assertTrue(extra.errors().stream().anyMatch(e -> "PARALLEL_JOIN_MISMATCH".equals(e.code()) && "join".equals(e.nodeId())), extra.errors().toString());

        var unpaired = validator.validate(xml(
                "<startEvent id='s'/><exclusiveGateway id='x'/>" + task("a") + task("b") + "<parallelGateway id='j'/><endEvent id='e'/>",
                flow("f0", "s", "x") + flow("f1", "x", "a") + flow("f2", "x", "b") + flow("f3", "a", "j") + flow("f4", "b", "j") + flow("f5", "j", "e")));
        assertEquals(Set.of("PARALLEL_JOIN_UNPAIRED"), codes(unpaired));
        assertEquals("j", unpaired.errors().getFirst().nodeId());
    }
}
