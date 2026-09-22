package one.nexus.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import one.nexus.cache.IsolationSupport;
import one.nexus.common.error.ErrorCode;
import one.nexus.common.error.NexusException;
import one.nexus.event.OutboxAppendCommand;
import one.nexus.isolation.IsolationKernel;
import one.nexus.isolation.audit.CollectingAuditSink;
import one.nexus.isolation.context.AccessContextHolder;
import one.nexus.isolation.query.TableRegistry;
import one.nexus.notification.infra.InMemoryNotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
    private static final String TENANT = "11111111-1111-4111-8111-111111111111";
    private final IsolationKernel kernel = IsolationKernel.create(
            new TableRegistry(), Set.of("notify.ops"), new CollectingAuditSink());

    @AfterEach
    void clear() {
        AccessContextHolder.clear();
    }

    @Test
    void enqueueRendersTemplateAndRecordsOutbox() {
        IsolationSupport.bindTenant(kernel, TENANT, "approver-1", "mem-a", "trace-n");
        InMemoryNotificationRepository repo = new InMemoryNotificationRepository();
        RecordingOutboxPort outbox = new RecordingOutboxPort();
        NotificationService service = new NotificationService(
                new TemplateCatalog(), repo, outbox, Clock.systemUTC());
        ApprovalNotificationPublisher publisher = new ApprovalNotificationPublisher(service);
        Notification notification = publisher.taskAssigned("approver-1", "请假单", "张三");
        assertEquals(NotificationStatus.PENDING, notification.status());
        assertEquals("待审批：请假单", notification.title());
        assertTrue(notification.body().contains("张三"));
        assertEquals(1, outbox.recorded().size());
        assertEquals(OutboxAppendCommand.NOTIFY_INBOX, outbox.recorded().get(0).destination());
        assertEquals("notify.in_app.deliver", outbox.recorded().get(0).eventType());
        assertEquals("notification", outbox.recorded().get(0).aggregateType());
        assertEquals(notification.id(), outbox.recorded().get(0).aggregateId());
    }

    @Test
    void emailAndSmsAreRejected() {
        IsolationSupport.bindTenant(kernel, TENANT, "user-a", "mem-a", "trace-n");
        NotificationService service = new NotificationService(
                new TemplateCatalog(),
                new InMemoryNotificationRepository(),
                new RecordingOutboxPort(),
                Clock.systemUTC());
        NexusException email = assertThrows(NexusException.class, () ->
                service.enqueue("approval.task.assigned", "user-a", NotificationChannel.EMAIL, Map.of("taskTitle", "x", "actor", "y")));
        assertEquals(ErrorCode.NOTIFY_CHANNEL_UNSUPPORTED, email.errorCode());
        NexusException sms = assertThrows(NexusException.class, () ->
                service.enqueue("approval.task.assigned", "user-a", NotificationChannel.SMS, Map.of("taskTitle", "x", "actor", "y")));
        assertEquals(ErrorCode.NOTIFY_CHANNEL_UNSUPPORTED, sms.errorCode());
    }

    @Test
    void immediateOutboxMarksDelivered() {
        IsolationSupport.bindTenant(kernel, TENANT, "user-a", "mem-a", "trace-n");
        InMemoryNotificationRepository repo = new InMemoryNotificationRepository();
        ImmediateOutboxPort outbox = new ImmediateOutboxPort(List.of(new InAppDeliveryHandler(repo)));
        NotificationService service = new NotificationService(
                new TemplateCatalog(), repo, outbox, Clock.systemUTC());
        Notification notification = service.enqueue(
                BuiltinTemplates.APPROVAL_APPROVED,
                "user-a",
                NotificationChannel.IN_APP,
                Map.of("taskTitle", "合同", "actor", "李四"));
        assertEquals(NotificationStatus.DELIVERED, notification.status());
    }
}
