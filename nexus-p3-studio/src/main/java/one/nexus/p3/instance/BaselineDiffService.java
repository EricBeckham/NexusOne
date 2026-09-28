package one.nexus.p3.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p3.domain.P3ErrorCodes;
import one.nexus.p3.domain.P3Exception;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** PLC8. Lists drifted overrides and rebases a chosen merge onto the current platform version. */
@Service
public class BaselineDiffService {
    private final InstanceOverrideStore overrides;
    private final PlatformObjectSource platform;
    private final InstanceResolutionService resolution;
    private final InstanceReconciler reconciler;

    @Autowired
    public BaselineDiffService(InstanceOverrideStore overrides, PlatformObjectSource platform,
            InstanceResolutionService resolution, @Autowired(required = false) InstanceReconciler reconciler) {
        this.overrides = overrides;
        this.platform = platform;
        this.resolution = resolution;
        this.reconciler = reconciler;
    }

    public List<BaselineDiff> drift(UUID tenantId) {
        reconcile(tenantId);
        List<BaselineDiff> drifted = new ArrayList<>();
        for (OverrideRecord row : overrides.list(tenantId)) {
            BaselineDiff diff = diff(tenantId, row);
            if (diff.drift()) {
                drifted.add(diff);
            }
        }
        return List.copyOf(drifted);
    }

    public BaselineDiff diff(UUID tenantId, UUID instanceId, ObjectType type, String code) {
        reconcile(tenantId);
        OverrideRecord row = overrides.find(tenantId, instanceId, type, code)
                .orElseThrow(() -> notFound(instanceId, type, code));
        return diff(tenantId, row);
    }

    public ResolvedObject merge(UUID tenantId, String actorId, UUID instanceId, ObjectType type, String code,
            Map<String, String> choices) {
        BaselineDiff diff = diff(tenantId, instanceId, type, code);
        if (diff.platformVersion() == null) {
            throw new P3Exception(P3ErrorCodes.INSTANCE_INVALID, 409, "platform version is missing", Map.of());
        }
        List<String> unresolved = new ArrayList<>();
        Map<String, String> picked = choices == null ? Map.of() : choices;
        for (BaselineChange change : diff.changes()) {
            if (!"CONFLICT".equals(change.kind())) {
                continue;
            }
            String choice = picked.get(change.path());
            if (!"TENANT".equals(choice) && !"PLATFORM".equals(choice)) {
                unresolved.add(change.path());
            }
        }
        if (!unresolved.isEmpty()) {
            throw new P3Exception(P3ErrorCodes.INSTANCE_INVALID, 409, "choose a side for every conflict",
                    Map.of("paths", List.copyOf(unresolved)));
        }
        PlatformObject published = platform.find(tenantId, instanceId, type, code).orElseThrow(
                () -> new P3Exception(P3ErrorCodes.INSTANCE_INVALID, 409, "platform version is missing", Map.of()));
        OverrideRecord row = overrides.find(tenantId, instanceId, type, code).orElseThrow(
                () -> notFound(instanceId, type, code));
        Map<String, Object> merged = BaselineContent.merged(published.content(), diff.changes(), picked);
        LedgerFieldRules.assertAdditive(published.content(), merged);
        return resolution.save(tenantId, actorId, instanceId, type, code, published.version(), merged);
    }

    private BaselineDiff diff(UUID tenantId, OverrideRecord row) {
        Optional<PlatformObject> published = platform.find(tenantId, row.instanceId(), row.objectType(), row.objectCode());
        String platformVersion = published.map(PlatformObject::version).orElse(null);
        Map<String, Object> platformContent = published.map(PlatformObject::content).orElse(Map.of());
        List<BaselineChange> changes = published.isEmpty()
                ? List.of()
                : BaselineContent.diff(row.content(), platformContent);
        return new BaselineDiff(row.instanceId(), row.objectType(), row.objectCode(), row.baseVersion(),
                platformVersion, AppVersions.newer(platformVersion, row.baseVersion()), changes);
    }

    private void reconcile(UUID tenantId) {
        if (reconciler != null) {
            reconciler.reconcile(tenantId);
        }
    }

    private static P3Exception notFound(UUID instanceId, ObjectType type, String code) {
        return new P3Exception(P3ErrorCodes.INSTANCE_NOT_FOUND, 404, "override not found",
                Map.of("instance_id", instanceId.toString(), "object_type", type.name(), "object_code", code));
    }
}
