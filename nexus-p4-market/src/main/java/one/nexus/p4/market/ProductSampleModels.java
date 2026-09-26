package one.nexus.p4.market;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC9–PC13: minimal "open a sample" payloads for the current product (platform configuration completeness).
 * Everything here is derived from the product's stored configuration; nothing is tenant business data,
 * money, or an invented metric.
 */
public final class ProductSampleModels {
    private ProductSampleModels() {}

    public record SampleColumn(String key, String label, String type, boolean required) {}

    /**
     * PC9: the ledger a tenant's filled form would land in. Columns come from the product's bound published
     * form template version; {@code rows} is always empty because the platform holds no tenant records.
     */
    public record LedgerSample(UUID productDefinitionId, String productName, String source,
            UUID formTemplateVersionId, String formTemplateName, Integer formTemplateVersion,
            List<SampleColumn> columns, List<Map<String, Object>> rows, String note) {
        public static final String SOURCE = "FORM_TEMPLATE_VERSION";
        public static final String NOTE = "列来自产品绑定的已发布表单字段。平台不保存租户业务数据，所以这里没有记录；"
                + "租户开通后，在该租户工作室的「台账」查看真实记录。";
        public static final String MISSING_NOTE = "读不到产品绑定的表单版本，暂无样例列。";

        public LedgerSample {
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
        }
    }
}