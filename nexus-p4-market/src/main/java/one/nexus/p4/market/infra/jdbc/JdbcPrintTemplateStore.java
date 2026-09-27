package one.nexus.p4.market.infra.jdbc;

import static one.nexus.common.time.Utc.sqlTimestamp;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import one.nexus.p4.market.PrintTemplateModels;
import one.nexus.p4.market.PrintTemplateModels.PrintTemplate;
import one.nexus.p4.market.PrintTemplateStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** DPO-L4: print template copies in {@code market.print_template} (V708). */
@Repository
public class JdbcPrintTemplateStore implements PrintTemplateStore {
    private final JdbcClient jdbc;

    public JdbcPrintTemplateStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PrintTemplate> copies() {
        return jdbc.sql("SELECT * FROM market.print_template ORDER BY created_at, code")
                .query((r, n) -> {
                    Timestamp created = r.getTimestamp("created_at");
                    return new PrintTemplate(r.getObject("id", UUID.class), r.getString("code"), r.getString("name"),
                            PrintTemplateModels.KIND_COPY, r.getString("source_template_code"), r.getString("paper"),
                            r.getString("orientation"), true, created == null ? null : created.toInstant(),
                            r.getObject("created_by", UUID.class), r.getLong("version"), PrintTemplateModels.NOTE_COPY);
                })
                .list();
    }

    @Override
    public void insert(PrintTemplate t) {
        jdbc.sql("""
                INSERT INTO market.print_template(id,code,name,source_template_code,paper,orientation,
                    created_at,created_by,updated_at,version)
                VALUES(:id,:code,:name,:source,:paper,:orientation,:at,:by,:at,:version)
                """)
                .param("id", t.id()).param("code", t.code()).param("name", t.name())
                .param("source", t.sourceTemplateCode()).param("paper", t.paper()).param("orientation", t.orientation())
                .param("at", sqlTimestamp(t.createdAt())).param("by", t.createdBy()).param("version", t.version())
                .update();
    }
}
