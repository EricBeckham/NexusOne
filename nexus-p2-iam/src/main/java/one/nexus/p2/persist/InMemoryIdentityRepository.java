package one.nexus.p2.persist;

import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryIdentityRepository implements IdentityRepository {
    private final Map<UUID, Model.GlobalAccount> accounts = new ConcurrentHashMap<>();
    private final Map<UUID, Model.TenantMembership> memberships = new ConcurrentHashMap<>();
    private final Map<UUID, Model.Person> persons = new ConcurrentHashMap<>();
    private final Map<UUID, Model.Department> departments = new ConcurrentHashMap<>();
    private final Map<UUID, Model.Position> positions = new ConcurrentHashMap<>();
    private final Map<UUID, Model.Employment> employments = new ConcurrentHashMap<>();
    private final Map<UUID, Model.IssuedContext> contexts = new ConcurrentHashMap<>();

    @Override
    public Model.GlobalAccount saveAccount(Model.GlobalAccount account) {
        accounts.put(account.id(), account);
        return account;
    }

    @Override
    public Optional<Model.GlobalAccount> findAccount(UUID id) {
        return Optional.ofNullable(accounts.get(id)).filter(item -> item.deletedAt() == null);
    }

    @Override
    public Optional<Model.GlobalAccount> findAccountByKeycloakUserId(String keycloakUserId) {
        return accounts.values().stream()
                .filter(item -> item.deletedAt() == null && item.keycloakUserId().equals(keycloakUserId))
                .findFirst();
    }

    @Override
    public Optional<Model.GlobalAccount> findAccountByEmail(String email) {
        return accounts.values().stream()
                .filter(item -> item.deletedAt() == null && item.email().equalsIgnoreCase(email))
                .findFirst();
    }

    @Override
    public List<Model.GlobalAccount> listAccounts() {
        return accounts.values().stream()
                .filter(item -> item.deletedAt() == null)
                .toList();
    }

    @Override
    public Model.TenantMembership saveMembership(Model.TenantMembership membership) {
        memberships.put(membership.id(), membership);
        return membership;
    }

    @Override
    public Optional<Model.TenantMembership> findMembership(UUID tenantId, UUID accountId) {
        return memberships.values().stream()
                .filter(item -> item.deletedAt() == null
                        && item.tenantId().equals(tenantId)
                        && item.accountId().equals(accountId))
                .findFirst();
    }

    @Override
    public List<Model.TenantMembership> listMembershipsByAccount(UUID accountId) {
        return memberships.values().stream()
                .filter(item -> item.deletedAt() == null && item.accountId().equals(accountId))
                .toList();
    }

    @Override
    public List<Model.TenantMembership> listMembershipsByTenant(UUID tenantId) {
        return memberships.values().stream()
                .filter(item -> item.deletedAt() == null && item.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public Model.Person savePerson(Model.Person person) {
        persons.put(person.id(), person);
        return person;
    }

    @Override
    public Optional<Model.Person> findPerson(UUID tenantId, UUID personId) {
        return Optional.ofNullable(persons.get(personId))
                .filter(item -> item.deletedAt() == null && item.tenantId().equals(tenantId));
    }

    @Override
    public Optional<Model.Person> findPersonByAccount(UUID tenantId, UUID accountId) {
        return persons.values().stream()
                .filter(item -> item.deletedAt() == null
                        && item.tenantId().equals(tenantId)
                        && item.accountId().equals(accountId))
                .findFirst();
    }

    @Override
    public List<Model.Person> listPersons(UUID tenantId) {
        return persons.values().stream()
                .filter(item -> item.deletedAt() == null && item.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public List<Model.Person> listAllPersons() {
        return persons.values().stream()
                .filter(item -> item.deletedAt() == null)
                .toList();
    }

    @Override
    public Optional<Model.Person> findPersonByEmployeeNo(UUID tenantId, String employeeNo) {
        if (employeeNo == null) {
            return Optional.empty();
        }
        return persons.values().stream()
                .filter(item -> item.deletedAt() == null
                        && item.tenantId().equals(tenantId)
                        && employeeNo.equals(item.employeeNo()))
                .findFirst();
    }

    @Override
    public Model.Department saveDepartment(Model.Department department) {
        departments.put(department.id(), department);
        return department;
    }

    @Override
    public Optional<Model.Department> findDepartment(UUID tenantId, UUID departmentId) {
        return Optional.ofNullable(departments.get(departmentId))
                .filter(item -> item.deletedAt() == null && item.tenantId().equals(tenantId));
    }

    @Override
    public Optional<Model.Department> findDepartmentByCode(UUID tenantId, String code) {
        return departments.values().stream()
                .filter(item -> item.deletedAt() == null
                        && item.tenantId().equals(tenantId)
                        && item.code().equals(code))
                .findFirst();
    }

    @Override
    public List<Model.Department> listDepartments(UUID tenantId) {
        return departments.values().stream()
                .filter(item -> item.deletedAt() == null && item.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public long countChildDepartments(UUID tenantId, UUID parentId) {
        return departments.values().stream()
                .filter(item -> item.deletedAt() == null
                        && item.tenantId().equals(tenantId)
                        && parentId.equals(item.parentId()))
                .count();
    }

    @Override
    public Model.Position savePosition(Model.Position position) {
        positions.put(position.id(), position);
        return position;
    }

    @Override
    public Optional<Model.Position> findPosition(UUID tenantId, UUID positionId) {
        return Optional.ofNullable(positions.get(positionId))
                .filter(item -> item.deletedAt() == null && item.tenantId().equals(tenantId));
    }

    @Override
    public Optional<Model.Position> findPositionByCode(UUID tenantId, String code) {
        return positions.values().stream()
                .filter(item -> item.deletedAt() == null
                        && item.tenantId().equals(tenantId)
                        && item.code().equals(code))
                .findFirst();
    }

    @Override
    public List<Model.Position> listPositions(UUID tenantId) {
        return positions.values().stream()
                .filter(item -> item.deletedAt() == null && item.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public Model.Employment saveEmployment(Model.Employment employment) {
        employments.put(employment.id(), employment);
        return employment;
    }

    @Override
    public Optional<Model.Employment> findEmployment(UUID tenantId, UUID employmentId) {
        return Optional.ofNullable(employments.get(employmentId))
                .filter(item -> item.deletedAt() == null && item.tenantId().equals(tenantId));
    }

    @Override
    public List<Model.Employment> listEmploymentsByPerson(UUID tenantId, UUID personId) {
        return employments.values().stream()
                .filter(item -> item.deletedAt() == null
                        && item.tenantId().equals(tenantId)
                        && item.personId().equals(personId))
                .toList();
    }

    @Override
    public List<Model.Employment> listEmploymentsByDepartment(UUID tenantId, UUID departmentId) {
        return employments.values().stream()
                .filter(item -> item.deletedAt() == null
                        && item.tenantId().equals(tenantId)
                        && item.departmentId().equals(departmentId)
                        && item.status() == Model.EmploymentStatus.ACTIVE)
                .toList();
    }

    @Override
    public Optional<Model.Employment> findPrimaryEmployment(UUID tenantId, UUID personId) {
        return listEmploymentsByPerson(tenantId, personId).stream()
                .filter(item -> item.kind() == Model.EmploymentKind.PRIMARY
                        && item.status() == Model.EmploymentStatus.ACTIVE)
                .findFirst();
    }

    @Override
    public Model.IssuedContext saveContext(Model.IssuedContext context) {
        contexts.put(context.id(), context);
        return context;
    }

    @Override
    public Optional<Model.IssuedContext> findContext(UUID contextId) {
        return Optional.ofNullable(contexts.get(contextId));
    }

    @Override
    public void revokeContextsForAccount(UUID accountId) {
        Instant now = Instant.now();
        contexts.replaceAll((id, context) -> {
            if (context.accountId().equals(accountId) && context.revokedAt() == null) {
                return new Model.IssuedContext(
                        context.id(), context.tenantId(), context.accountId(), context.personId(),
                        context.idpSubject(), context.issuedAt(), context.expiresAt(), now);
            }
            return context;
        });
    }
}
