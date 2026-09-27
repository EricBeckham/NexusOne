package one.nexus.p4.market.infra.jdbc;

import static one.nexus.common.time.Utc.sqlTimestamp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import one.nexus.p4.market.PrintMappingModels.FieldSlotMapping;
import one.nexus.p4.market.PrintMappingModels.PrintMappingRevision;
import one.nexus.p4.market.PrintMappingModels.ProductPrintMapping;
import one.nexus.p4.market.PrintMappingStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * DPO-L3: print mappings in {@code market.print_mapping} + {@code market.print_mapping_revision} (V707).
 * The revision check is part of the write itself ({@code UPDATE … WHERE revision = expected}, or an insert that
 * only succeeds when nothing is stored), so two instances cannot overwrite each other either.
 * Mapping JSON uses fixed keys {@code field_key} / {@code slot_id}, independent of the product mapper's naming.
 */
@Repository
public class JdbcPrintMappingStore implements PrintMappingStore {
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    public JdbcPrintMappingStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ProductPrintMapping> find(UUID productDefinitionId) {
        return jdbc.sql("SELECT * FROM market.print_mapping WHERE product_definition_id=:id")
                .param("id", productDefinitionId)
                .query((r, n) -> new ProductPrintMapping(uuid(r, "product_definition_id"), source(),
                        r.getString("paper"), r.getString("orientation"), r.getString("template_code"),
                        read(r.getString("mappings")), note(), r.getLong("revision")))
                .optional();
    }

    @Override
    public boolean saveIfRevision(ProductPrintMapping next, long expectedCurrent, UUID savedBy, Instant savedAt) {
        String mappings = write(next.mappings());
        int rows = expectedCurrent == 0
                ? jdbc.sql("""
                        INSERT INTO market.print_mapping(product_definition_id,template_code,paper,orientation,
                            mappings,revision,updated_at,updated_by)
                        VALUES(:id,:template,:paper,:orientation,CAST(:mappings AS jsonb),:revision,:at,:by)
                        ON CONFLICT (product_definition_id) DO NOTHING
                        """)
                        .param("id", next.productDefinitionId()).param("template", next.templateCode())
                        .param("paper", next.paper()).param("orientation", next.orientation())
                        .param("mappings", mappings).param("revision", next.revision())
                        .param("at", sqlTimestamp(savedAt)).param("by", savedBy).update()
                : jdbc.sql("""
                        UPDATE market.print_mapping SET template_code=:template,paper=:paper,orientation=:orientation,
                            mappings=CAST(:mappings AS jsonb),revision=:revision,updated_at=:at,updated_by=:by
                        WHERE product_definition_id=:id AND revision=:expected
                        """)
                        .param("id", next.productDefinitionId()).param("template", next.templateCode())
                        .param("paper", next.paper()).param("orientation", next.orientation())
                        .param("mappings", mappings).param("revision", next.revision())
                        .param("at", sqlTimestamp(savedAt)).param("by", savedBy)
                        .param("expected", expectedCurrent).update();
        if (rows != 1) return false;
        jdbc.sql("""
                INSERT INTO market.print_mapping_revision(product_definition_id,revision,template_code,paper,
                    orientation,mappings,saved_at,saved_by)
                VALUES(:id,:revision,:template,:paper,:orientation,CAST(:mappings AS jsonb),:at,:by)
                """)
                .param("id", next.productDefinitionId()).param("revision", next.revision())
                .param("template", next.templateCode()).param("paper", next.paper())
                .param("orientation", next.orientation()).param("mappings", mappings)
                .param("at", sqlTimestamp(savedAt)).param("by", savedBy).update();
        return true;
    }

    @Override
    public List<PrintMappingRevision> history(UUID productDefinitionId, int limit) {
        return jdbc.sql("""
                SELECT * FROM market.print_mapping_revision WHERE product_definition_id=:id
                ORDER BY revision DESC LIMIT :limit
                """)
                .param("id", productDefinitionId).param("limit", limit)
                .query((r, n) -> new PrintMappingRevision(uuid(r, "product_definition_id"), r.getLong("revision"),
                        r.getString("template_code"), r.getString("paper"), r.getString("orientation"),
                        read(r.getString("mappings")), instant(r, "saved_at"), uuid(r, "saved_by")))
                .list();
    }

    @Override
    public String source() {
        return ProductPrintMapping.SOURCE_STORED;
    }

    @Override
    public String note() {
        return ProductPrintMapping.NOTE_STORED;
    }

    static String write(List<FieldSlotMapping> mappings) {
        List<Map<String, String>> rows = mappings.stream().map(m -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("field_key", m.fieldKey());
            row.put("slot_id", m.slotId());
            return row;
        }).toList();
        return JSON.writeValueAsString(rows);
    }

    static List<FieldSlotMapping> read(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<Map<String, String>> rows = JSON.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        return rows.stream().map(row -> new FieldSlotMapping(row.get("field_key"), row.get("slot_id"))).toList();
    }

    private static UUID uuid(ResultSet r, String column) throws SQLException {
        return r.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        Timestamp value = r.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
