package one.nexus.p2.membership;

import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.p2.support.DomainException;
import one.nexus.p2.support.Ids;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MembershipService {
    private final IdentityRepository repository;
    private final Clock clock;

    public MembershipService(IdentityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Model.TenantMembership add(UUID accountId) {
        UUID tenantId = TenantContextHolder.require().tenantId();
        repository.findAccount(accountId).orElseThrow(() -> DomainException.notFound("global account not found"));
        repository.findMembership(tenantId, accountId).ifPresent(existing -> {
            throw DomainException.conflict("account is already a member of this tenant");
        });
        Instant now = clock.instant();
        return repository.saveMembership(new Model.TenantMembership(
                Ids.v7(clock), tenantId, accountId, Model.MembershipStatus.ACTIVE, now, now, null, 0));
    }

    public List<Model.TenantMembership> listCurrentTenant() {
        return repository.listMembershipsByTenant(TenantContextHolder.require().tenantId());
    }

    public Model.TenantMembership suspend(UUID accountId) {
        UUID tenantId = TenantContextHolder.require().tenantId();
        Model.TenantMembership membership = repository.findMembership(tenantId, accountId)
                .orElseThrow(() -> DomainException.notFound("membership not found in current tenant"));
        Instant now = clock.instant();
        return repository.saveMembership(new Model.TenantMembership(
                membership.id(), membership.tenantId(), membership.accountId(),
                Model.MembershipStatus.SUSPENDED, membership.joinedAt(), now, membership.deletedAt(),
                membership.version() + 1));
    }
}
