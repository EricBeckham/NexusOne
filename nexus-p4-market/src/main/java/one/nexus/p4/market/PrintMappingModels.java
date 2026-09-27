package one.nexus.p4.market;

import java.util.List;
import java.util.UUID;

/**
 * CF-R3: print field→slot mapping contract + paper size enum. Static / in-memory only (no Flyway).
 * For CF-R1/R2 shells and a later designer — not a drag designer itself.
 */
public final class PrintMappingModels {
    private PrintMappingModels() {}

    public record PaperSize(String code, String label, int widthMm, int heightMm) {}

    public record Orientation(String code, String label) {}

    public record Slot(String id, String label, String section, List<String> accepts, boolean repeatable) {
        public Slot {
            accepts = List.copyOf(accepts);
        }
    }

    public record BuiltinTemplate(String code, String name, String defaultPaper, String defaultOrientation,
            List<Slot> slots) {
        public BuiltinTemplate {
            slots = List.copyOf(slots);
        }
    }

    public record FieldSlotMapping(String fieldKey, String slotId) {}

    public record MappingRules(boolean oneFieldOneSlot, String unmappedSpillSlotId, String description) {}

    /** Static contract served from classpath JSON (+ mirrored under docs/planning/fixtures). */
    public record PrintMappingContract(String contractVersion, String source, String note,
            List<PaperSize> paperSizes, List<Orientation> orientations, BuiltinTemplate builtinTemplate,
            List<FieldSlotMapping> defaultFieldMappings, MappingRules mappingRules) {
        public static final String SOURCE = "STATIC";

        public PrintMappingContract {
            paperSizes = List.copyOf(paperSizes);
            orientations = List.copyOf(orientations);
            defaultFieldMappings = List.copyOf(defaultFieldMappings);
        }
    }

    /**
     * Per-product mapping view. {@code source=DEFAULT} means derived from the static contract;
     * {@code STORED} means saved in {@code market.print_mapping} (DPO-L3, survives restarts);
     * {@code IN_MEMORY} means a process-local override (lost on restart; only the in-memory test store).
     * CF-RD3: {@code revision} is 0 for the contract default and +1 per accepted PUT. With the JDBC store
     * (DPO-L3) it is durable and every revision is kept in {@code market.print_mapping_revision}.
     */
    public record ProductPrintMapping(UUID productDefinitionId, String source, String paper, String orientation,
            String templateCode, List<FieldSlotMapping> mappings, String note, long revision) {
        public static final String SOURCE_DEFAULT = "DEFAULT";
        public static final String SOURCE_STORED = "STORED";
        public static final String SOURCE_IN_MEMORY = "IN_MEMORY";
        public static final String NOTE_DEFAULT = "来自 CF-R3 静态契约默认映射；还没有保存过。";
        public static final String NOTE_STORED = "已保存到数据库，重启后仍在；每次保存修订号 +1，可查看修订历史。";
        public static final String NOTE_IN_MEMORY = "进程内覆盖（JVM 内存，重启丢失）。";

        public ProductPrintMapping {
            mappings = List.copyOf(mappings);
        }
    }

    /** DPO-L3: one accepted save, read from {@code market.print_mapping_revision} (newest first). */
    public record PrintMappingRevision(UUID productDefinitionId, long revision, String templateCode, String paper,
            String orientation, List<FieldSlotMapping> mappings, java.time.Instant savedAt, UUID savedBy) {
        public PrintMappingRevision {
            mappings = List.copyOf(mappings);
        }
    }

    /**
     * PUT body: optional paper/orientation; mappings replace the saved mapping.
     * CF-RD3: {@code expectedRevision}, when present, must equal the current revision or the PUT is refused (409),
     * so an editor never silently overwrites a mapping it has not seen.
     */
    public record ProductPrintMappingPut(String paper, String orientation, List<FieldSlotMapping> mappings,
            Long expectedRevision) {
        public ProductPrintMappingPut {
            mappings = mappings == null ? List.of() : List.copyOf(mappings);
        }

        public ProductPrintMappingPut(String paper, String orientation, List<FieldSlotMapping> mappings) {
            this(paper, orientation, mappings, null);
        }
    }
}