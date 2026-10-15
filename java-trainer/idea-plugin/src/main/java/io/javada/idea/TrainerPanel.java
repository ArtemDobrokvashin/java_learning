package io.javada.idea;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A non-modal tool window: every network operation is off the Swing thread. */
final class TrainerPanel implements Disposable {
    private static final String PORT_KEY = "javada.local.port";
    private final Project project;
    private final TrainerApi api = new TrainerApi();
    private final JPanel root = new JPanel(new BorderLayout(6, 6));
    private final JTextField port = new JTextField(5);
    private final JComboBox<String> filter = new JComboBox<>(new String[] {
            "Практика · 44", "Хард · 99", "Лаборатории · 5", "Все задачи"
    });
    private final JTextField search = new JTextField(18);
    private final DefaultListModel<TrainerApi.CodingTask> model = new DefaultListModel<>();
    private final JList<TrainerApi.CodingTask> list = new JList<>(model);
    private final JTextArea details = new JTextArea();
    private final JTextArea output = new JTextArea();
    private final JLabel status = new JLabel("Запустите Flask-сервер и нажмите «Обновить».");
    private final JButton open = new JButton("Открыть Main.java");
    private final JButton check = new JButton("Проверить решение");
    private final JButton save = new JButton("Сохранить черновик");
    private final JButton hint = new JButton("Подсказка");
    private final ScheduledThreadPoolExecutor io = new ScheduledThreadPoolExecutor(1, job -> {
        Thread worker = new Thread(job, "javada-idea-local-api");
        worker.setDaemon(true);
        return worker;
    });
    private final Map<String, ScheduledFuture<?>> queuedDrafts = new HashMap<>();
    private final Map<String, LightVirtualFile> editors = new HashMap<>();

    /** Separate virtual paths, but each editable file still has the Java name Main.java. */
    private static final class TaskFile extends LightVirtualFile {
        private final String taskId;
        TaskFile(String taskId, String text) {
            super("Main.java", JavaFileType.INSTANCE, text);
            this.taskId = taskId;
        }
        @Override public @NotNull String getPath() { return "/javada/" + taskId + "/Main.java"; }
    }

    private List<TrainerApi.CodingTask> catalog = List.of();
    private TrainerApi.Profile profile;
    private boolean showHint;
    private boolean disposed;

    TrainerPanel(Project project) {
        this.project = project;
        io.setRemoveOnCancelPolicy(true);
        port.setText(PropertiesComponent.getInstance(project).getValue(PORT_KEY, "8000"));
        root.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton refresh = new JButton("Обновить");
        toolbar.add(new JLabel("127.0.0.1 :"));
        toolbar.add(port);
        toolbar.add(refresh);
        toolbar.add(filter);
        toolbar.add(search);
        search.setToolTipText("Поиск по ID, модулю и теме");
        root.add(toolbar, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> target, Object value,
                    int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(target, value, index, selected, focused);
                if (value instanceof TrainerApi.CodingTask task) {
                    setText(task.id() + " · " + task.lessonTitle());
                    setToolTipText(task.moduleTitle() + " — " + task.section());
                }
                return this;
            }
        });
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(details.getFont().deriveFont(Font.PLAIN, 13f));
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        output.setText("Результат проверки появится здесь. Ошибки javac выводятся полностью.");
        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(details), new JScrollPane(output));
        right.setResizeWeight(.58);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(list), right);
        split.setResizeWeight(.42);
        root.add(split, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        actions.add(open);
        actions.add(check);
        actions.add(save);
        actions.add(hint);
        south.add(actions, BorderLayout.NORTH);
        south.add(status, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        refresh.addActionListener(event -> refresh());
        filter.addActionListener(event -> updateFilter());
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { updateFilter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { updateFilter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { updateFilter(); }
        });
        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) describe(); });
        open.addActionListener(event -> openEditor());
        check.addActionListener(event -> checkAnswer());
        save.addActionListener(event -> saveNow());
        hint.addActionListener(event -> { showHint = !showHint; describe(); });
        refresh();
    }

    JPanel component() { return root; }

    private int selectedPort() {
        try {
            int value = Integer.parseInt(port.getText().trim());
            TrainerApi.address(value, "/api/health");
            PropertiesComponent.getInstance(project).setValue(PORT_KEY, String.valueOf(value));
            return value;
        } catch (IllegalArgumentException wrong) {
            status.setText("Укажите порт локального Flask-сервера от 1 до 65535.");
            return -1;
        }
    }

    private void refresh() {
        int target = selectedPort();
        if (target < 0) return;
        status.setText("Подключаемся к локальному серверу…");
        io.execute(() -> {
            try {
                List<TrainerApi.CodingTask> tasks = api.catalog(target);
                TrainerApi.Profile saved = api.profile(target);
                onUi(() -> {
                    catalog = tasks;
                    profile = saved;
                    updateFilter();
                    status.setText("Подключено · " + tasks.size() + " задач · " + saved.xp() + " XP. Черновики общие с браузером.");
                });
            } catch (IOException | InterruptedException error) {
                onUi(() -> status.setText("Нет связи с Flask: " + error.getMessage()));
            }
        });
    }

    private void updateFilter() {
        String query = search.getText().trim().toLowerCase(java.util.Locale.ROOT);
        String group = (String) filter.getSelectedItem();
        String selectedId = list.getSelectedValue() == null ? "" : list.getSelectedValue().id();
        model.clear();
        List<TrainerApi.CodingTask> visible = new ArrayList<>();
        for (TrainerApi.CodingTask task : catalog) {
            boolean category = group == null || group.equals("Все задачи")
                    || (group.startsWith("Практика") && task.section().equals("Практика"))
                    || (group.startsWith("Хард") && task.section().equals("Необязательный хард"))
                    || (group.startsWith("Лаборатории") && task.section().equals("Лаборатории"));
            if (category && (task.id() + " " + task.moduleTitle() + " " + task.lessonTitle()
                    + " " + task.prompt()).toLowerCase(java.util.Locale.ROOT).contains(query)) visible.add(task);
        }
        visible.sort(Comparator.comparingInt(TrainerApi.CodingTask::moduleId)
                .thenComparing(TrainerApi.CodingTask::id));
        int selectedIndex = 0;
        for (TrainerApi.CodingTask task : visible) {
            if (task.id().equals(selectedId)) selectedIndex = model.size();
            model.addElement(task);
        }
        if (!model.isEmpty()) list.setSelectedIndex(selectedIndex);
        else details.setText("В выбранной категории задач нет. Измените фильтр или проверьте сервер.");
    }

    private void describe() {
        TrainerApi.CodingTask task = list.getSelectedValue();
        if (task == null) return;
        StringBuilder text = new StringBuilder(task.moduleTitle() + " / " + task.lessonTitle()
                + "\n" + task.section() + " · " + task.id() + "\n\n" + task.prompt());
        if (!task.sampleInput().isEmpty()) {
            text.append("\n\nПример ввода:\n").append(task.sampleInput())
                    .append("\nПример вывода:\n").append(task.sampleOutput());
        }
        if (showHint) text.append("\n\nПодсказка: ").append(task.hint());
        if (profile != null) {
            text.append("\n\n").append(profile.solved(task) ? "✓ Решено" : "Ожидает решения");
            if (profile.drafts().containsKey(task.id())) text.append(" · сохранён черновик");
            if (profile.badges().contains("mastery:" + task.id().replace("-hardcore", ""))) {
                text.append(" · достижение за хард");
            }
            text.append("\nОбщий опыт: ").append(profile.xp()).append(" XP");
        }
        details.setText(text.toString());
        details.setCaretPosition(0);
    }

    private Document documentFor(TrainerApi.CodingTask task) {
        LightVirtualFile file = editors.get(task.id());
        return file == null ? null : FileDocumentManager.getInstance().getDocument(file);
    }

    private void openEditor() {
        TrainerApi.CodingTask task = list.getSelectedValue();
        if (task == null) return;
        LightVirtualFile file = editors.get(task.id());
        boolean firstOpen = file == null;
        if (firstOpen) {
            String initial = profile == null ? task.starter()
                    : profile.drafts().getOrDefault(task.id(), task.starter());
            file = new TaskFile(task.id(), initial);
            editors.put(task.id(), file);
        }
        FileEditorManager.getInstance(project).openFile(file, true);
        if (firstOpen) {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document != null) {
                String id = task.id();
                document.addDocumentListener(new com.intellij.openapi.editor.event.DocumentListener() {
                    @Override public void documentChanged(@NotNull DocumentEvent event) {
                        if (!disposed) queueDraft(id, event.getDocument().getText());
                    }
                }, this);
            }
        }
        status.setText("Редактируйте Main.java, затем нажмите «Проверить решение». ");
    }

    private void queueDraft(String id, String code) {
        ScheduledFuture<?> earlier = queuedDrafts.get(id);
        if (earlier != null) earlier.cancel(false);
        int target = selectedPort();
        if (target < 0) return;
        queuedDrafts.put(id, io.schedule(() -> {
            try {
                TrainerApi.Profile saved = api.saveDraft(target, id, code);
                onUi(() -> { profile = saved; describe(); });
            } catch (IOException | InterruptedException error) {
                onUi(() -> status.setText("Не удалось записать черновик на сервер: " + error.getMessage()));
            }
        }, 750, TimeUnit.MILLISECONDS));
    }

    private void saveNow() {
        TrainerApi.CodingTask task = list.getSelectedValue();
        if (task == null) return;
        Document document = documentFor(task);
        if (document == null) { openEditor(); document = documentFor(task); }
        if (document == null) { status.setText("Не удалось открыть редактор Main.java."); return; }
        ScheduledFuture<?> queued = queuedDrafts.remove(task.id());
        if (queued != null) queued.cancel(false);
        int target = selectedPort();
        if (target < 0) return;
        String code = document.getText();
        io.execute(() -> {
            try {
                TrainerApi.Profile saved = api.saveDraft(target, task.id(), code);
                onUi(() -> { profile = saved; describe(); status.setText("Черновик сохранён на локальном сервере."); });
            } catch (IOException | InterruptedException error) {
                onUi(() -> status.setText("Не удалось сохранить черновик: " + error.getMessage()));
            }
        });
    }

    private void checkAnswer() {
        TrainerApi.CodingTask task = list.getSelectedValue();
        if (task == null) return;
        Document document = documentFor(task);
        if (document == null) { openEditor(); document = documentFor(task); }
        if (document == null) { status.setText("Не удалось открыть редактор Main.java."); return; }
        String code = document.getText();
        if (code.isBlank() || code.getBytes(StandardCharsets.UTF_8).length > 16_384) {
            status.setText("Введите решение на Java не длиннее 16 КБ."); return;
        }
        int target = selectedPort();
        if (target < 0) return;
        ScheduledFuture<?> queued = queuedDrafts.remove(task.id());
        if (queued != null) queued.cancel(false);
        status.setText("javac компилирует решение · выполнение не дольше 5 секунд…");
        check.setEnabled(false);
        io.execute(() -> {
            try {
                // In one serial queue: the draft cannot be saved *after* a
                // successful check and resurrect code already marked solved.
                TrainerApi.Profile before = api.saveDraft(target, task.id(), code);
                TrainerApi.CompileResult result = api.check(target, task, code);
                TrainerApi.Profile saved = result.progress() != null ? result.progress() : before;
                boolean draftCleared = true;
                if (result.success()) {
                    try { saved = api.saveDraft(target, task.id(), ""); }
                    catch (IOException clearFailed) { draftCleared = false; }
                }
                TrainerApi.Profile current = saved;
                boolean cleared = draftCleared;
                onUi(() -> {
                    profile = current;
                    describe();
                    output.setText((result.success() ? "✓ Зачтено" : "✗ " + result.kind())
                            + " · " + task.id() + "\n\n" + result.error()
                            + (result.output().isBlank() ? "" : "\nВывод:\n" + result.output()));
                    output.setCaretPosition(0);
                    status.setText(result.success()
                            ? cleared ? task.id().startsWith("lab-")
                                            ? "Лаборатория отмечена в общем профиле (без XP)."
                                            : "Прогресс и XP записаны в общий профиль."
                                      : "Прогресс записан, но черновик не очищен: обновите соединение."
                            : "Черновик сохранён. Исправьте ошибку и повторите проверку.");
                });
            } catch (IOException | InterruptedException error) {
                onUi(() -> { output.setText("Ошибка связи: " + error.getMessage());
                    status.setText("Данные остались в редакторе. Проверьте Flask и нажмите ещё раз."); });
            } finally {
                onUi(() -> check.setEnabled(true));
            }
        });
    }

    private void onUi(Runnable action) {
        SwingUtilities.invokeLater(() -> { if (!disposed && !project.isDisposed()) action.run(); });
    }

    @Override public void dispose() {
        disposed = true;
        Map<String, String> unsaved = new HashMap<>();
        for (String id : queuedDrafts.keySet()) {
            Document document = FileDocumentManager.getInstance().getDocument(editors.get(id));
            if (document != null) unsaved.put(id, document.getText());
        }
        for (ScheduledFuture<?> pending : queuedDrafts.values()) pending.cancel(false);
        queuedDrafts.clear();
        try {
            int target = Integer.parseInt(port.getText().trim());
            if (!unsaved.isEmpty()) io.execute(() -> {
                for (Map.Entry<String, String> entry : unsaved.entrySet()) {
                    try { api.saveDraft(target, entry.getKey(), entry.getValue()); }
                    catch (IOException | InterruptedException ignored) { break; }
                }
            });
        } catch (IllegalArgumentException ignored) { /* invalid port: edits stay in an open IDE tab */ }
        io.shutdown(); // drain already queued edits before stopping the client
    }
}
