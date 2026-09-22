package one.nexus.p3.flow;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.permission.persist.PolicyRepository;

/**
 * Resolves Flowable assignees from person, position, or P2 role assignment.
 * Role lookup uses M0-05 {@link PolicyRepository}; it does not read Flowable tables.
 */
public final class AssigneeResolver {
    private final IdentityRepository identity;
    private final PolicyRepository policies;

    public AssigneeResolver(IdentityRepository identity, PolicyRepository policies) {
        this.identity = identity;
        this.policies = policies;
    }

    public List<UUID> resolve(UUID tenantId, AssigneeType type, String ref) {
        if (ref == null || ref.isBlank()) {
            throw FlowException.invalidAssignee("assignee_ref is required");
        }
        List<UUID> people = switch (type) {
            case PERSON -> List.of(requirePerson(tenantId, parseUuid(ref, "person")));
            case POSITION -> peopleWithPosition(tenantId, parseUuid(ref, "position"));
            case ROLE -> peopleWithRole(tenantId, ref.trim());
        };
        if (people.isEmpty()) {
            throw FlowException.invalidAssignee("no active people match assignee " + type + ":" + ref);
        }
        return List.copyOf(people);
    }

    private UUID requirePerson(UUID tenantId, UUID personId) {
        Model.Person person = identity.findPerson(tenantId, personId)
                .orElseThrow(() -> FlowException.invalidAssignee("person not found in current tenant"));
        if (person.status() != Model.PersonStatus.ACTIVE || person.deletedAt() != null) {
            throw FlowException.invalidAssignee("person is not active in current tenant");
        }
        return person.id();
    }

    private List<UUID> peopleWithPosition(UUID tenantId, UUID positionId) {
        identity.findPosition(tenantId, positionId)
                .orElseThrow(() -> FlowException.invalidAssignee("position not found in current tenant"));
        Set<UUID> people = new LinkedHashSet<>();
        for (Model.Person person : identity.listPersons(tenantId)) {
            if (person.status() != Model.PersonStatus.ACTIVE || person.deletedAt() != null) {
                continue;
            }
            for (Model.Employment employment : identity.listEmploymentsByPerson(tenantId, person.id())) {
                if (employment.status() == Model.EmploymentStatus.ACTIVE
                        && employment.deletedAt() == null
                        && positionId.equals(employment.positionId())) {
                    people.add(person.id());
                }
            }
        }
        return new ArrayList<>(people);
    }

    private List<UUID> peopleWithRole(UUID tenantId, String roleCode) {
        PolicyRepository.RoleRecord role = policies.findRoleByCode(tenantId, roleCode)
                .orElseThrow(() -> FlowException.invalidAssignee("role not found in current tenant"));
        Set<UUID> people = new LinkedHashSet<>();
        for (PolicyRepository.AssignmentRecord assignment : policies.listAssignments(tenantId)) {
            if (!assignment.roleId().equals(role.id())) {
                continue;
            }
            switch (assignment.principalType()) {
                case "user" -> people.add(requirePerson(tenantId, parseUuid(assignment.principalId(), "person")));
                case "position" -> people.addAll(peopleWithPosition(tenantId, parseUuid(assignment.principalId(), "position")));
                case "department" -> people.addAll(peopleInDepartment(tenantId, parseUuid(assignment.principalId(), "department")));
                default -> { }
            }
        }
        return new ArrayList<>(people);
    }

    private List<UUID> peopleInDepartment(UUID tenantId, UUID departmentId) {
        Set<UUID> people = new LinkedHashSet<>();
        for (Model.Person person : identity.listPersons(tenantId)) {
            if (person.status() != Model.PersonStatus.ACTIVE || person.deletedAt() != null) {
                continue;
            }
            for (Model.Employment employment : identity.listEmploymentsByPerson(tenantId, person.id())) {
                if (employment.status() == Model.EmploymentStatus.ACTIVE
                        && employment.deletedAt() == null
                        && departmentId.equals(employment.departmentId())) {
                    people.add(person.id());
                }
            }
        }
        return new ArrayList<>(people);
    }

    private static UUID parseUuid(String value, String label) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw FlowException.invalidAssignee(label + " must be a UUID");
        }
    }
}
