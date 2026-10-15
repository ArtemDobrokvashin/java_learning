import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TaskRepository {
    private final Path file;

    public TaskRepository(Path file) { this.file = file; }

    /** Read id<TAB>done<TAB>title. A missing file means an empty list. */
    public List<Task> load() throws IOException {
        // TODO: Files.readAllLines(file), split("\\t", 3), parse id/done.
        // Decide what to do with a corrupted row and document that decision.
        return new ArrayList<>();
    }

    /** Save the complete list in UTF-8 after a successful change. */
    public void save(List<Task> tasks) throws IOException {
        // TODO: write to a temporary file and move it into place to avoid
        // losing data on partial writes. Use Files.move(..., REPLACE_EXISTING).
    }
}
