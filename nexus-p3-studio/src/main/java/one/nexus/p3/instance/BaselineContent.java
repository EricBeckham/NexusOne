package one.nexus.p3.instance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Structural diff and merge for override content. Paths use a leading slash. Lists of coded objects match on {@code code}. */
final class BaselineContent {
    private BaselineContent() {}

    static List<BaselineChange> diff(Map<String, Object> tenant, Map<String, Object> platform) {
        List<BaselineChange> changes = new ArrayList<>();
        walk("", copy(tenant), copy(platform), true, true, changes);
        return List.copyOf(changes);
    }

    static Map<String, Object> merged(Map<String, Object> platform, List<BaselineChange> changes, Map<String, String> choices) {
        Map<String, Object> result = copy(platform);
        Map<String, String> picked = choices == null ? Map.of() : choices;
        for (BaselineChange change : changes) {
            boolean keepTenant = "TENANT_ONLY".equals(change.kind()) || "TENANT".equals(picked.get(change.path()));
            if (keepTenant) {
                put(result, change.path(), copyValue(change.tenantValue()));
            }
        }
        return result;
    }

    private static void walk(String path, Object tenant, Object platform, boolean tenantPresent, boolean platformPresent,
            List<BaselineChange> out) {
        if (!tenantPresent) {
            out.add(new BaselineChange(path, "PLATFORM_ONLY", null, copyValue(platform)));
            return;
        }
        if (!platformPresent) {
            out.add(new BaselineChange(path, "TENANT_ONLY", copyValue(tenant), null));
            return;
        }
        if (tenant instanceof Map<?, ?> tenantMap && platform instanceof Map<?, ?> platformMap) {
            walkMap(path, tenantMap, platformMap, out);
            return;
        }
        if (tenant instanceof List<?> tenantList && platform instanceof List<?> platformList
                && keyed(tenantList) && keyed(platformList)) {
            walkKeyed(path, tenantList, platformList, out);
            return;
        }
        if (!Objects.equals(tenant, platform)) {
            out.add(new BaselineChange(path.isEmpty() ? "/" : path, "CONFLICT", copyValue(tenant), copyValue(platform)));
        }
    }

    private static void walkMap(String path, Map<?, ?> tenant, Map<?, ?> platform, List<BaselineChange> out) {
        Set<String> keys = new LinkedHashSet<>();
        tenant.keySet().forEach(key -> keys.add(String.valueOf(key)));
        platform.keySet().forEach(key -> keys.add(String.valueOf(key)));
        for (String key : keys) {
            walk(path + "/" + key, tenant.get(key), platform.get(key), tenant.containsKey(key), platform.containsKey(key), out);
        }
    }

    private static void walkKeyed(String path, List<?> tenant, List<?> platform, List<BaselineChange> out) {
        Map<String, Object> tenantByCode = byCode(tenant);
        Map<String, Object> platformByCode = byCode(platform);
        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(tenantByCode.keySet());
        codes.addAll(platformByCode.keySet());
        for (String code : codes) {
            walk(path + "/" + code, tenantByCode.get(code), platformByCode.get(code),
                    tenantByCode.containsKey(code), platformByCode.containsKey(code), out);
        }
    }

    private static boolean keyed(List<?> list) {
        if (list.isEmpty()) {
            return true;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map) || !(map.get("code") instanceof String code) || code.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> byCode(List<?> list) {
        Map<String, Object> indexed = new LinkedHashMap<>();
        for (Object item : list) {
            indexed.put((String) ((Map<?, ?>) item).get("code"), item);
        }
        return indexed;
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> root, String path, Object value) {
        String[] parts = path.substring(1).split("/");
        Object parent = root;
        for (int i = 0; i < parts.length - 1; i++) {
            parent = descend(parent, parts[i]);
        }
        String leaf = parts[parts.length - 1];
        if (parent instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).put(leaf, value);
            return;
        }
        if (parent instanceof List<?> list) {
            List<Object> items = (List<Object>) list;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) instanceof Map<?, ?> map && leaf.equals(map.get("code"))) {
                    items.set(i, value);
                    return;
                }
            }
            items.add(value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object descend(Object parent, String key) {
        if (parent instanceof Map<?, ?> map) {
            Object child = map.get(key);
            if (child == null) {
                Map<String, Object> created = new LinkedHashMap<>();
                ((Map<String, Object>) map).put(key, created);
                return created;
            }
            return child;
        }
        if (parent instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && key.equals(map.get("code"))) {
                    return item;
                }
            }
        }
        return new LinkedHashMap<String, Object>();
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return (Map<String, Object>) copyValue(source == null ? Map.of() : source);
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), copyValue(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(copyValue(item)));
            return copy;
        }
        return value;
    }
}
