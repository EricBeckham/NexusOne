package one.nexus.p3.flow.api;

import java.util.List;
import java.util.UUID;
import one.nexus.p3.flow.ProcessRecords.ProcessInstanceView;
import one.nexus.p3.flow.ProcessRecords.TodoView;
import one.nexus.p3.flow.TodoQueryService;
import one.nexus.p3.infra.permission.TodoSurfaceGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Todo list / detail HTTP. Read is {@code process.task}/{@code read} through
 * {@link TodoSurfaceGuard} then the tenant-person filter. Isolation-only
 * hosts without the guard stay tenant-only. Approve / reject / withdraw stay
 * on {@code SequentialApprovalService}.
 */
@RestController
@ConditionalOnProperty(name = "nexus.flow.engine")
public class TodoController {
    private final TodoQueryService todos;
    private final ObjectProvider<TodoSurfaceGuard> surfaces;

    public TodoController(TodoQueryService todos, ObjectProvider<TodoSurfaceGuard> surfaces) {
        this.todos = todos;
        this.surfaces = surfaces;
    }

    @GetMapping({"/api/v1/todos/pending", "/api/v1/p3/todos"})
    public List<TodoView> pending() {
        requireTodoRead();
        return todos.pending();
    }

    @GetMapping("/api/v1/todos/done")
    public List<TodoView> done() {
        requireTodoRead();
        return todos.done();
    }

    @GetMapping("/api/v1/todos/initiated")
    public List<ProcessInstanceView> initiated() {
        requireTodoRead();
        return todos.initiated();
    }

    @GetMapping("/api/v1/todos/{todoId}")
    public TodoView get(@PathVariable UUID todoId) {
        requireTodoRead();
        return todos.get(todoId);
    }

    private void requireTodoRead() {
        TodoSurfaceGuard guard = surfaces.getIfAvailable();
        if (guard != null) {
            guard.requireTodoRead();
        }
    }
}
