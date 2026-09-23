package one.nexus.p2.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityRepository {
    Model.GlobalAccount saveAccount(Model.GlobalAccount account);

    Optional<Model.GlobalAccount> findAccount(UUID id);

    Optional<Model.GlobalAccount> findAccountByKeycloakUserId(String keycloakUserId);

    Optional<Model.GlobalAccount> findAccountByEmail(String email);

    /** Platform-scoped. G10 identity reconcile only; not a tenant list. */
    List<Model.GlobalAccount> listAccounts();

    Model.TenantMembership saveMembership(Model.TenantMembership membership);

    Optional<Model.TenantMembership> findMembership(UUID tenantId, UUID accountId);

    List<Model.TenantMembership> listMembershipsByAccount(UUID accountId);

    List<Model.TenantMembership> listMembershipsByTenant(UUID tenantId);

    Model.Person savePerson(Model.Person person);

    Optional<Model.Person> findPerson(UUID tenantId, UUID personId);

    Optional<Model.Person> findPersonByAccount(UUID tenantId, UUID accountId);

    List<Model.Person> listPersons(UUID tenantId);

    /** Platform-scoped. G10 identity reconcile only; callers must not treat this as a tenant list. */
    List<Model.Person> listAllPersons();

    Optional<Model.Person> findPersonByEmployeeNo(UUID tenantId, String employeeNo);

    Model.Department saveDepartment(Model.Department department);

    Optional<Model.Department> findDepartment(UUID tenantId, UUID departmentId);

    Optional<Model.Department> findDepartmentByCode(UUID tenantId, String code);

    List<Model.Department> listDepartments(UUID tenantId);

    long countChildDepartments(UUID tenantId, UUID parentId);

    Model.Position savePosition(Model.Position position);

    Optional<Model.Position> findPosition(UUID tenantId, UUID positionId);

    Optional<Model.Position> findPositionByCode(UUID tenantId, String code);

    List<Model.Position> listPositions(UUID tenantId);

    Model.Employment saveEmployment(Model.Employment employment);

    Optional<Model.Employment> findEmployment(UUID tenantId, UUID employmentId);

    List<Model.Employment> listEmploymentsByPerson(UUID tenantId, UUID personId);

    List<Model.Employment> listEmployments(UUID tenantId);

    List<Model.Employment> listEmploymentsByDepartment(UUID tenantId, UUID departmentId);

    List<Model.Employment> listEmploymentsByPosition(UUID tenantId, UUID positionId);

    Optional<Model.Employment> findPrimaryEmployment(UUID tenantId, UUID personId);

    Model.IssuedContext saveContext(Model.IssuedContext context);

    Optional<Model.IssuedContext> findContext(UUID contextId);

    void revokeContextsForAccount(UUID accountId);
}
