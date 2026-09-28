package one.nexus.permission.persist;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.engine.FieldRule;
import one.nexus.permission.engine.Mask;
import one.nexus.permission.engine.PolicySnapshot;
import one.nexus.permission.engine.Predicate;
import one.nexus.common.id.UuidV7;
import one.nexus.permission.engine.Rule;
import one.nexus.permission.support.PermissionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public final class JdbcPolicyRepository implements PolicyRepository {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcPolicyRepository(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    public JdbcPolicyRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public RoleRecord saveRole(RoleRecord role) {
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO iam.perm_role (id, tenant_id, code, name, system_role, status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, status = EXCLUDED.status, updated_at = EXCLUDED.updated_at
                """,
                role.id(), role.tenantId(), role.code(), role.name(), role.systemRole(), role.status(), ts(now), ts(now));
        ensureVersion(role.tenantId());
        return role;
    }

    @Override
    public Optional<RoleRecord> findRole(UUID tenantId, UUID roleId) {
        List<RoleRecord> rows = jdbc.query(
                "SELECT id, tenant_id, code, name, system_role, status FROM iam.perm_role WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL",
                roleMapper(), tenantId, roleId);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<RoleRecord> findRoleByCode(UUID tenantId, String code) {
        List<RoleRecord> rows = jdbc.query(
                "SELECT id, tenant_id, code, name, system_role, status FROM iam.perm_role WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL",
                roleMapper(), tenantId, code);
        return rows.stream().findFirst();
    }

    @Override
    public List<RoleRecord> listRoles(UUID tenantId) {
        return jdbc.query(
                "SELECT id, tenant_id, code, name, system_role, status FROM iam.perm_role WHERE tenant_id = ? AND deleted_at IS NULL",
                roleMapper(), tenantId);
    }

    @Override
    public void addRoleParent(UUID tenantId, UUID childRoleId, UUID parentRoleId) {
        findRole(tenantId, childRoleId).orElseThrow(() -> PermissionException.notFound("child role"));
        findRole(tenantId, parentRoleId).orElseThrow(() -> PermissionException.notFound("parent role"));
        jdbc.update(
                """
                INSERT INTO iam.perm_role_parent (tenant_id, child_role_id, parent_role_id)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                tenantId, childRoleId, parentRoleId);
    }

    @Override
    public AssignmentRecord saveAssignment(AssignmentRecord assignment) {
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO iam.perm_assignment (id, tenant_id, principal_type, principal_id, role_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                assignment.id(), assignment.tenantId(), assignment.principalType(), assignment.principalId(),
                assignment.roleId(), ts(now));
        return assignment;
    }

    @Override
    public boolean deleteAssignment(UUID tenantId, UUID assignmentId) {
        return jdbc.update(
                "UPDATE iam.perm_assignment SET deleted_at = now() WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL",
                tenantId, assignmentId) > 0;
    }

    @Override
    public List<AssignmentRecord> listAssignments(UUID tenantId) {
        return jdbc.query(
                """
                SELECT a.id, a.tenant_id, a.principal_type, a.principal_id, a.role_id, r.code
                FROM iam.perm_assignment a
                JOIN iam.perm_role r ON r.id = a.role_id AND r.tenant_id = a.tenant_id
                WHERE a.tenant_id = ? AND a.deleted_at IS NULL
                """,
                (rs, i) -> new AssignmentRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("principal_type"),
                        rs.getString("principal_id"),
                        rs.getObject("role_id", UUID.class),
                        rs.getString("code")),
                tenantId);
    }

    @Override
    public Set<String> roleCodesFor(UUID tenantId, String userId, Set<String> positionIds, Set<String> departmentIds) {
        Set<String> codes = new LinkedHashSet<>();
        for (AssignmentRecord item : listAssignments(tenantId)) {
            boolean match = switch (item.principalType()) {
                case "user" -> item.principalId().equals(userId);
                case "position" -> positionIds != null && positionIds.contains(item.principalId());
                case "department" -> departmentIds != null && departmentIds.contains(item.principalId());
                default -> false;
            };
            if (match) {
                codes.add(item.roleCode());
            }
        }
        return codes;
    }

    @Override
    public GrantRecord saveGrant(GrantRecord grant) {
        Instant now = clock.instant();
        Rule rule = grant.rule();
        jdbc.update(
                """
                INSERT INTO iam.perm_grant
                    (id, tenant_id, effect, principal_type, principal_id, resource, action, scope_kind, specified_depts, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                grant.id(), grant.tenantId(), grant.effect(), rule.principalType(), rule.principalId(),
                rule.resource(), rule.action(), rule.scopeKind().name(),
                rule.specifiedDepartmentIds().isEmpty() ? null : String.join(",", rule.specifiedDepartmentIds()),
                ts(now));
        rule.fields().forEach((name, field) -> jdbc.update(
                """
                INSERT INTO iam.perm_field_policy
                    (id, tenant_id, grant_id, field_name, visible, editable, mask, second_authorization)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UuidV7.generate(), grant.tenantId(), grant.id(), name, field.visible(), field.editable(),
                field.mask().name(), field.secondAuthorization()));
        return grant;
    }

    @Override
    public boolean deleteGrant(UUID tenantId, UUID grantId) {
        return jdbc.update(
                "UPDATE iam.perm_grant SET deleted_at = now() WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL",
                tenantId, grantId) > 0;
    }

    @Override
    public List<GrantRecord> listGrants(UUID tenantId) {
        List<GrantRecord> out = new ArrayList<>();
        jdbc.query(
                """
                SELECT id, tenant_id, effect, principal_type, principal_id, resource, action, scope_kind, specified_depts
                FROM iam.perm_grant WHERE tenant_id = ? AND deleted_at IS NULL
                """,
                (rs) -> {
                    UUID grantId = rs.getObject("id", UUID.class);
                    DataScopeKind kind = DataScopeKind.valueOf(rs.getString("scope_kind"));
                    List<String> specified = split(rs.getString("specified_depts"));
                    Map<String, FieldRule> fields = loadFields(tenantId, grantId);
                    Rule rule = new Rule(
                            rs.getString("principal_type"),
                            rs.getString("principal_id"),
                            rs.getString("resource"),
                            rs.getString("action"),
                            kind,
                            Predicate.TRUE,
                            specified,
                            fields);
                    out.add(new GrantRecord(grantId, tenantId, rs.getString("effect"), rule));
                },
                tenantId);
        return out;
    }

    @Override
    public ShareRecord saveShare(ShareRecord share) {
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO iam.perm_record_share
                    (id, tenant_id, resource, record_id, principal_type, principal_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                share.id(), share.tenantId(), share.resource(), share.recordId(),
                share.principalType(), share.principalId(), ts(now));
        return share;
    }

    @Override
    public List<Predicate> recordShares(UUID tenantId, String resource, Set<String> principalKeys) {
        List<String> ids = jdbc.query(
                """
                SELECT record_id, principal_type, principal_id
                FROM iam.perm_record_share
                WHERE tenant_id = ? AND resource = ? AND deleted_at IS NULL
                  AND (expires_at IS NULL OR expires_at > now())
                """,
                (rs, i) -> {
                    String key = rs.getString("principal_type") + ":" + rs.getString("principal_id");
                    return principalKeys.contains(key) ? rs.getString("record_id") : null;
                },
                tenantId, resource).stream().filter(item -> item != null).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return List.of(Predicate.atom("id", "IN", ids));
    }

    @Override
    public long currentVersion(UUID tenantId) {
        Long version = jdbc.query(
                "SELECT policy_version FROM iam.perm_policy_version WHERE tenant_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                tenantId);
        return version == null ? 1L : version;
    }

    @Override
    public long bumpVersion(UUID tenantId) {
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO iam.perm_policy_version (tenant_id, policy_version, updated_at)
                VALUES (?, 1, ?)
                ON CONFLICT (tenant_id) DO UPDATE
                    SET policy_version = iam.perm_policy_version.policy_version + 1,
                        updated_at = EXCLUDED.updated_at
                """,
                tenantId, ts(now));
        return currentVersion(tenantId);
    }

    @Override
    public PolicySnapshot loadSnapshot(UUID tenantId) {
        List<Rule> grantRules = new ArrayList<>();
        List<Rule> denyRules = new ArrayList<>();
        for (GrantRecord grant : listGrants(tenantId)) {
            if ("DENY".equalsIgnoreCase(grant.effect())) {
                denyRules.add(grant.rule());
            } else {
                grantRules.add(grant.rule());
            }
        }
        Map<String, Set<String>> parents = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT c.code AS child_code, p.code AS parent_code
                FROM iam.perm_role_parent x
                JOIN iam.perm_role c ON c.id = x.child_role_id AND c.tenant_id = x.tenant_id
                JOIN iam.perm_role p ON p.id = x.parent_role_id AND p.tenant_id = x.tenant_id
                WHERE x.tenant_id = ?
                """,
                (rs) -> {
                    parents.computeIfAbsent(rs.getString("child_code"), ignored -> new LinkedHashSet<>())
                            .add(rs.getString("parent_code"));
                },
                tenantId);
        return new PolicySnapshot(
                tenantId.toString(), currentVersion(tenantId), grantRules, denyRules, parents, Instant.now());
    }

    private void ensureVersion(UUID tenantId) {
        jdbc.update(
                """
                INSERT INTO iam.perm_policy_version (tenant_id, policy_version, updated_at)
                VALUES (?, 1, ?)
                ON CONFLICT (tenant_id) DO NOTHING
                """,
                tenantId, ts(clock.instant()));
    }

    private Map<String, FieldRule> loadFields(UUID tenantId, UUID grantId) {
        Map<String, FieldRule> fields = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT field_name, visible, editable, mask, second_authorization
                FROM iam.perm_field_policy WHERE tenant_id = ? AND grant_id = ?
                """,
                (rs) -> {
                    fields.put(rs.getString("field_name"), new FieldRule(
                            rs.getBoolean("visible"),
                            rs.getBoolean("editable"),
                            Mask.valueOf(rs.getString("mask")),
                            rs.getBoolean("second_authorization")));
                },
                tenantId, grantId);
        return fields;
    }

    private static RowMapper<RoleRecord> roleMapper() {
        return (rs, i) -> new RoleRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getBoolean("system_role"),
                rs.getString("status"));
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }
}
