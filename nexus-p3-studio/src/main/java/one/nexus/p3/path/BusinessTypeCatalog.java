package one.nexus.p3.path;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import one.nexus.p3.domain.FieldType;
import one.nexus.p3.domain.P3Exception;
import one.nexus.p3.port.LedgerRecordSink;
import one.nexus.p3.studio.form.WidgetType;

/**
 * G7 catalog: three distinct real business classes on the existing
 * ledger / form / sequential-approval stack. Leave is the G18 class;
 * expense and travel are the two additional classes.
 */
public final class BusinessTypeCatalog {
    public static final String LEAVE = "leave";
    public static final String EXPENSE = "expense";
    public static final String TRAVEL = "travel";

    private static final Map<String, TypeSpec> TYPES = new LinkedHashMap<>();

    static {
        put(new TypeSpec(
                LEAVE,
                "请假",
                "leave",
                "人事请假：类型、天数、申请人、事由。G18 已关闭的一类。",
                "hr",
                "人事",
                "leave",
                "请假台账",
                "leave_form",
                "请假单",
                List.of(
                        field("title", "请假类型", FieldType.TEXT, true, WidgetType.TEXT),
                        field("days", "天数", FieldType.INTEGER, true, WidgetType.NUMBER),
                        field("applicant", "申请人", FieldType.TEXT, true, WidgetType.PERSON),
                        field("reason", "事由", FieldType.TEXT, false, WidgetType.TEXTAREA)),
                Map.of("title", "年假", "days", 2, "applicant", "alice", "reason", "family")));
        put(new TypeSpec(
                EXPENSE,
                "费用报销",
                "expense",
                "财务报销：说明、金额、类别、收款人。与请假字段模型不同。",
                "fin",
                "财务",
                "expense",
                "报销台账",
                "expense_form",
                "报销单",
                List.of(
                        field("title", "费用说明", FieldType.TEXT, true, WidgetType.TEXT),
                        field("amount", "金额", FieldType.NUMERIC, true, WidgetType.NUMBER),
                        field("category", "费用类别", FieldType.TEXT, true, WidgetType.TEXT),
                        field("payee", "收款人", FieldType.TEXT, true, WidgetType.TEXT),
                        field("remark", "备注", FieldType.TEXT, false, WidgetType.TEXTAREA)),
                Map.of(
                        "title", "交通费",
                        "amount", 128.50,
                        "category", "交通",
                        "payee", "张三",
                        "remark", "地铁往返")));
        put(new TypeSpec(
                TRAVEL,
                "出差申请",
                "travel",
                "差旅：标题、目的地、天数、事由、申请人。与请假/报销字段模型不同。",
                "trip",
                "差旅",
                "travel",
                "出差台账",
                "travel_form",
                "出差单",
                List.of(
                        field("title", "出差标题", FieldType.TEXT, true, WidgetType.TEXT),
                        field("destination", "目的地", FieldType.TEXT, true, WidgetType.TEXT),
                        field("days", "出差天数", FieldType.INTEGER, true, WidgetType.NUMBER),
                        field("purpose", "事由", FieldType.TEXT, true, WidgetType.TEXTAREA),
                        field("applicant", "申请人", FieldType.TEXT, true, WidgetType.PERSON)),
                Map.of(
                        "title", "客户拜访",
                        "destination", "上海",
                        "days", 3,
                        "purpose", "项目评审",
                        "applicant", "alice")));
    }

    private BusinessTypeCatalog() {}

    public static Collection<TypeSpec> all() {
        return List.copyOf(TYPES.values());
    }

    public static TypeSpec require(String code) {
        return find(code).orElseThrow(() -> P3Exception.queryInvalid(
                "unknown business type: " + code + " (leave|expense|travel)"));
    }

    public static Optional<TypeSpec> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(TYPES.get(code.trim().toLowerCase()));
    }

    private static void put(TypeSpec spec) {
        TYPES.put(spec.code(), spec);
    }

    private static FieldSpec field(
            String code, String name, FieldType type, boolean required, WidgetType widget) {
        return new FieldSpec(code, name, type, required, widget);
    }

    public record FieldSpec(
            String fieldCode,
            String displayName,
            FieldType fieldType,
            boolean required,
            WidgetType widgetType) {}

    public record TypeSpec(
            String code,
            String displayName,
            String className,
            String description,
            String appCode,
            String appName,
            String ledgerCode,
            String ledgerName,
            String formCode,
            String formName,
            List<FieldSpec> fields,
            Map<String, Object> sampleValues) {

        public FieldSpec approvalStatus() {
            return new FieldSpec(
                    LedgerRecordSink.APPROVAL_STATUS,
                    "审批状态",
                    FieldType.TEXT,
                    false,
                    WidgetType.TEXT);
        }
    }
}
