package one.nexus.p4.market;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** PLB2: platform-owned category and draft manifest editing. No tenant installation side effects. */
@Service
@DependsOn("flyway")
public class AppDesignService {
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,23}");
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final IsolationGuard isolation;
    private final AppManifestValidator validator = new AppManifestValidator();

    public record Category(String code, String name, String description, int sortOrder,
            Instant createdAt, Instant updatedAt, long version) {}
    public record CategoryWrite(String name, String description, int sortOrder, Long version) {}
    public record ManifestSnapshot(UUID versionId, Map<String, Object> manifest) {}
    public record Validation(List<AppManifestValidator.Problem> problems) {
        public boolean valid() { return problems.isEmpty(); }
    }

    public AppDesignService(JdbcClient jdbc, ObjectMapper json, IsolationGuard isolation) {
        this.jdbc = jdbc;
        this.json = json;
        this.isolation = isolation;
    }

    @Transactional(readOnly = true)
    public List<Category> categories() {
        platform("app-category.list");
        return jdbc.sql("SELECT * FROM market.app_category ORDER BY sort_order, code").query(this::mapCategory).list();
    }

    @Transactional(readOnly = true)
    public Category category(String code) {
        platform("app-category.get");
        return requireCategory(code);
    }

    @Transactional
    public Category createCategory(String code, CategoryWrite request) {
        platform("app-category.create");
        requireCode(code);
        requireName(request);
        Instant now = Instant.now();
        int rows = jdbc.sql("""
                INSERT INTO market.app_category(code,name,description,sort_order,created_at,updated_at,version)
                VALUES(:code,:name,:description,:sort,:now,:now,1)
                ON CONFLICT(code) DO NOTHING
                """).param("code", code).param("name", request.name().trim())
                .param("description", request.description()).param("sort", request.sortOrder())
                .param("now", java.sql.Timestamp.from(now)).update();
        if (rows != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "category code already exists");
        return requireCategory(code);
    }

    @Transactional
    public Category updateCategory(String code, CategoryWrite request) {
        platform("app-category.update");
        requireCode(code);
        requireName(request);
        if (request.version() == null || request.version() < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "version required");
        int rows = jdbc.sql("""
                UPDATE market.app_category SET name=:name,description=:description,sort_order=:sort,
                updated_at=:now,version=version+1 WHERE code=:code AND version=:version
                """).param("name", request.name().trim()).param("description", request.description())
                .param("sort", request.sortOrder()).param("now", java.sql.Timestamp.from(Instant.now()))
                .param("code", code).param("version", request.version()).update();
        if (rows != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "category version changed or category not found");
        return requireCategory(code);
    }

    @Transactional
    public void deleteCategory(String code) {
        platform("app-category.delete");
        requireCode(code);
        int rows = jdbc.sql("""
                DELETE FROM market.app_category c WHERE c.code=:code
                AND NOT EXISTS(SELECT 1 FROM market.app_package a WHERE a.category_code=c.code)
                """).param("code", code).update();
        if (rows != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "category not found or in use");
    }

    @Transactional
    public void setAppCategory(UUID appId, String code) {
        platform("app.category.update");
        if (code != null) requireCategory(code);
        int rows = jdbc.sql("UPDATE market.app_package SET category_code=:code WHERE id=:id")
                .param("code", code).param("id", appId).update();
        if (rows != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "app not found");
    }

    @Transactional(readOnly = true)
    public ManifestSnapshot manifest(UUID versionId) {
        platform("app-manifest.get");
        return new ManifestSnapshot(versionId, readManifest(requireVersion(versionId).manifest()));
    }

    @Transactional(readOnly = true)
    public Validation validate(UUID versionId, Map<String, Object> manifest) {
        platform("app-manifest.validate");
        VersionRow row = requireVersion(versionId);
        return new Validation(validateForVersion(row, manifest));
    }

    @Transactional
    public ManifestSnapshot saveManifest(UUID versionId, Map<String, Object> manifest) {
        platform("app-manifest.update");
        VersionRow row = requireVersion(versionId);
        if (!"DRAFT".equals(row.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "only draft manifests can change");
        List<AppManifestValidator.Problem> problems = validateForVersion(row, manifest);
        if (!problems.isEmpty()) throw new ManifestValidationException(problems);
        String snapshot = json.writeValueAsString(manifest);
        int changed = jdbc.sql("UPDATE market.app_version SET manifest=CAST(:manifest AS jsonb) WHERE id=:id AND status='DRAFT'")
                .param("manifest", snapshot).param("id", versionId).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "version is no longer draft");
        return new ManifestSnapshot(versionId, readManifest(snapshot));
    }

    /** Legacy NULL/NULL rows remain on their old path until PLB9; new categorized apps need a valid snapshot. */
    public void requirePublishable(UUID versionId) {
        platform("app-manifest.publish");
        VersionRow row = requireVersion(versionId);
        if (row.manifest() == null && row.categoryCode() == null) return;
        if (row.manifest() == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "application manifest required");
        List<AppManifestValidator.Problem> problems = validateForVersion(row, readManifest(row.manifest()));
        if (!problems.isEmpty()) throw new ManifestValidationException(problems);
    }

    private List<AppManifestValidator.Problem> validateForVersion(VersionRow row, Map<String, Object> manifest) {
        List<AppManifestValidator.Problem> problems = new java.util.ArrayList<>(validator.validate(manifest));
        if (manifest != null) {
            if (!row.appCode().equals(manifest.get("app_code")))
                problems.add(new AppManifestValidator.Problem("/app_code", "app_code_mismatch"));
            if (!row.version().equals(manifest.get("version")))
                problems.add(new AppManifestValidator.Problem("/version", "version_mismatch"));
            if (row.categoryCode() == null || !row.categoryCode().equals(manifest.get("category_code")))
                problems.add(new AppManifestValidator.Problem("/category_code", "category_mismatch"));
        }
        return List.copyOf(problems);
    }

    private Category requireCategory(String code) {
        return jdbc.sql("SELECT * FROM market.app_category WHERE code=:code").param("code", code)
                .query(this::mapCategory).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found"));
    }

    private Category mapCategory(ResultSet row, int index) throws SQLException {
        return new Category(row.getString("code"), row.getString("name"), row.getString("description"),
                row.getInt("sort_order"), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(), row.getLong("version"));
    }

    private VersionRow requireVersion(UUID id) {
        return jdbc.sql("""
                SELECT v.version,v.status,v.manifest,a.code AS app_code,a.category_code
                FROM market.app_version v JOIN market.app_package a ON a.id=v.app_id WHERE v.id=:id
                """).param("id", id).query((row, index) -> new VersionRow(row.getString("version"),
                        row.getString("status"), row.getString("manifest"), row.getString("app_code"),
                        row.getString("category_code"))).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "app version not found"));
    }

    private Map<String, Object> readManifest(String source) {
        return source == null ? null : json.readValue(source, new TypeReference<Map<String, Object>>() {});
    }

    private void platform(String operation) { isolation.requirePlatformContext(operation, PlatformDuty.SYSTEM_ADMIN); }
    private static void requireCode(String code) {
        if (code == null || !CODE.matcher(code).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid category code");
    }
    private static void requireName(CategoryWrite request) {
        if (request == null || request.name() == null || request.name().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category name required");
    }
    private record VersionRow(String version, String status, String manifest, String appCode, String categoryCode) {}

    public static final class ManifestValidationException extends RuntimeException {
        private final List<AppManifestValidator.Problem> problems;
        public ManifestValidationException(List<AppManifestValidator.Problem> problems) {
            super("invalid application manifest");
            this.problems = List.copyOf(problems);
        }
        public List<AppManifestValidator.Problem> problems() { return problems; }
    }
}
