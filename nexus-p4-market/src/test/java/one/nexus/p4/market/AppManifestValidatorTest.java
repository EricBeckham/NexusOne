package one.nexus.p4.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class AppManifestValidatorTest {
    private final AppManifestValidator validator = new AppManifestValidator();

    @Test
    void acceptsRoleAndPositionNodesWithoutLookingUpTenantPositions() {
        assertTrue(validator.validate(parse(VALID)).isEmpty());
    }

    @Test
    void collectsIndependentProblemsInsteadOfStoppingAtFirstError() {
        String broken = VALID.replace("\"type\":\"POSITION\"", "\"type\":\"PERSON\"")
                .replace("\"ref\":\"hr_clerk\"", "\"ref\":\"some_person\"")
                .replace("\"process_key\":\"leave_approval\"", "\"process_key\":\"missing\"")
                .replace("\"scope\":\"SELF\"", "\"scope\":\"SPECIFIED\"");
        var problems = validator.validate(parse(broken));
        assertTrue(problems.stream().anyMatch(p -> p.path().equals("/processes/0/nodes/1/assignee/type")
                && p.reason().equals("invalid_assignee_type")));
        assertTrue(problems.stream().anyMatch(p -> p.path().equals("/entries/0/process_key")
                && p.reason().equals("unknown_ref")));
        assertTrue(problems.stream().anyMatch(p -> p.path().equals("/role_pack/0/grants/0/scope")
                && p.reason().equals("invalid_scope")));
        assertTrue(problems.size() >= 3);
    }

    @Test
    void rejectsNullAndMalformedDocumentAsProblems() {
        assertEquals("required", validator.validate(null).getFirst().reason());
        assertTrue(validator.validate(Map.of("schema", "nexus.app-manifest.v1"))
                .stream().anyMatch(p -> p.path().equals("/entries") && p.reason().equals("required_array")));
    }

    private static Map<String, Object> parse(String source) {
        return JsonMapper.builder().build().readValue(source, new TypeReference<Map<String, Object>>() {});
    }

    private static final String VALID = """
            {
              "schema":"nexus.app-manifest.v1","app_code":"leave","version":"1.0.0",
              "category_code":"oa","name":"请假",
              "ledgers":[{"code":"leave","name":"请假台账","fields":[{"code":"days","name":"天数","type":"NUMERIC"}]}],
              "forms":[
                {"code":"leave_apply","name":"申请","ledger_code":"leave","purpose":"FILL",
                 "layout":{"sections":[{"title":"基本","fields":["days"]}]},"readonly_fields":[]},
                {"code":"leave_list","name":"列表","ledger_code":"leave","purpose":"VIEW",
                 "layout":{"sections":[{"title":"基本","fields":["days"]}]},"readonly_fields":[]},
                {"code":"leave_approve","name":"审批","ledger_code":"leave","purpose":"APPROVE",
                 "layout":{"sections":[{"title":"基本","fields":["days"]}]},"readonly_fields":[]}
              ],
              "processes":[{"key":"leave_approval","name":"审批","form_code":"leave_apply","nodes":[
                {"id":"n1","name":"经理","form_code":"leave_approve","assignee":{"type":"ROLE","ref":"approver"}},
                {"id":"n2","name":"人事","form_code":"leave_approve","assignee":{"type":"POSITION","ref":"hr_clerk"}}
              ]}],
              "role_pack":[{"code":"approver","name":"审批人","grants":[
                {"resource":"fn.leave.leave_apply","action":"read","scope":"SELF"},
                {"resource":"fn.leave.leave_apply","action":"write","scope":"SELF"}
              ]}],
              "entries":[
                {"key":"fn:leave:leave_apply","app_code":"leave","form_code":"leave_apply","ledger_code":"leave",
                 "process_key":"leave_approval","name":"请假申请","kind":"FILL"},
                {"key":"fn:leave:leave_list","app_code":"leave","form_code":"leave_list","ledger_code":"leave",
                 "process_key":null,"name":"请假列表","kind":"LIST"}
              ]
            }
            """;
}
