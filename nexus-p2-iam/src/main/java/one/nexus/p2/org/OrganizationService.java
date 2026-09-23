package one.nexus.p2.org;

import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.support.DomainException;
import one.nexus.common.id.UuidV7;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class OrganizationService {
    public record PersonCommand(String displayName, String employeeNo, String email, String phone, LocalDate hiredOn, UUID accountId) {}
    public record DepartmentCommand(String code, String name, UUID parentId, int sortOrder, UUID leaderPersonId) {}
    public record PositionCommand(String code, String name, String category, String duties) {}
    public record EmploymentCommand(UUID personId, UUID departmentId, UUID positionId, Model.EmploymentKind kind, LocalDate validFrom, LocalDate validTo) {}
    public record DepartmentNode(Model.Department department, List<DepartmentNode> children) {}

    private final IdentityRepository repository;
    private final Clock clock;

    public OrganizationService(IdentityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Model.Person createPerson(PersonCommand command) {
        TenantContext context = TenantContextHolder.require();
        UUID tenantId = context.tenantId();
        if (command.displayName() == null || command.displayName().isBlank()) {
            throw DomainException.badRequest("P2.IAM.PERSON_NAME", "displayName is required");
        }
        if (command.employeeNo() != null) {
            repository.findPersonByEmployeeNo(tenantId, command.employeeNo()).ifPresent(existing -> {
                throw DomainException.conflict("employee number already exists in this tenant");
            });
        }
        UUID accountId = command.accountId() == null ? context.accountId() : command.accountId();
        repository.findPersonByAccount(tenantId, accountId).ifPresent(existing -> {
            throw DomainException.conflict("person already exists for this account in the tenant");
        });
        Instant now = clock.instant();
        Model.Person person = new Model.Person(
                UuidV7.generate(), tenantId, accountId, command.displayName(), command.employeeNo(),
                command.email(), command.phone(), command.hiredOn(), Model.PersonStatus.ACTIVE,
                now, now, null, 0);
        return repository.savePerson(person);
    }

    public List<Model.Person> listPersons() {
        return repository.listPersons(TenantContextHolder.require().tenantId());
    }

    public Model.Person getPerson(UUID personId) {
        TenantContext context = TenantContextHolder.require();
        return repository.findPerson(context.tenantId(), personId)
                .orElseThrow(() -> DomainException.notFound("person not found in current tenant"));
    }

    public Model.Person disablePerson(UUID personId) {
        Model.Person person = getPerson(personId);
        Instant now = clock.instant();
        return repository.savePerson(new Model.Person(
                person.id(), person.tenantId(), person.accountId(), person.displayName(), person.employeeNo(),
                person.email(), person.phone(), person.hiredOn(), Model.PersonStatus.DISABLED,
                person.createdAt(), now, person.deletedAt(), person.version() + 1));
    }

    public Model.Department createDepartment(DepartmentCommand command) {
        TenantContext context = TenantContextHolder.require();
        UUID tenantId = context.tenantId();
        requireText(command.code(), "P2.IAM.DEPT_CODE", "department code is required");
        requireText(command.name(), "P2.IAM.DEPT_NAME", "department name is required");
        repository.findDepartmentByCode(tenantId, command.code()).ifPresent(existing -> {
            throw DomainException.conflict("department code already exists in this tenant");
        });
        String pathPrefix;
        if (command.parentId() == null) {
            pathPrefix = "/";
        } else {
            Model.Department parent = repository.findDepartment(tenantId, command.parentId())
                    .orElseThrow(() -> DomainException.notFound("parent department not found in current tenant"));
            if (parent.status() != Model.OrgStatus.ACTIVE) {
                throw DomainException.conflict("parent department is disabled");
            }
            pathPrefix = parent.path();
        }
        if (command.leaderPersonId() != null) {
            Model.Person leader = repository.findPerson(tenantId, command.leaderPersonId())
                    .orElseThrow(() -> DomainException.notFound("leader person not found in current tenant"));
            if (leader.status() != Model.PersonStatus.ACTIVE) {
                throw DomainException.conflict("leader person is not active");
            }
        }
        Instant now = clock.instant();
        UUID id = UuidV7.generate();
        Model.Department department = new Model.Department(
                id, tenantId, command.parentId(), command.code(), command.name(),
                pathPrefix + id + "/", command.sortOrder(), command.leaderPersonId(), Model.OrgStatus.ACTIVE,
                now, now, null, 0);
        return repository.saveDepartment(department);
    }

    public List<DepartmentNode> departmentTree() {
        UUID tenantId = TenantContextHolder.require().tenantId();
        List<Model.Department> all = repository.listDepartments(tenantId);
        Map<UUID, List<Model.Department>> byParent = all.stream()
                .collect(Collectors.groupingBy(item -> item.parentId() == null ? tenantId : item.parentId()));
        List<Model.Department> roots = all.stream()
                .filter(item -> item.parentId() == null)
                .sorted(Comparator.comparingInt(Model.Department::sortOrder).thenComparing(Model.Department::name))
                .toList();
        List<DepartmentNode> tree = new ArrayList<>();
        for (Model.Department root : roots) {
            tree.add(toNode(root, byParent));
        }
        return tree;
    }

    public Model.Department disableDepartment(UUID departmentId) {
        TenantContext context = TenantContextHolder.require();
        Model.Department department = repository.findDepartment(context.tenantId(), departmentId)
                .orElseThrow(() -> DomainException.notFound("department not found in current tenant"));
        if (repository.countChildDepartments(context.tenantId(), departmentId) > 0) {
            throw DomainException.conflict("department has children");
        }
        if (!repository.listEmploymentsByDepartment(context.tenantId(), departmentId).isEmpty()) {
            throw DomainException.conflict("department still has active employments");
        }
        Instant now = clock.instant();
        return repository.saveDepartment(new Model.Department(
                department.id(), department.tenantId(), department.parentId(), department.code(), department.name(),
                department.path(), department.sortOrder(), department.leaderPersonId(), Model.OrgStatus.DISABLED,
                department.createdAt(), now, department.deletedAt(), department.version() + 1));
    }

    public Model.Position createPosition(PositionCommand command) {
        TenantContext context = TenantContextHolder.require();
        requireText(command.code(), "P2.IAM.POS_CODE", "position code is required");
        requireText(command.name(), "P2.IAM.POS_NAME", "position name is required");
        repository.findPositionByCode(context.tenantId(), command.code()).ifPresent(existing -> {
            throw DomainException.conflict("position code already exists in this tenant");
        });
        Instant now = clock.instant();
        Model.Position position = new Model.Position(
                UuidV7.generate(), context.tenantId(), command.code(), command.name(), command.category(),
                command.duties(), Model.OrgStatus.ACTIVE, now, now, null, 0);
        return repository.savePosition(position);
    }

    public List<Model.Position> listPositions() {
        return repository.listPositions(TenantContextHolder.require().tenantId());
    }

    public Model.Position disablePosition(UUID positionId) {
        TenantContext context = TenantContextHolder.require();
        Model.Position position = repository.findPosition(context.tenantId(), positionId)
                .orElseThrow(() -> DomainException.notFound("position not found in current tenant"));
        if (!repository.listEmploymentsByPosition(context.tenantId(), positionId).isEmpty()) {
            throw DomainException.conflict("position still has active employments");
        }
        if (position.status() == Model.OrgStatus.DISABLED) {
            return position;
        }
        Instant now = clock.instant();
        return repository.savePosition(new Model.Position(
                position.id(), position.tenantId(), position.code(), position.name(), position.category(),
                position.duties(), Model.OrgStatus.DISABLED, position.createdAt(), now,
                position.deletedAt(), position.version() + 1));
    }

    public Model.Employment assign(EmploymentCommand command) {
        TenantContext context = TenantContextHolder.require();
        UUID tenantId = context.tenantId();
        Model.Person person = repository.findPerson(tenantId, command.personId())
                .orElseThrow(() -> DomainException.notFound("person not found in current tenant"));
        Model.Department department = repository.findDepartment(tenantId, command.departmentId())
                .orElseThrow(() -> DomainException.notFound("department not found in current tenant"));
        Model.Position position = repository.findPosition(tenantId, command.positionId())
                .orElseThrow(() -> DomainException.notFound("position not found in current tenant"));
        if (person.status() != Model.PersonStatus.ACTIVE) {
            throw DomainException.conflict("person is not active");
        }
        if (department.status() != Model.OrgStatus.ACTIVE) {
            throw DomainException.conflict("department is disabled");
        }
        if (position.status() != Model.OrgStatus.ACTIVE) {
            throw DomainException.conflict("position is disabled");
        }
        Model.EmploymentKind kind = command.kind() == null ? Model.EmploymentKind.PRIMARY : command.kind();
        if (kind == Model.EmploymentKind.PRIMARY) {
            repository.findPrimaryEmployment(tenantId, command.personId()).ifPresent(existing -> {
                throw DomainException.conflict("person already has a primary employment in this tenant");
            });
        }
        Instant now = clock.instant();
        LocalDate from = command.validFrom() == null ? LocalDate.ofInstant(now, clock.getZone()) : command.validFrom();
        if (command.validTo() != null && command.validTo().isBefore(from)) {
            throw DomainException.badRequest("P2.IAM.EMPLOYMENT_DATES", "validTo must not be before validFrom");
        }
        Model.Employment employment = new Model.Employment(
                UuidV7.generate(), tenantId, command.personId(), command.departmentId(), command.positionId(),
                kind, Model.EmploymentStatus.ACTIVE, from, command.validTo(), now, now, null, 0);
        return repository.saveEmployment(employment);
    }

    public List<Model.Employment> listEmployments(UUID personId) {
        return repository.listEmploymentsByPerson(TenantContextHolder.require().tenantId(), personId);
    }

    public List<Model.Employment> listEmployments() {
        return repository.listEmployments(TenantContextHolder.require().tenantId());
    }

    public Model.Employment endEmployment(UUID employmentId) {
        TenantContext context = TenantContextHolder.require();
        Model.Employment employment = repository.findEmployment(context.tenantId(), employmentId)
                .orElseThrow(() -> DomainException.notFound("employment not found in current tenant"));
        if (employment.status() == Model.EmploymentStatus.ENDED) {
            return employment;
        }
        Instant now = clock.instant();
        return repository.saveEmployment(new Model.Employment(
                employment.id(), employment.tenantId(), employment.personId(), employment.departmentId(),
                employment.positionId(), employment.kind(), Model.EmploymentStatus.ENDED,
                employment.validFrom(), LocalDate.ofInstant(now, clock.getZone()),
                employment.createdAt(), now, employment.deletedAt(), employment.version() + 1));
    }

    private DepartmentNode toNode(Model.Department department, Map<UUID, List<Model.Department>> byParent) {
        List<Model.Department> children = byParent.getOrDefault(department.id(), List.of()).stream()
                .sorted(Comparator.comparingInt(Model.Department::sortOrder).thenComparing(Model.Department::name))
                .toList();
        List<DepartmentNode> nodes = new ArrayList<>();
        for (Model.Department child : children) {
            nodes.add(toNode(child, byParent));
        }
        return new DepartmentNode(department, nodes);
    }

    private static void requireText(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw DomainException.badRequest(code, message);
        }
    }
}
