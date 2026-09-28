package one.nexus.p4.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** PLX1 C2: collect manifest problems without resolving tenant-specific POSITION references. */
public final class AppManifestValidator {
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,23}");
    private static final Set<String> TYPES = Set.of("TEXT", "NUMERIC", "INTEGER", "BOOLEAN", "TIMESTAMPTZ");
    private static final Set<String> PURPOSES = Set.of("FILL", "APPROVE", "VIEW");
    private static final Set<String> KINDS = Set.of("FILL", "LIST", "VIEW");
    private static final Set<String> SCOPES = Set.of("SELF", "DEPT", "DEPT_AND_CHILDREN", "ALL");
    private static final Set<String> SYSTEM_FIELDS = Set.of("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "version");

    public record Problem(String path, String reason) {}

    public List<Problem> validate(Map<String, Object> manifest) {
        List<Problem> problems = new ArrayList<>();
        if (manifest == null) return List.of(new Problem("", "required"));
        if (!"nexus.app-manifest.v1".equals(manifest.get("schema"))) add(problems, "/schema", "invalid_schema");
        String appCode = code(manifest, "app_code", "", problems);
        code(manifest, "category_code", "", problems);
        text(manifest, "name", "", problems);
        text(manifest, "version", "", problems);

        Map<String, Set<String>> ledgerFields = new HashMap<>();
        Set<String> ledgerCodes = new HashSet<>();
        List<Map<String, Object>> ledgers = rows(manifest, "ledgers", "", problems);
        for (int i = 0; i < ledgers.size(); i++) {
            String path = "/ledgers/" + i;
            Map<String, Object> ledger = ledgers.get(i);
            String key = code(ledger, "code", path, problems);
            if (key != null && !ledgerCodes.add(key)) add(problems, path + "/code", "duplicate_code");
            text(ledger, "name", path, problems);
            Set<String> fields = new HashSet<>();
            List<Map<String, Object>> items = rows(ledger, "fields", path, problems);
            for (int j = 0; j < items.size(); j++) {
                String fieldPath = path + "/fields/" + j;
                Map<String, Object> field = items.get(j);
                String fieldCode = code(field, "code", fieldPath, problems);
                if (fieldCode != null && (!fields.add(fieldCode) || SYSTEM_FIELDS.contains(fieldCode)))
                    add(problems, fieldPath + "/code", "duplicate_or_reserved_code");
                text(field, "name", fieldPath, problems);
                if (!contains(TYPES, field.get("type"))) add(problems, fieldPath + "/type", "invalid_type");
            }
            if (key != null) ledgerFields.put(key, fields);
        }

        Map<String, Map<String, Object>> forms = new HashMap<>();
        List<Map<String, Object>> formRows = rows(manifest, "forms", "", problems);
        for (int i = 0; i < formRows.size(); i++) {
            String path = "/forms/" + i;
            Map<String, Object> form = formRows.get(i);
            String key = code(form, "code", path, problems);
            if (key != null && forms.putIfAbsent(key, form) != null) add(problems, path + "/code", "duplicate_code");
            String ledger = code(form, "ledger_code", path, problems);
            if (ledger != null && !ledgerFields.containsKey(ledger)) add(problems, path + "/ledger_code", "unknown_ref");
            if (!contains(PURPOSES, form.get("purpose"))) add(problems, path + "/purpose", "invalid_purpose");
            Set<String> fields = ledgerFields.getOrDefault(ledger, Set.of());
            List<Object> readOnly = values(form, "readonly_fields", path, problems);
            for (int j = 0; j < readOnly.size(); j++)
                if (!fields.contains(readOnly.get(j))) add(problems, path + "/readonly_fields/" + j, "unknown_ref");
            Object layoutObject = form.get("layout");
            if (!(layoutObject instanceof Map<?, ?> layout)) {
                add(problems, path + "/layout", "invalid_layout");
            } else {
                List<Map<String, Object>> sections = rows(cast(layout), "sections", path + "/layout", problems);
                for (int j = 0; j < sections.size(); j++) {
                    List<Object> used = values(sections.get(j), "fields", path + "/layout/sections/" + j, problems);
                    for (int k = 0; k < used.size(); k++)
                        if (!fields.contains(used.get(k))) add(problems, path + "/layout/sections/" + j + "/fields/" + k, "unknown_ref");
                }
            }
        }

        Set<String> roleCodes = new HashSet<>();
        List<Map<String, Object>> roles = rows(manifest, "role_pack", "", problems);
        for (int i = 0; i < roles.size(); i++) {
            String path = "/role_pack/" + i;
            String key = code(roles.get(i), "code", path, problems);
            if (key != null && !roleCodes.add(key)) add(problems, path + "/code", "duplicate_code");
            text(roles.get(i), "name", path, problems);
        }

        Map<String, Map<String, Object>> processes = new HashMap<>();
        List<Map<String, Object>> processRows = rows(manifest, "processes", "", problems);
        for (int i = 0; i < processRows.size(); i++) {
            String path = "/processes/" + i;
            Map<String, Object> process = processRows.get(i);
            String key = code(process, "key", path, problems);
            if (key != null && processes.putIfAbsent(key, process) != null) add(problems, path + "/key", "duplicate_code");
            String form = code(process, "form_code", path, problems);
            if (form != null && !forms.containsKey(form)) add(problems, path + "/form_code", "unknown_ref");
            else if (form != null && !"FILL".equals(forms.get(form).get("purpose")))
                add(problems, path + "/form_code", "wrong_purpose_ref");
            List<Map<String, Object>> nodes = rows(process, "nodes", path, problems);
            Set<String> nodeIds = new HashSet<>();
            for (int j = 0; j < nodes.size(); j++) {
                String nodePath = path + "/nodes/" + j;
                Map<String, Object> node = nodes.get(j);
                String id = code(node, "id", nodePath, problems);
                if (id != null && !nodeIds.add(id)) add(problems, nodePath + "/id", "duplicate_code");
                String nodeForm = optionalCode(node, "form_code", nodePath, problems);
                if (nodeForm != null) {
                    Map<String, Object> referenced = forms.get(nodeForm);
                    Map<String, Object> parent = forms.get(form);
                    if (referenced == null || parent == null || !"APPROVE".equals(referenced.get("purpose"))
                            || !java.util.Objects.equals(referenced.get("ledger_code"), parent.get("ledger_code")))
                        add(problems, nodePath + "/form_code", "unknown_or_wrong_purpose_ref");
                }
                Object assigneeObject = node.get("assignee");
                if (!(assigneeObject instanceof Map<?, ?> assignee)) {
                    add(problems, nodePath + "/assignee", "required");
                } else {
                    Map<String, Object> target = cast(assignee);
                    String ref = code(target, "ref", nodePath + "/assignee", problems);
                    if ("ROLE".equals(target.get("type"))) {
                        if (ref != null && !roleCodes.contains(ref)) add(problems, nodePath + "/assignee/ref", "unknown_ref");
                    } else if (!"POSITION".equals(target.get("type"))) {
                        add(problems, nodePath + "/assignee/type", "invalid_assignee_type");
                    }
                }
            }
        }

        Set<String> entryKeys = new HashSet<>();
        Map<String, String> resources = new HashMap<>();
        List<Map<String, Object>> entries = rows(manifest, "entries", "", problems);
        for (int i = 0; i < entries.size(); i++) {
            String path = "/entries/" + i;
            Map<String, Object> entry = entries.get(i);
            String key = text(entry, "key", path, problems);
            if (key != null && !entryKeys.add(key)) add(problems, path + "/key", "duplicate_code");
            String entryApp = code(entry, "app_code", path, problems);
            if (entryApp != null && !entryApp.equals(appCode)) add(problems, path + "/app_code", "app_code_mismatch");
            String formCode = code(entry, "form_code", path, problems);
            if (key != null && appCode != null && formCode != null && !key.equals("fn:" + appCode + ":" + formCode))
                add(problems, path + "/key", "invalid_entry_key");
            Map<String, Object> form = forms.get(formCode);
            if (form == null) add(problems, path + "/form_code", "unknown_ref");
            String ledgerCode = code(entry, "ledger_code", path, problems);
            if (ledgerCode != null && !ledgerFields.containsKey(ledgerCode)) add(problems, path + "/ledger_code", "unknown_ref");
            if (form != null && !java.util.Objects.equals(form.get("ledger_code"), ledgerCode)) add(problems, path + "/ledger_code", "unknown_ref");
            String processCode = optionalCode(entry, "process_key", path, problems);
            if (processCode != null && !processes.containsKey(processCode)) add(problems, path + "/process_key", "unknown_ref");
            String kind = text(entry, "kind", path, problems);
            if (kind != null && !KINDS.contains(kind)) add(problems, path + "/kind", "invalid_kind");
            if (form != null && kind != null && !java.util.Objects.equals(
                    "FILL".equals(kind) ? "FILL" : "VIEW", form.get("purpose")))
                add(problems, path + "/kind", "kind_purpose_mismatch");
            if (processCode != null && processes.containsKey(processCode) && (!"FILL".equals(kind)
                    || !java.util.Objects.equals(processes.get(processCode).get("form_code"), formCode)))
                add(problems, path + "/process_key", "process_form_mismatch");
            if (formCode != null && appCode != null) resources.put("fn." + appCode + "." + formCode, kind);
        }

        for (int i = 0; i < roles.size(); i++) {
            String path = "/role_pack/" + i;
            List<Map<String, Object>> grants = rows(roles.get(i), "grants", path, problems);
            for (int j = 0; j < grants.size(); j++) {
                String grantPath = path + "/grants/" + j;
                Map<String, Object> grant = grants.get(j);
                String resource = text(grant, "resource", grantPath, problems);
                if (resource != null && !resources.containsKey(resource)) add(problems, grantPath + "/resource", "unknown_ref");
                if (!contains(Set.of("read", "write"), grant.get("action"))) add(problems, grantPath + "/action", "invalid_action");
                if ("write".equals(grant.get("action")) && !"FILL".equals(resources.get(resource)))
                    add(problems, grantPath + "/action", "write_requires_fill");
                if (!contains(SCOPES, grant.get("scope"))) add(problems, grantPath + "/scope", "invalid_scope");
            }
        }
        return List.copyOf(problems);
    }

    private static void add(List<Problem> out, String path, String reason) { out.add(new Problem(path, reason)); }
    private static boolean contains(Set<String> choices, Object value) { return value instanceof String text && choices.contains(text); }
    private static String text(Map<String, Object> row, String key, String path, List<Problem> out) {
        Object value = row.get(key);
        if (!(value instanceof String string) || string.isBlank()) { add(out, path + "/" + key, "required"); return null; }
        return string;
    }
    private static String code(Map<String, Object> row, String key, String path, List<Problem> out) {
        String value = text(row, key, path, out);
        if (value != null && !CODE.matcher(value).matches()) add(out, path + "/" + key, "invalid_code");
        return value;
    }
    private static String optionalCode(Map<String, Object> row, String key, String path, List<Problem> out) {
        Object value = row.get(key);
        if (value == null) return null;
        if (!(value instanceof String string) || !CODE.matcher(string).matches()) { add(out, path + "/" + key, "invalid_code"); return null; }
        return string;
    }
    private static List<Object> values(Map<String, Object> row, String key, String path, List<Problem> out) {
        Object value = row.get(key);
        if (!(value instanceof List<?> list)) { add(out, path + "/" + key, "required_array"); return List.of(); }
        return new ArrayList<>(list);
    }
    private static List<Map<String, Object>> rows(Map<String, Object> row, String key, String path, List<Problem> out) {
        List<Object> values = values(row, key, path, out);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) instanceof Map<?, ?> map) result.add(cast(map));
            else { add(out, path + "/" + key + "/" + i, "required_object"); result.add(Map.of()); }
        }
        return result;
    }
    private static Map<String, Object> cast(Map<?, ?> source) {
        Map<String, Object> result = new HashMap<>();
        source.forEach((key, value) -> { if (key instanceof String string) result.put(string, value); });
        return result;
    }
}
