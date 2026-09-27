package one.nexus.p4.market;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import one.nexus.p1.domain.AuditEvent;
import one.nexus.p1.port.AuditPort;
import one.nexus.p2.domain.IdentityRepository;
import one.nexus.p2.domain.Model;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.p2.kernel.TenantContextHolder;
import one.nexus.permission.PermissionModule;
import one.nexus.permission.admin.PolicyAdminService;
import one.nexus.permission.engine.DataScopeKind;
import one.nexus.permission.persist.PolicyRepository;
import one.nexus.p4.market.MarketModels.ProductDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Installs an idempotent, product-keyed tenant role via the existing permission engine. */
@Component
@ConditionalOnBean({PermissionModule.class, IdentityRepository.class, AuditPort.class})
public class ProductDefaultPermissionProvisioner {
    private static final List<String[]> REQUIRED_CAPABILITIES = List.of(
            new String[] {"form", "write"},
            new String[] {"process.instance", "start"},
            new String[] {"process.instance", "withdraw"},
            new String[] {"process.task", "read"},
            new String[] {"process.task", "approve"},
            new String[] {"process.task", "reject"});

    private final PermissionModule permission;
    private final IdentityRepository identities;
    private final AuditPort audit;

    public ProductDefaultPermissionProvisioner(PermissionModule permission, IdentityRepository identities, AuditPort audit) {
        this.permission = permission;
        this.identities = identities;
        this.audit = audit;
    }

    @Transactional
    public int provision(UUID tenantId, ProductDefinition definition) {
        TenantContext context = TenantContextHolder.require();
        if (!tenantId.equals(context.tenantId())) throw new SecurityException("permission pack tenant scope mismatch");
        String roleCode = roleCode(definition.id());
        PolicyRepository repository = permission.repository();
        PolicyAdminService admin = permission.admin();
        PolicyRepository.RoleRecord role = repository.findRoleByCode(tenantId, roleCode)
                .orElseGet(() -> admin.createRole(roleCode, "Product " + definition.name() + " members", true));
        ensureCapabilities(tenantId, roleCode, admin, repository);

        List<Model.Person> members = identities.listPersons(tenantId).stream()
                .filter(person -> person.status() == Model.PersonStatus.ACTIVE)
                .toList();
        for (Model.Person person : members) {
            boolean alreadyAssigned = repository.listAssignments(tenantId).stream()
                    .anyMatch(item -> "user".equals(item.principalType())
                            && person.id().toString().equals(item.principalId())
                            && role.id().equals(item.roleId()));
            if (!alreadyAssigned) admin.assign("user", person.id().toString(), role.id());
        }
        audit.append(new AuditEvent(UUID.randomUUID(), "p4.product_permission_pack.provisioned", AuditEvent.VERSION,
                context.issuedAt(), tenantId, context.accountId(), tenantId, false, null, context.contextId().toString(),
                context.contextId().toString(), null, AuditEvent.OUTCOME_ACCEPTED, "product_definition",
                definition.id().toString(), Map.of("role_code", roleCode, "member_count", members.size())));
        return members.size();
    }

    /** Approved install only: fixed runtime pack, explicit target, no impersonated tenant context. */
    @Transactional
    public int provisionApproved(PlatformActivationCommand command, ProductDefinition definition) {
        command.assertActive();
        command.assertDefinition(definition);
        UUID tenant=command.application().tenantId();
        String code=roleCode(definition.id());
        var repository=permission.repository();
        var role=repository.findRoleByCode(tenant,code).orElse(null);
        boolean changed=false;
        if(role==null) {
            role=repository.saveRole(new PolicyRepository.RoleRecord(one.nexus.common.id.UuidV7.generate(),tenant,code,
                    "Product "+definition.name()+" members",true,"ACTIVE")); changed=true;
        } else if(!role.systemRole() || !"ACTIVE".equals(role.status())) {
            throw new SecurityException("product permission role is not an active system-managed pack");
        }
        var grants=repository.listGrants(tenant);
        // Only the existing fixed runtime capabilities, never arbitrary caller-supplied grants/admin roles.
        for(String[] capability:REQUIRED_CAPABILITIES) {
            boolean exists=grants.stream().anyMatch(grant->grant.rule()!=null && "GRANT".equalsIgnoreCase(grant.effect())
                    && "role".equals(grant.rule().principalType()) && code.equals(grant.rule().principalId())
                    && capability[0].equals(grant.rule().resource()) && capability[1].equals(grant.rule().action()));
            if(!exists) {
                repository.saveGrant(new PolicyRepository.GrantRecord(one.nexus.common.id.UuidV7.generate(),tenant,"GRANT",
                        one.nexus.permission.engine.Rule.scoped("role",code,capability[0],capability[1],DataScopeKind.ALL,List.of(),Map.of())));
                changed=true;
            }
        }
        var members=identities.listPersons(tenant).stream().filter(person->person.status()==Model.PersonStatus.ACTIVE).toList();
        var assignments=repository.listAssignments(tenant);
        for(var person:members) {
            if(!tenant.equals(person.tenantId())) throw new SecurityException("permission pack member belongs to another tenant");
            UUID roleId=role.id();
            if(assignments.stream().noneMatch(item->"user".equals(item.principalType()) && person.id().toString().equals(item.principalId()) && roleId.equals(item.roleId()))) {
                repository.saveAssignment(new PolicyRepository.AssignmentRecord(one.nexus.common.id.UuidV7.generate(),tenant,"user",person.id().toString(),roleId,code));
                changed=true;
            }
        }
        if(changed) {
            repository.bumpVersion(tenant);
            if(permission.invalidation()!=null) permission.invalidation().notifyTenant(tenant.toString());
        }
        audit.append(new AuditEvent(UUID.randomUUID(),"p4.product_permission_pack.provisioned",AuditEvent.VERSION,
                java.time.Instant.now(),tenant,command.actorId(),TenantContextHolder.require().tenantId(),true,
                "approved activation "+command.application().id(),command.traceId(),command.traceId(),null,
                AuditEvent.OUTCOME_ACCEPTED,"product_definition",definition.id().toString(),Map.of("role_code",code,"member_count",members.size(),"activation_id",command.application().id().toString())));
        return members.size();
    }

    /** Must be used by application submission / approver selection to reject non-members. */
    public void requireTenantMember(UUID tenantId, UUID personId) {
        boolean member = identities.findPerson(tenantId, personId)
                .filter(person -> person.status() == Model.PersonStatus.ACTIVE)
                .isPresent();
        if (!member) throw new SecurityException("selected person is not an active member of this tenant");
    }

    static String roleCode(UUID productDefinitionId) {
        return "product_" + productDefinitionId.toString().replace("-", "");
    }

    private static void ensureCapabilities(UUID tenantId, String roleCode, PolicyAdminService admin,
            PolicyRepository repository) {
        List<PolicyRepository.GrantRecord> existing = repository.listGrants(tenantId);
        for (String[] capability : REQUIRED_CAPABILITIES) {
            boolean present = existing.stream().anyMatch(grant -> grant.rule() != null
                    && "GRANT".equalsIgnoreCase(grant.effect())
                    && "role".equals(grant.rule().principalType())
                    && roleCode.equals(grant.rule().principalId())
                    && capability[0].equals(grant.rule().resource())
                    && capability[1].equals(grant.rule().action()));
            if (!present) admin.grant("GRANT", "role", roleCode, capability[0], capability[1],
                    DataScopeKind.ALL, List.of(), Map.of());
        }
    }
}
