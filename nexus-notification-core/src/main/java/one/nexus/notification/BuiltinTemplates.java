package one.nexus.notification;

import java.util.List;

public final class BuiltinTemplates {
    private BuiltinTemplates() {}

    public static final String APPROVAL_ASSIGNED = "approval.task.assigned";
    public static final String APPROVAL_APPROVED = "approval.task.approved";
    public static final String APPROVAL_REJECTED = "approval.task.rejected";

    public static List<NotificationTemplate> all() {
        return List.of(
                new NotificationTemplate(
                        APPROVAL_ASSIGNED,
                        NotificationChannel.IN_APP,
                        "待审批：{taskTitle}",
                        "{actor} 提交了「{taskTitle}」，请办理。",
                        List.of("taskTitle", "actor")),
                new NotificationTemplate(
                        APPROVAL_APPROVED,
                        NotificationChannel.IN_APP,
                        "审批已通过：{taskTitle}",
                        "「{taskTitle}」已由 {actor} 通过。",
                        List.of("taskTitle", "actor")),
                new NotificationTemplate(
                        APPROVAL_REJECTED,
                        NotificationChannel.IN_APP,
                        "审批已驳回：{taskTitle}",
                        "「{taskTitle}」已被 {actor} 驳回。",
                        List.of("taskTitle", "actor"))
        );
    }
}
