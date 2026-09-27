package one.nexus.p4.market;

import java.util.List;
import java.util.UUID;

/**
 * DPO-L5: read-only print preview payload for the existing HTML preview. {@code dataSource=NONE} means no real
 * application is bound (every value is null); {@code APPLICATION} means values come from one application of the
 * caller's own tenant. A value the preview cannot resolve is null with a {@code valueNote} saying why.
 */
public final class PrintPreviewModels {
    private PrintPreviewModels() {}

    public static final String SOURCE_NONE = "NONE";
    public static final String SOURCE_APPLICATION = "APPLICATION";
    public static final String NOTE_NONE = "未绑定真实单据：字段值留空，只预览版式。";
    public static final String NOTE_APPLICATION = "按本租户的一张申请单填充字段值；解析不了的值留空并写明原因。只读，不是 PDF。";

    public record PreviewField(String key, String label, String type, String slotId, String value, String valueNote) {}

    public record PreviewSection(String key, String label, List<PreviewField> fields) {
        public PreviewSection {
            fields = List.copyOf(fields);
        }
    }

    public record PrintPreview(UUID productDefinitionId, UUID applicationId, String dataSource, String templateCode,
            String paper, String orientation, String title, String mappingSource, long mappingRevision,
            List<PreviewSection> sections, String note) {
        public PrintPreview {
            sections = List.copyOf(sections);
        }
    }
}
