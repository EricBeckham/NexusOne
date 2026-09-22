package one.nexus.permission.identity;

import java.util.UUID;
import one.nexus.p2.kernel.TenantContext;
import one.nexus.permission.engine.DepartmentCatalog;
import one.nexus.permission.engine.Subject;

/** Resolves a permission Subject from M0-04 identity. Does not fork TenantContext. */
public interface SubjectDirectory {
    Subject resolve(TenantContext context);

    DepartmentCatalog catalog(UUID tenantId);
}
