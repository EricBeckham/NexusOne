package one.nexus.p3.flow;

import java.util.List;
import one.nexus.p3.flow.ProcessRecords.TodoView;
import org.flowable.engine.TaskService;
import org.flowable.engine.task.Comment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Decision notes live on the Flowable task comment, not a new business column.
 * V553 would have to merge between V552 and V601; this store keeps K17 free of Flyway.
 */
@Service
@ConditionalOnProperty(name = "nexus.flow.engine")
public class ApprovalCommentStore {
    private final TaskService taskService;

    public ApprovalCommentStore(TaskService taskService) {
        this.taskService = taskService;
    }

    public void save(String flowableTaskId, String flowableInstanceId, String comment) {
        taskService.addComment(flowableTaskId, flowableInstanceId, comment);
    }

    public String latest(String flowableTaskId) {
        return find(flowableTaskId);
    }

    public TodoView attach(TodoView todo) {
        if (todo == null || todo.outcome() == null) {
            return todo;
        }
        String comment = find(todo.flowableTaskId());
        if (comment == null) {
            return todo;
        }
        return todo.withComment(comment);
    }

    private String find(String flowableTaskId) {
        if (flowableTaskId == null || flowableTaskId.isBlank()) {
            return null;
        }
        List<Comment> comments = taskService.getTaskComments(flowableTaskId);
        if (comments == null || comments.isEmpty()) {
            return null;
        }
        Comment latest = comments.get(comments.size() - 1);
        String message = latest.getFullMessage();
        if (message == null || message.isBlank()) {
            return null;
        }
        return message;
    }
}
