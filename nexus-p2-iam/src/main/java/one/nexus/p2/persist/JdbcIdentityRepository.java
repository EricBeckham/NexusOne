package one.nexus.p2.persist;

import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Parameterized JDBC persistence. Every tenant-scoped query includes tenant_id.
 * This module does not implement Query AST / SQL Guard (M0-02).
 */
public final class JdbcIdentityRepository implements IdentityRepository {
    private final JdbcTemplate jdbc;

    public JdbcIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Model.GlobalAccount saveAccount(Model.GlobalAccount account) {
        int updated = jdbc.update("""
                INSERT INTO iam_global_account
                  (id, keycloak_user_id, email, phone, username, display_name, status, created_at, updated_at, deleted_at, version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET
                  keycloak_user_id = EXCLUDED.keycloak_user_id,
                  email = EXCLUDED.email,
                  phone = EXCLUDED.phone,
                  username = EXCLUDED.username,
                  display_name = EXCLUDED.display_name,
                  status = EXCLUDED.status,
                  updated_at = EXCLUDED.updated_at,
                  deleted_at = EXCLUDED.deleted_at,
                  version = EXCLUDED.version
                """,
                account.id(), account.keycloakUserId(), account.email(), account.phone(),
                account.username(), account.displayName(), account.status().name(),
                ts(account.createdAt()), ts(account.updatedAt()), ts(account.deletedAt()), account.version());
        if (updated == 0) {
            throw new IllegalStateException("failed to persist global account");
        }
        return account;
    }

    @Override
    public Optional<Model.GlobalAccount> findAccount(UUID id) {
        return one("SELECT * FROM iam_global_account WHERE id = ? AND deleted_at IS NULL", ACCOUNT, id);
    }

    @Override
    public Optional<Model.GlobalAccount> findAccountByKeycloakUserId(String keycloakUserId) {
        return one("SELECT * FROM iam_global_account WHERE keycloak_user_id = ? AND deleted_at IS NULL", ACCOUNT, keycloakUserId);
    }

    @Override
    public Optional<Model.GlobalAccount> findAccountByEmail(String email) {
        return one("SELECT * FROM iam_global_account WHERE lower(email) = lower(?) AND deleted_at IS NULL", ACCOUNT, email);
    }

    @Override
    public Model.TenantMembership saveMembership(Model.TenantMembership membership) {
        jdbc.update("""
                INSERT INTO iam_tenant_membership
                  (id, tenant_id, account_id, status, joined_at, updated_at, deleted_at, version)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET
                  status = EXCLUDED.status,
                  updated_at = EXCLUDED.updated_at,
                  deleted_at = EXCLUDED.deleted_at,
                  version = EXCLUDED.version
                """,
                membership.id(), membership.tenantId(), membership.accountId(), membership.status().name(),
                ts(membership.joinedAt()), ts(membership.updatedAt()), ts(membership.deletedAt()), membership.version());
        return membership;
    }

    @Override
    public Optional<Model.TenantMembership> findMembership(UUID tenantId, UUID accountId) {
        return one("""
                SELECT * FROM iam_tenant_membership
                WHERE tenant_id = ? AND account_id = ? AND deleted_at IS NULL
                """, MEMBERSHIP, tenantId, accountId);
    }

    @Override
    public List<Model.TenantMembership> listMembershipsByAccount(UUID accountId) {
        return jdbc.query(
                "SELECT * FROM iam_tenant_membership WHERE account_id = ? AND deleted_at IS NULL",
                MEMBERSHIP, accountId);
    }

    @Override
    public List<Model.TenantMembership> listMembershipsByTenant(UUID tenantId) {
        return jdbc.query(
                "SELECT * FROM iam_tenant_membership WHERE tenant_id = ? AND deleted_at IS NULL",
                MEMBERSHIP, tenantId);
    }

    @Override
    public Model.Person savePerson(Model.Person person) {
        jdbc.update("""
                INSERT INTO iam_person
                  (id, tenant_id, account_id, display_name, employee_no, email, phone, hired_on, status, created_at, updated_at, deleted_at, version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET
                  display_name = EXCLUDED.display_name,
                  employee_no = EXCLUDED.employee_no,
                  email = EXCLUDED.email,
                  phone = EXCLUDED.phone,
                  hired_on = EXCLUDED.hired_on,
                  status = EXCLUDED.status,
                  updated_at = EXCLUDED.updated_at,
                  deleted_at = EXCLUDED.deleted_at,
                  version = EXCLUDED.version
                """,
                person.id(), person.tenantId(), person.accountId(), person.displayName(), person.employeeNo(),
                person.email(), person.phone(), date(person.hiredOn()), person.status().name(),
                ts(person.createdAt()), ts(person.updatedAt()), ts(person.deletedAt()), person.version());
        return person;
    }

    @Override
    public Optional<Model.Person> findPerson(UUID tenantId, UUID personId) {
        return one("SELECT * FROM iam_person WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL", PERSON, tenantId, personId);
    }

    @Override
    public Optional<Model.Person> findPersonByAccount(UUID tenantId, UUID accountId) {
        return one("SELECT * FROM iam_person WHERE tenant_id = ? AND account_id = ? AND deleted_at IS NULL", PERSON, tenantId, accountId);
    }

    @Override
    public List<Model.Person> listPersons(UUID tenantId) {
        return jdbc.query("SELECT * FROM iam_person WHERE tenant_id = ? AND deleted_at IS NULL", PERSON, tenantId);
    }

    @Override
    public Optional<Model.Person> findPersonByEmployeeNo(UUID tenantId, String employeeNo) {
        return one("""
                SELECT * FROM iam_person
                WHERE tenant_id = ? AND employee_no = ? AND deleted_at IS NULL
                """, PERSON, tenantId, employeeNo);
    }

    @Override
    public Model.Department saveDepartment(Model.Department department) {
        jdbc.update("""
                INSERT INTO iam_department
                  (id, tenant_id, parent_id, code, name, path, sort_order, leader_person_id, status, created_at, updated_at, deleted_at, version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET
                  parent_id = EXCLUDED.parent_id,
                  code = EXCLUDED.code,
                  name = EXCLUDED.name,
                  path = EXCLUDED.path,
                  sort_order = EXCLUDED.sort_order,
                  leader_person_id = EXCLUDED.leader_person_id,
                  status = EXCLUDED.status,
                  updated_at = EXCLUDED.updated_at,
                  deleted_at = EXCLUDED.deleted_at,
                  version = EXCLUDED.version
                """,
                department.id(), department.tenantId(), department.parentId(), department.code(), department.name(),
                department.path(), department.sortOrder(), department.leaderPersonId(), department.status().name(),
                ts(department.createdAt()), ts(department.updatedAt()), ts(department.deletedAt()), department.version());
        return department;
    }

    @Override
    public Optional<Model.Department> findDepartment(UUID tenantId, UUID departmentId) {
        return one("SELECT * FROM iam_department WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL", DEPARTMENT, tenantId, departmentId);
    }

    @Override
    public Optional<Model.Department> findDepartmentByCode(UUID tenantId, String code) {
        return one("SELECT * FROM iam_department WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL", DEPARTMENT, tenantId, code);
    }

    @Override
    public List<Model.Department> listDepartments(UUID tenantId) {
        return jdbc.query("SELECT * FROM iam_department WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY sort_order, name", DEPARTMENT, tenantId);
    }

    @Override
    public long countChildDepartments(UUID tenantId, UUID parentId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam_department WHERE tenant_id = ? AND parent_id = ? AND deleted_at IS NULL",
                Long.class, tenantId, parentId);
        return count == null ? 0 : count;
    }

    @Override
    public Model.Position savePosition(Model.Position position) {
        jdbc.update("""
                INSERT INTO iam_position
                  (id, tenant_id, code, name, category, duties, status, created_at, updated_at, deleted_at, version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET
                  code = EXCLUDED.code,
                  name = EXCLUDED.name,
                  category = EXCLUDED.category,
                  duties = EXCLUDED.duties,
                  status = EXCLUDED.status,
                  updated_at = EXCLUDED.updated_at,
                  deleted_at = EXCLUDED.deleted_at,
                  version = EXCLUDED.version
                """,
                position.id(), position.tenantId(), position.code(), position.name(), position.category(),
                position.duties(), position.status().name(), ts(position.createdAt()), ts(position.updatedAt()),
                ts(position.deletedAt()), position.version());
        return position;
    }

    @Override
    public Optional<Model.Position> findPosition(UUID tenantId, UUID positionId) {
        return one("SELECT * FROM iam_position WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL", POSITION, tenantId, positionId);
    }

    @Override
    public Optional<Model.Position> findPositionByCode(UUID tenantId, String code) {
        return one("SELECT * FROM iam_position WHERE tenant_id = ? AND code = ? AND deleted_at IS NULL", POSITION, tenantId, code);
    }

    @Override
    public List<Model.Position> listPositions(UUID tenantId) {
        return jdbc.query("SELECT * FROM iam_position WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY name", POSITION, tenantId);
    }

    @Override
    public Model.Employment saveEmployment(Model.Employment employment) {
        jdbc.update("""
                INSERT INTO iam_employment
                  (id, tenant_id, person_id, department_id, position_id, kind, status, valid_from, valid_to, created_at, updated_at, deleted_at, version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET
                  department_id = EXCLUDED.department_id,
                  position_id = EXCLUDED.position_id,
                  kind = EXCLUDED.kind,
                  status = EXCLUDED.status,
                  valid_from = EXCLUDED.valid_from,
                  valid_to = EXCLUDED.valid_to,
                  updated_at = EXCLUDED.updated_at,
                  deleted_at = EXCLUDED.deleted_at,
                  version = EXCLUDED.version
                """,
                employment.id(), employment.tenantId(), employment.personId(), employment.departmentId(),
                employment.positionId(), employment.kind().name(), employment.status().name(),
                date(employment.validFrom()), date(employment.validTo()),
                ts(employment.createdAt()), ts(employment.updatedAt()), ts(employment.deletedAt()), employment.version());
        return employment;
    }

    @Override
    public Optional<Model.Employment> findEmployment(UUID tenantId, UUID employmentId) {
        return one("SELECT * FROM iam_employment WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL", EMPLOYMENT, tenantId, employmentId);
    }

    @Override
    public List<Model.Employment> listEmploymentsByPerson(UUID tenantId, UUID personId) {
        return jdbc.query(
                "SELECT * FROM iam_employment WHERE tenant_id = ? AND person_id = ? AND deleted_at IS NULL",
                EMPLOYMENT, tenantId, personId);
    }

    @Override
    public List<Model.Employment> listEmploymentsByDepartment(UUID tenantId, UUID departmentId) {
        return jdbc.query("""
                SELECT * FROM iam_employment
                WHERE tenant_id = ? AND department_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """, EMPLOYMENT, tenantId, departmentId);
    }

    @Override
    public Optional<Model.Employment> findPrimaryEmployment(UUID tenantId, UUID personId) {
        return one("""
                SELECT * FROM iam_employment
                WHERE tenant_id = ? AND person_id = ? AND kind = 'PRIMARY' AND status = 'ACTIVE' AND deleted_at IS NULL
                """, EMPLOYMENT, tenantId, personId);
    }

    @Override
    public Model.IssuedContext saveContext(Model.IssuedContext context) {
        jdbc.update("""
                INSERT INTO iam_issued_context
                  (id, tenant_id, account_id, person_id, idp_subject, issued_at, expires_at, revoked_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET revoked_at = EXCLUDED.revoked_at
                """,
                context.id(), context.tenantId(), context.accountId(), context.personId(), context.idpSubject(),
                ts(context.issuedAt()), ts(context.expiresAt()), ts(context.revokedAt()));
        return context;
    }

    @Override
    public Optional<Model.IssuedContext> findContext(UUID contextId) {
        return one("SELECT * FROM iam_issued_context WHERE id = ?", CONTEXT, contextId);
    }

    @Override
    public void revokeContextsForAccount(UUID accountId) {
        jdbc.update(
                "UPDATE iam_issued_context SET revoked_at = ? WHERE account_id = ? AND revoked_at IS NULL",
                ts(Instant.now()), accountId);
    }

    private <T> Optional<T> one(String sql, RowMapper<T> mapper, Object... args) {
        List<T> rows = jdbc.query(sql, mapper, args);
        return rows.stream().findFirst();
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Date date(java.time.LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static final RowMapper<Model.GlobalAccount> ACCOUNT = (rs, row) -> new Model.GlobalAccount(
            uuid(rs, "id"), rs.getString("keycloak_user_id"), rs.getString("email"), rs.getString("phone"),
            rs.getString("username"), rs.getString("display_name"),
            Model.AccountStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "deleted_at"), rs.getInt("version"));

    private static final RowMapper<Model.TenantMembership> MEMBERSHIP = (rs, row) -> new Model.TenantMembership(
            uuid(rs, "id"), uuid(rs, "tenant_id"), uuid(rs, "account_id"),
            Model.MembershipStatus.valueOf(rs.getString("status")),
            instant(rs, "joined_at"), instant(rs, "updated_at"), instant(rs, "deleted_at"), rs.getInt("version"));

    private static final RowMapper<Model.Person> PERSON = (rs, row) -> new Model.Person(
            uuid(rs, "id"), uuid(rs, "tenant_id"), uuid(rs, "account_id"), rs.getString("display_name"),
            rs.getString("employee_no"), rs.getString("email"), rs.getString("phone"),
            localDate(rs, "hired_on"), Model.PersonStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "deleted_at"), rs.getInt("version"));

    private static final RowMapper<Model.Department> DEPARTMENT = (rs, row) -> new Model.Department(
            uuid(rs, "id"), uuid(rs, "tenant_id"), uuid(rs, "parent_id"), rs.getString("code"), rs.getString("name"),
            rs.getString("path"), rs.getInt("sort_order"), uuid(rs, "leader_person_id"),
            Model.OrgStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "deleted_at"), rs.getInt("version"));

    private static final RowMapper<Model.Position> POSITION = (rs, row) -> new Model.Position(
            uuid(rs, "id"), uuid(rs, "tenant_id"), rs.getString("code"), rs.getString("name"),
            rs.getString("category"), rs.getString("duties"),
            Model.OrgStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "deleted_at"), rs.getInt("version"));

    private static final RowMapper<Model.Employment> EMPLOYMENT = (rs, row) -> new Model.Employment(
            uuid(rs, "id"), uuid(rs, "tenant_id"), uuid(rs, "person_id"), uuid(rs, "department_id"),
            uuid(rs, "position_id"), Model.EmploymentKind.valueOf(rs.getString("kind")),
            Model.EmploymentStatus.valueOf(rs.getString("status")),
            localDate(rs, "valid_from"), localDate(rs, "valid_to"),
            instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "deleted_at"), rs.getInt("version"));

    private static final RowMapper<Model.IssuedContext> CONTEXT = (rs, row) -> new Model.IssuedContext(
            uuid(rs, "id"), uuid(rs, "tenant_id"), uuid(rs, "account_id"), uuid(rs, "person_id"),
            rs.getString("idp_subject"), instant(rs, "issued_at"), instant(rs, "expires_at"), instant(rs, "revoked_at"));

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static java.time.LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
