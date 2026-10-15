package ru.javada.taskapi;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskRepository repository;

    public TaskController(TaskRepository repository) { this.repository = repository; }

    @GetMapping
    public List<Task> list() { return repository.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<Task> get(@PathVariable Long id) {
        return repository.findById(id).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Task> create(@Valid @RequestBody CreateTaskRequest request) {
        Task saved = repository.save(new Task(request.title().trim()));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // TODO: PATCH /{id}/done -> 200 + updated task, or 404 for missing ID.
    // TODO: DELETE /{id} -> 204, or 404. Then move business logic into a service.
    // TODO: return response DTOs rather than persistence entities.
}
