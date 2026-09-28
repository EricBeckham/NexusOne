package one.nexus.p3.instance;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import org.springframework.stereotype.Service;

/**
 * PLX1 C6. Tenant override wins. An override stays put when the platform version moves ahead, and then {@code drift} is set.
 */
@Service
public class InstanceResolutionService {
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{0,23}$");

    private final InstanceOverrideStore overrides;
    private final PlatformObjectSource platform;

    public InstanceResolutionService(InstanceOverrideStore overrides, PlatformObjectSource platform) {
        this.overrides = overrides;
        this.platform = platform;
    }

    public ResolvedObject resolve(UUID tenantId, UUID instanceId, ObjectType type, String code) {
        String objectCode = requireCode(code);
        requireIds(tenantId, instanceId);
        Optional<OverrideRecord> override = overrides.find(tenantId, instanceId, type, objectCode);
        Optional<PlatformObject> published = platform.find(tenantId, instanceId, type, objectCode);
        if (override.isPresent()) {
            OverrideRecord row = override.get();
            String platformVersion = published.map(PlatformObject::version).orElse(null);
            return new ResolvedObject(type, objectCode, ResolutionSource.TENANT, row.baseVersion(), platformVersion,
                    AppVersions.newer(platformVersion, row.baseVersion()), copy(row.content()));
        }
        PlatformObject publishedObject = published.orElseThrow(() -> notFound(instanceId, type, objectCode));
        return new ResolvedObject(type, objectCode, ResolutionSource.PLATFORM, publishedObject.version(),
                publishedObject.version(), false, copy(publishedObject.content()));
    }

    public ResolvedObject save(UUID tenantId, String actorId, UUID instanceId, ObjectType type, String code,
            String baseVersion, Map<String, Object> content) {
        String objectCode = requireCode(code);
        requireIds(tenantId, instanceId);
        if (!AppVersions.valid(baseVersion)) {
            throw invalid("base_version must be MAJOR.MINOR.PATCH");
        }
        Map<String, Object> body = content == null ? Map.of() : new LinkedHashMap<>(content);
        platform.find(tenantId, instanceId, type, objectCode)
                .ifPresent(published -> LedgerFieldRules.assertAdditive(published.content(), body));
        overrides.save(new OverrideRecord(tenantId, instanceId, type, objectCode, baseVersion, body,
                Instant.now(), actorId == null || actorId.isBlank() ? "unknown" : actorId));
        return resolve(tenantId, instanceId, type, objectCode);
    }

    public ResolvedObject clear(UUID tenantId, UUID instanceId, ObjectType type, String code) {
        String objectCode = requireCode(code);
        requireIds(tenantId, instanceId);
        overrides.delete(tenantId, instanceId, type, objectCode);
        return resolve(tenantId, instanceId, type, objectCode);
    }

    private static String requireCode(String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw invalid("object_code is invalid");
        }
        return code;
    }

    private static void requireIds(UUID tenantId, UUID instanceId) {
        if (tenantId == null || instanceId == null) {
            throw invalid("instance is required");
        }
    }

    private static Map<String, Object> copy(Map<String, Object> content) {
        return content == null ? Map.of() : new LinkedHashMap<>(content);
    }

    private static P3Exception invalid(String message) {
        return new P3Exception(P3ErrorCodes.INSTANCE_INVALID, 400, message, Map.of());
    }

    private static P3Exception notFound(UUID instanceId, ObjectType type, String code) {
        return new P3Exception(P3ErrorCodes.INSTANCE_NOT_FOUND, 404, "resolved object not found",
                Map.of("instance_id", instanceId.toString(), "object_type", type.name(), "object_code", code));
    }
}
