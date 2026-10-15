import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TaskService {
    private final TaskRepository repository;
    private final List<Task> tasks;

    public TaskService(TaskRepository repository) throws IOException {
        this.repository = repository;
        this.tasks = repository.load();
    }

    public String add(String title) throws IOException {
        // TODO: trim title, reject empty or unsafe separators, choose max(id)+1,
        // add the task, persist, return "ADDED <id>".
        return "ERROR empty_title";
    }

    public String complete(int id) throws IOException {
        // TODO: find by id, reject missing/already done, mark done, persist.
        // Return "DONE <id>", "ERROR not_found", or "ERROR already_done".
        return "ERROR not_found";
    }

    public List<Task> list() {
        // TODO: return tasks sorted by id. Keep the list owned by this service.
        return new ArrayList<>(tasks);
    }
}
