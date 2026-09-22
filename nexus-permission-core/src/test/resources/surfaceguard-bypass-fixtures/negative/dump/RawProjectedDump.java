package one.nexus.p4.api;

import java.util.List;
import java.util.Map;
import one.nexus.isolation.api.ExportFieldRule;
import one.nexus.isolation.boundary.IsolatedExport;

/** Intentional fixture: IsolatedExport.writeProjected without AuthorizedExport. */
public final class RawProjectedDump {
    private RawProjectedDump() {}

    public static void dump(String tenantId) {
        IsolatedExport.writeProjected(
                tenantId,
                "export/dump/raw.csv",
                List.of("id"),
                List.of(Map.of("id", "1")),
                Map.of("id", ExportFieldRule.allow()),
                false,
                (key, body) -> {});
    }
}
