package one.nexus.p4.api;

import one.nexus.isolation.boundary.IsolatedExport;

/** Mini-product market dump skips AuthorizedExport / SurfaceGuard. */
public final class RawExportDump {
    private RawExportDump() {}

    public static void dump(String tenantId) {
        IsolatedExport.submit(tenantId, "{\"ledger\":\"leave\"}");
    }
}
