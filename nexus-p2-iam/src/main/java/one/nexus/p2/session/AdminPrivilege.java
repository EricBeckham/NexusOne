package one.nexus.p2.session;

import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;

import java.util.UUID;

/**
 * Admin detection from P2 employment / position codes. Does not invent a
 * second IdP role store; {@code TENANT_ADMIN} is the M0-04 initializer code.
 */
public final class AdminPrivilege {
    private AdminPrivilege() {}

    public static boolean holdsAdminRole(
            IdentityRepository repository,
            UUID accountId,
            UUID tenantId,
            AdminMfaPolicy policy
    ) {
        if (accountId == null) {
            return false;
        }
        if (tenantId != null && holdsAdminInTenant(repository, accountId, tenantId, policy)) {
            return true;
        }
        UUID platform = policy.platformTenantId();
        if (platform != null && !platform.equals(tenantId)
                && holdsAdminInTenant(repository, accountId, platform, policy)) {
            return true;
        }
        if (tenantId == null) {
            return repository.listMembershipsByAccount(accountId).stream()
                    .anyMatch(membership -> holdsAdminInTenant(
                            repository, accountId, membership.tenantId(), policy));
        }
        return false;
    }

    private static boolean holdsAdminInTenant(
            IdentityRepository repository,
            UUID accountId,
            UUID tenantId,
            AdminMfaPolicy policy
    ) {
        return repository.findPersonByAccount(tenantId, accountId)
                .filter(person -> person.status() == Model.PersonStatus.ACTIVE)
                .map(person -> repository.listEmploymentsByPerson(tenantId, person.id()).stream()
                        .filter(item -> item.status() == Model.EmploymentStatus.ACTIVE)
                        .anyMatch(employment -> repository.findPosition(tenantId, employment.positionId())
                                .map(position -> policy.isAdminPosition(position.code()))
                                .orElse(false)))
                .orElse(false);
    }
}
