package one.nexus.p4.market;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import one.nexus.p1.domain.PlatformDuty;
import one.nexus.p1.port.IsolationGuard;
import one.nexus.p4.market.MarketModels.EntitlementSpec;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** PLB5 product composition and product-native SKU definition. Installation is PLB7. */
@Service
@DependsOn("flyway")
public class ProductCatalogService {
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,23}");
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final IsolationGuard isolation;
    private AppInstanceService instances;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void useInstances(AppInstanceService instances) { this.instances = instances; }

    public record Product(UUID id, String code, String name, String description, String status,
            Instant createdAt, Instant updatedAt, long version, boolean autoUpgradeMinor) {}
    public record ProductWrite(String code, String name, String description, Long version) {}
    public record ProductApp(UUID appId, UUID versionId, int sortOrder) {}
    public record ProductView(Product product, List<ProductApp> apps, List<String> skuCodes) {}
    public record SkuWrite(String code, String name, Map<String, EntitlementSpec> entitlements) {}

    public ProductCatalogService(JdbcClient jdbc, ObjectMapper json, IsolationGuard isolation) {
        this.jdbc = jdbc;
        this.json = json;
        this.isolation = isolation;
    }

    @Transactional(readOnly = true)
    public List<Product> list() {
        platform("product.list");
        return jdbc.sql("SELECT * FROM market.product ORDER BY created_at DESC,id").query(this::mapProduct).list();
    }

    @Transactional(readOnly = true)
    public ProductView get(UUID id) {
        platform("product.get");
        return view(id);
    }

    @Transactional
    public ProductView create(ProductWrite request) {
        platform("product.create");
        requireWrite(request, true);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        int changed = jdbc.sql("""
                INSERT INTO market.product(id,code,name,description,status,created_at,updated_at,version)
                VALUES(:id,:code,:name,:description,'DRAFT',:now,:now,1)
                ON CONFLICT(code) DO NOTHING
                """).param("id", id).param("code", request.code()).param("name", request.name().trim())
                .param("description", request.description()).param("now", java.sql.Timestamp.from(now)).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "product code already exists");
        return view(id);
    }

    @Transactional
    public ProductView update(UUID id, ProductWrite request) {
        platform("product.update");
        requireWrite(request, false);
        if (request.version() == null || request.version() < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "version required");
        Product old = requireProduct(id);
        if (request.code() != null && !old.code().equals(request.code()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "product code is immutable");
        int changed = jdbc.sql("""
                UPDATE market.product SET name=:name,description=:description,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE id=:id AND status='DRAFT' AND version=:version
                """).param("name", request.name().trim()).param("description", request.description())
                .param("id", id).param("version", request.version()).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "product is published or version changed");
        return view(id);
    }

    @Transactional
    public ProductView replaceApps(UUID id, long expectedVersion, List<ProductApp> apps) {
        platform("product.apps.update");
        if (apps == null || apps.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product needs at least one app");
        Set<UUID> appIds = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (ProductApp app : apps) {
            if (app == null || app.appId() == null || app.versionId() == null || !appIds.add(app.appId())
                    || !orders.add(app.sortOrder())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate or incomplete product app");
            Integer valid = jdbc.sql("""
                    SELECT 1 FROM market.app_version WHERE id=:version AND app_id=:app AND status='PUBLISHED'
                    AND manifest IS NOT NULL
                    """).param("version", app.versionId()).param("app", app.appId())
                    .query(Integer.class).optional().orElse(null);
            if (valid == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "app version is not a published manifest");
        }
        int changed = jdbc.sql("""
                UPDATE market.product SET updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE id=:id AND status='DRAFT' AND version=:version
                """).param("id", id).param("version", expectedVersion).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "product is published or version changed");
        jdbc.sql("DELETE FROM market.product_app WHERE product_id=:id").param("id", id).update();
        for (ProductApp app : apps) {
            jdbc.sql("""
                    INSERT INTO market.product_app(product_id,app_id,app_version_id,sort_order)
                    VALUES(:product,:app,:version,:sort)
                    """).param("product", id).param("app", app.appId()).param("version", app.versionId())
                    .param("sort", app.sortOrder()).update();
        }
        return view(id);
    }

    @Transactional
    public ProductView publish(UUID id, long expectedVersion) {
        platform("product.publish");
        int changed = jdbc.sql("""
                UPDATE market.product p SET status='PUBLISHED',updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE p.id=:id AND p.status IN ('DRAFT','ARCHIVED') AND p.version=:version
                  AND EXISTS(SELECT 1 FROM market.product_app pa WHERE pa.product_id=p.id AND pa.app_version_id IS NOT NULL)
                  AND NOT EXISTS(SELECT 1 FROM market.product_app pa
                    LEFT JOIN market.app_version av ON av.id=pa.app_version_id
                    WHERE pa.product_id=p.id AND (av.id IS NULL OR av.status<>'PUBLISHED' OR av.manifest IS NULL))
                """).param("id", id).param("version", expectedVersion).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "product not ready or version changed");
        return view(id);
    }

    /** PLB6: take a published product off the catalog. The row stays so it can be published again. */
    @Transactional
    public ProductView archive(UUID id, long expectedVersion) {
        platform("product.archive");
        int changed = jdbc.sql("""
                UPDATE market.product SET status='ARCHIVED', updated_at=CURRENT_TIMESTAMP, version=version+1
                WHERE id=:id AND status='PUBLISHED' AND version=:version
                """).param("id", id).param("version", expectedVersion).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "product is not published or version changed");
        return view(id);
    }

    @Transactional
    public String createSku(UUID productId, SkuWrite request) {
        platform("product.sku.create");
        if (request == null || request.code() == null || request.code().isBlank() || request.code().length() > 80
                || request.name() == null || request.name().isBlank() || request.entitlements() == null
                || request.entitlements().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU code, name and entitlements required");
        Product product = requireProduct(productId);
        if (!"PUBLISHED".equals(product.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "product is not published");
        int changed = jdbc.sql("""
                INSERT INTO market.sku(code,app_id,product_id,name,entitlements)
                VALUES(:code,NULL,:product,:name,CAST(:entitlements AS jsonb))
                ON CONFLICT(code) DO NOTHING
                """).param("code", request.code()).param("product", productId).param("name", request.name().trim())
                .param("entitlements", json.writeValueAsString(request.entitlements())).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU code already exists");
        return request.code();
    }

    /** PLB8 / D3. Turning the switch on catches INHERIT instances up to the newest same-major published version. */
    @Transactional
    public Product setAutoUpgradeMinor(UUID id, boolean enabled, long expectedVersion) {
        platform("product.auto-upgrade");
        int changed = jdbc.sql("""
                UPDATE market.product SET auto_upgrade_minor=:enabled, updated_at=CURRENT_TIMESTAMP, version=version+1
                WHERE id=:id AND version=:version
                """).param("enabled", enabled).param("id", id).param("version", expectedVersion).update();
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "product version changed or product not found");
        if (enabled && instances != null) instances.catchUpProduct(id);
        return requireProduct(id);
    }

    private ProductView view(UUID id) {
        Product product = requireProduct(id);
        List<ProductApp> apps = jdbc.sql("""
                SELECT app_id,app_version_id,sort_order FROM market.product_app
                WHERE product_id=:id ORDER BY sort_order,app_id
                """).param("id", id).query((row, n) -> new ProductApp(
                        row.getObject("app_id", UUID.class), row.getObject("app_version_id", UUID.class),
                        row.getInt("sort_order"))).list();
        List<String> skus = jdbc.sql("SELECT code FROM market.sku WHERE product_id=:id ORDER BY code")
                .param("id", id).query(String.class).list();
        return new ProductView(product, apps, skus);
    }

    private Product requireProduct(UUID id) {
        return jdbc.sql("SELECT * FROM market.product WHERE id=:id").param("id", id)
                .query(this::mapProduct).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
    }
    private Product mapProduct(ResultSet row, int index) throws SQLException {
        return new Product(row.getObject("id", UUID.class), row.getString("code"), row.getString("name"),
                row.getString("description"), row.getString("status"), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(), row.getLong("version"), row.getBoolean("auto_upgrade_minor"));
    }
    private void platform(String operation) { isolation.requirePlatformContext(operation, PlatformDuty.SYSTEM_ADMIN); }
    private static void requireWrite(ProductWrite request, boolean creating) {
        if (request == null || request.name() == null || request.name().isBlank()
                || (creating && (request.code() == null || !CODE.matcher(request.code()).matches())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product code and name required");
    }
}
