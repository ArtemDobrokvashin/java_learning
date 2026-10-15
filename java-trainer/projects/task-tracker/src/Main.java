import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** CLI entry point. Implement domain rules in TaskService, storage in TaskRepository. */
public class Main {
    public static void main(String[] args) throws Exception {
        Path data = args.length == 2 && "--data".equals(args[0])
            ? Path.of(args[1]) : Path.of("tasks.tsv");
        TaskService service = new TaskService(new TaskRepository(data));
        BufferedReader input = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = input.readLine()) != null) {
            if (line.equals("exit")) {
                System.out.println("BYE");
                break;
            } else if (line.equals("list")) {
                var tasks = service.list();
                if (tasks.isEmpty()) System.out.println("EMPTY");
                for (Task task : tasks) {
                    System.out.println(task.getId() + "|" + (task.isDone() ? "done" : "todo")
                        + "|" + task.getTitle());
                }
            } else if (line.equals("add") || line.startsWith("add ")) {
                System.out.println(service.add(line.length() <= 4 ? "" : line.substring(4)));
            } else if (line.startsWith("done ")) {
                try {
                    int id = Integer.parseInt(line.substring(5).trim());
                    System.out.println(service.complete(id));
                } catch (NumberFormatException exception) {
                    System.out.println("ERROR invalid_command");
                }
            } else {
                System.out.println("ERROR invalid_command");
            }
        }
    }
}
