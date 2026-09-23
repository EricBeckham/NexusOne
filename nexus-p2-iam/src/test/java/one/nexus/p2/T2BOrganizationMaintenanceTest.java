package one.nexus.p2;

import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.org.OrganizationService;
import one.nexus.p2.persist.InMemoryIdentityRepository;
import one.nexus.p2.support.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class T2BOrganizationMaintenanceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ACCOUNT = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-09-23T08:00:00Z");

    private InMemoryIdentityRepository repository;
    private OrganizationService service;
    private Model.Person person;

    @BeforeEach
    void setUp() {
        repository = new InMemoryIdentityRepository();
        service = new OrganizationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), TENANT, ACCOUNT, UUID.randomUUID(), "t2-b-user",
                NOW, NOW.plusSeconds(3600)));
        person = service.createPerson(new OrganizationService.PersonCommand(
                "Alice", "E-001", "alice@example.com", null, LocalDate.of(2026, 1, 1), ACCOUNT));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void maintainsDepartmentPositionAndEmploymentLifecycle() {
        Model.Department root = service.createDepartment(
                new OrganizationService.DepartmentCommand("ROOT", "Head Office", null, 0, person.id()));
        Model.Department engineering = service.createDepartment(
                new OrganizationService.DepartmentCommand("ENG", "Engineering", root.id(), 10, person.id()));
        Model.Position engineer = service.createPosition(
                new OrganizationService.PositionCommand("ENGINEER", "Engineer", "IC", "Build products"));

        Model.Employment employment = service.assign(new OrganizationService.EmploymentCommand(
                person.id(), engineering.id(), engineer.id(), Model.EmploymentKind.PRIMARY,
                LocalDate.of(2026, 9, 1), null));

        assertEquals(1, service.listEmployments().size());
        assertEquals(employment.id(), service.listEmployments(person.id()).getFirst().id());
        assertEquals("ENG", service.departmentTree().getFirst().children().getFirst().department().code());
        assertThrows(DomainException.class, () -> service.disableDepartment(engineering.id()));
        assertThrows(DomainException.class, () -> service.disablePosition(engineer.id()));

        Model.Employment ended = service.endEmployment(employment.id());
        assertEquals(Model.EmploymentStatus.ENDED, ended.status());
        assertEquals(LocalDate.of(2026, 9, 23), ended.validTo());
        assertEquals(Model.OrgStatus.DISABLED, service.disablePosition(engineer.id()).status());
        assertEquals(Model.OrgStatus.DISABLED, service.disableDepartment(engineering.id()).status());
    }

    @Test
    void rejectsInvalidDatesAndDisabledOrganizationTargets() {
        Model.Department department = service.createDepartment(
                new OrganizationService.DepartmentCommand("OPS", "Operations", null, 0, null));
        Model.Position position = service.createPosition(
                new OrganizationService.PositionCommand("OPS", "Operator", "IC", null));

        DomainException dates = assertThrows(DomainException.class, () -> service.assign(
                new OrganizationService.EmploymentCommand(person.id(), department.id(), position.id(),
                        Model.EmploymentKind.PRIMARY, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1))));
        assertEquals("P2.IAM.EMPLOYMENT_DATES", dates.code());

        service.disablePosition(position.id());
        DomainException disabled = assertThrows(DomainException.class, () -> service.assign(
                new OrganizationService.EmploymentCommand(person.id(), department.id(), position.id(),
                        Model.EmploymentKind.PRIMARY, null, null)));
        assertTrue(disabled.getMessage().contains("disabled"));
    }

    @Test
    void leaderAndParentMustBelongToCurrentTenantAndBeActive() {
        DomainException missingLeader = assertThrows(DomainException.class, () -> service.createDepartment(
                new OrganizationService.DepartmentCommand("FIN", "Finance", null, 0, UUID.randomUUID())));
        assertEquals("P2.IAM.NOT_FOUND", missingLeader.code());

        Model.Department disabledParent = service.createDepartment(
                new OrganizationService.DepartmentCommand("OLD", "Old", null, 0, null));
        service.disableDepartment(disabledParent.id());
        DomainException inactiveParent = assertThrows(DomainException.class, () -> service.createDepartment(
                new OrganizationService.DepartmentCommand("CHILD", "Child", disabledParent.id(), 0, null)));
        assertTrue(inactiveParent.getMessage().contains("disabled"));
    }
}
