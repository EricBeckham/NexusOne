package one.nexus.permission.scan.fixtures;

import one.nexus.isolation.boundary.IsolatedExport;

/** Intentional fixture: IsolatedExport.submit without AuthorizedExport. */
public final class RawExport {
    private RawExport() {}

    public static void dump(String tenantId) {
        IsolatedExport.submit(tenantId, "{\"ledger\":\"leave\"}");
    }
}
