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
}
