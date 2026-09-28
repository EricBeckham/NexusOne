package one.nexus.p3.template;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PLB3: immutable platform template snapshot addressed by one published application-version id.
 * The market module owns storage; tenant-side resolution only depends on this P3 port.
 */
public interface PlatformTemplateCatalog {
    Optional<TemplateVersion> published(UUID versionId);

    record TemplateVersion(UUID versionId, String appCode, String version, String categoryCode,
            List<Map<String, Object>> ledgers, List<Map<String, Object>> forms,
            List<Map<String, Object>> processes, List<Map<String, Object>> rolePack,
            List<Map<String, Object>> entries) {
        public TemplateVersion {
            ledgers = List.copyOf(ledgers);
            forms = List.copyOf(forms);
            processes = List.copyOf(processes);
            rolePack = List.copyOf(rolePack);
            entries = List.copyOf(entries);
        }
    }
}
