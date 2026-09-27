package one.nexus.p4.market;

import java.time.Instant;
import java.util.UUID;

/**
 * DPO-L4: print template metadata. {@code BUILTIN} is the code/classpath layout (never stored, never editable);
 * {@code COPY} rows live in {@code market.print_template} (V708) and carry metadata only — their slots are the
 * source template's slots. A drag / layout engine is later.
 */
public final class PrintTemplateModels {
    private PrintTemplateModels() {}

    public static final String KIND_BUILTIN = "BUILTIN";
    public static final String KIND_COPY = "COPY";
    public static final String NOTE_BUILTIN = "内置版式，只读；复制后可改名称和纸张。";
    public static final String NOTE_COPY = "内置版式的副本：只有名称和纸张是自己的，槽位沿用来源模板；拖拽排版 later。";

    public record PrintTemplate(UUID id, String code, String name, String kind, String sourceTemplateCode,
            String paper, String orientation, boolean editable, Instant createdAt, UUID createdBy, long version,
            String note) {}

    /** POST body; everything optional except that only the built-in template can be the source. */
    public record PrintTemplateCopy(String sourceTemplateCode, String name, String paper, String orientation) {}
}
