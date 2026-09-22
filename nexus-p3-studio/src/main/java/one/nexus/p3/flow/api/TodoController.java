package one.nexus.p3.flow.api;

import java.util.List;
import java.util.UUID;
import one.nexus.p3.flow.ProcessRecords.ProcessInstanceView;
import one.nexus.p3.flow.ProcessRecords.TodoView;
import one.nexus.p3.flow.TodoQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "nexus.flow.engine")
public class TodoController {
    private final TodoQueryService todos;

    public TodoController(TodoQueryService todos) {
        this.todos = todos;
    }

    @GetMapping({"/api/v1/todos/pending", "/api/v1/p3/todos"})
    public List<TodoView> pending() {
        return todos.pending();
    }

    @GetMapping("/api/v1/todos/done")
    public List<TodoView> done() {
        return todos.done();
    }

    @GetMapping("/api/v1/todos/initiated")
    public List<ProcessInstanceView> initiated() {
        return todos.initiated();
    }

    @GetMapping("/api/v1/todos/{todoId}")
    public TodoView get(@PathVariable UUID todoId) {
        return todos.get(todoId);
    }
}
