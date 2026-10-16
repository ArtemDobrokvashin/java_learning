package io.javada.idea

import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.JBSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.AbstractButton
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent as SwingDocumentEvent
import javax.swing.event.DocumentListener as SwingDocumentListener

/**
 * Фабрика инициализации Tool Window "JavaДа" в IntelliJ IDEA.
 */
class JavadaToolWindowFactory : ToolWindowFactory, DumbAware {
    companion object {
        val PANEL_KEY: Key<JavadaWorkspacePanel> = Key.create("io.javada.idea.WorkspacePanel")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JavadaWorkspacePanel(project)
        project.putUserData(PANEL_KEY, panel)
        val content = ContentFactory.getInstance().createContent(panel, "Курс и Задачи", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * Глобальный Action "JavaДа: Проверить решение" для тулбара и контекстного меню редактора (Ctrl+Shift+Enter).
 */
class CheckTaskAction : AnAction("JavaДа: Проверить решение", "Проверить текущую задачу тренажёра", AllIcons.Actions.Execute), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = project.getUserData(JavadaToolWindowFactory.PANEL_KEY) ?: return
        panel.triggerCheckCurrentTask()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project?.getUserData(JavadaToolWindowFactory.PANEL_KEY) != null
    }
}

/**
 * Главная панель плагина "JavaДа", объединяющая:
 * 1. Дерево курса (`CourseTree`) с поиском и иконками статусов.
 * 2. Панель просмотра теории в формате Markdown/HTML (`JEditorPane` в составе Split View).
 * 3. Рабочее пространство задачи (автоматическое открытие `Main.java`, сохранение черновиков, интерактивные виджеты).
 * 4. Режим мини-экзамена (последовательное прохождение 5–8 задач без подсказок с порогом >= 80%).
 */
class JavadaWorkspacePanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    private val trainerRoot = CourseRepository.resolveTrainerRoot(project.basePath)
    private val progressManager = ProgressManager(trainerRoot)
    private val taskChecker = TaskChecker(project, trainerRoot)

    private var catalog: FullCourseCatalog = CourseRepository.loadCatalog(trainerRoot)
    private var profile: ProgressProfile = progressManager.getProfile()

    // Текущий выбранный контекст
    private var currentModule: CourseModule? = null
    private var currentLesson: CourseLesson? = null
    private var currentTask: CourseTask? = null

    // Состояние режима мини-экзамена (5–8 задач без подсказок)
    private var activeExamModule: CourseModule? = null
    private var activeExam: CourseExam? = null
    private var examStepIndex: Int = 0
    private val examCollectedAnswers = ConcurrentHashMap<String, Any>()
    private val isExamMode: Boolean
        get() = activeExam != null

    // Виртуальные файлы редактора Main.java для кодовых задач
    private val openTaskFiles = ConcurrentHashMap<String, TaskVirtualFile>()
    private val pendingDraftSaves = ConcurrentHashMap<String, ScheduledFuture<*>>()

    private val backgroundExecutor = Executors.newScheduledThreadPool(2) { r ->
        Thread(r, "javada-plugin-worker").apply { isDaemon = true }
    }

    // UI-компоненты
    private val xpBadgeLabel = JBLabel()
    private val searchField = SearchTextField(true)
    private val courseTree = CourseTree(progressManager) { nodeData -> onTreeNodeSelected(nodeData) }

    private val theoryHtmlPane = JEditorPane("text/html", "").apply {
        isEditable = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        border = JBUI.Borders.empty(10)
    }

    private val interactiveAnswerPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(6, 10)
    }

    private val consoleOutputArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        border = JBUI.Borders.empty(8)
        text = "Выберите урок или задачу в дереве курса слева. Для кодовых задач файл Main.java откроется автоматически."
    }

    private val openFileBtn = JButton("Открыть Main.java", AllIcons.Actions.EditSource)
    private val checkSolutionBtn = JButton("Проверить решение", AllIcons.Actions.Execute)
    private val hintBtn = JButton("Подсказка", AllIcons.Actions.IntentionBulb)
    private val examNextBtn = JButton("Следующий вопрос →").apply { isVisible = false }
    private val syncBtn = JButton("Синхронизировать", AllIcons.Actions.Refresh)

    // Виджеты ответов для не-кодовых задач
    private val choiceButtons = mutableListOf<AbstractButton>()
    private val fillInputs = mutableMapOf<String, () -> String>()
    private val matchingCombos = mutableMapOf<String, JComboBox<String>>()
    private val parsonsOrderArea = JBTextArea(6, 40)

    private var showHint = false

    /**
     * Виртуальный файл `Main.java`, изолированный по ID задачи.
     */
    private class TaskVirtualFile(
        val taskId: String,
        fileName: String,
        initialCode: String
    ) : LightVirtualFile(fileName, JavaFileType.INSTANCE, initialCode) {
        override fun getPath(): String = "/javada/$taskId/$name"
    }

    init {
        Disposer.register(this, progressManager)
        buildLayout()
        bindListeners()
        refreshUiFromProfile(profile)
    }

    private fun buildLayout() {
        // Верхняя панель статуса и поиска
        val headerPanel = JPanel(BorderLayout(8, 4)).apply {
            border = JBUI.Borders.empty(6, 8)
            val topStatusRow = JPanel(BorderLayout()).apply {
                add(xpBadgeLabel, BorderLayout.WEST)
                add(syncBtn, BorderLayout.EAST)
            }
            add(topStatusRow, BorderLayout.NORTH)
            add(searchField, BorderLayout.SOUTH)
        }

        val leftPanel = JPanel(BorderLayout()).apply {
            add(headerPanel, BorderLayout.NORTH)
            add(JBScrollPane(courseTree), BorderLayout.CENTER)
        }

        // Правая часть (Split View: Теория + Интерактивная область / Консоль проверки)
        val theoryAndControlsPanel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(theoryHtmlPane), BorderLayout.CENTER)
            add(interactiveAnswerPanel, BorderLayout.SOUTH)
        }

        val actionsBar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            add(openFileBtn)
            add(checkSolutionBtn)
            add(examNextBtn)
            add(hintBtn)
        }

        val bottomConsolePanel = JPanel(BorderLayout()).apply {
            add(actionsBar, BorderLayout.NORTH)
            add(JBScrollPane(consoleOutputArea), BorderLayout.CENTER)
        }

        val rightVerticalSplitter = JBSplitter(true, 0.62f).apply {
            firstComponent = theoryAndControlsPanel
            secondComponent = bottomConsolePanel
        }

        val mainHorizontalSplitter = JBSplitter(false, 0.36f).apply {
            firstComponent = leftPanel
            secondComponent = rightVerticalSplitter
        }

        setContent(mainHorizontalSplitter)
    }

    private fun bindListeners() {
        progressManager.addListener { updatedProfile ->
            SwingUtilities.invokeLater {
                if (!project.isDisposed) {
                    profile = updatedProfile
                    refreshUiFromProfile(updatedProfile)
                }
            }
        }

        searchField.addDocumentListener(object : SwingDocumentListener {
            override fun insertUpdate(e: SwingDocumentEvent?) = applySearch()
            override fun removeUpdate(e: SwingDocumentEvent?) = applySearch()
            override fun changedUpdate(e: SwingDocumentEvent?) = applySearch()
            private fun applySearch() {
                courseTree.setSearchFilter(searchField.text, profile)
            }
        })

        openFileBtn.addActionListener {
            currentTask?.let { task ->
                if (task.isCodeTask) openTaskInEditor(task, focusEditor = true)
            }
        }

        checkSolutionBtn.addActionListener {
            triggerCheckCurrentTask()
        }

        examNextBtn.addActionListener {
            advanceMiniExamStep()
        }

        hintBtn.addActionListener {
            if (isExamMode) return@addActionListener // В режиме мини-экзамена подсказки отключены
            showHint = !showHint
            renderTheoryAndTaskView()
        }

        syncBtn.addActionListener {
            backgroundExecutor.execute {
                catalog = CourseRepository.loadCatalog(trainerRoot)
                val reloaded = progressManager.reloadFromDisk()
                SwingUtilities.invokeLater {
                    profile = reloaded
                    refreshUiFromProfile(reloaded)
                    consoleOutputArea.text = "Синхронизировано с ${progressManager.progressFile}"
                }
            }
        }
    }

    private fun refreshUiFromProfile(currentProfile: ProgressProfile) {
        val state = currentProfile.state
        val level = (state.xp / 250) + 1
        xpBadgeLabel.text = "Уровень $level · ${state.xp} XP · Серия: ${state.streak} дн. · Уроков: ${state.completedLessons.size}/99"
        courseTree.rebuildTree(catalog, currentProfile)
    }

    /**
     * Обработчик выбора узла в дереве курса.
     */
    private fun onTreeNodeSelected(nodeData: CourseTreeNodeData) {
        showHint = false
        when (nodeData) {
            is CourseTreeNodeData.ModuleNode -> {
                exitMiniExamMode()
                currentModule = nodeData.module
                currentLesson = nodeData.module.lessons.firstOrNull()
                currentTask = currentLesson?.tasks?.firstOrNull()
                renderTheoryAndTaskView()
            }
            is CourseTreeNodeData.LessonNode -> {
                exitMiniExamMode()
                currentModule = nodeData.module
                currentLesson = nodeData.lesson
                currentTask = nodeData.lesson.tasks.firstOrNull()
                renderTheoryAndTaskView()
                currentTask?.takeIf { it.isCodeTask }?.let { openTaskInEditor(it, focusEditor = false) }
            }
            is CourseTreeNodeData.SectionNode -> {
                exitMiniExamMode()
                currentModule = nodeData.module
                currentLesson = null
                currentTask = nodeData.module?.practiceTasks?.firstOrNull()
                renderTheoryAndTaskView()
            }
            is CourseTreeNodeData.TaskNode -> {
                exitMiniExamMode()
                currentModule = nodeData.module
                currentLesson = nodeData.lesson
                currentTask = nodeData.task
                renderTheoryAndTaskView()
                if (nodeData.task.isCodeTask) {
                    openTaskInEditor(nodeData.task, focusEditor = true)
                }
            }
            is CourseTreeNodeData.ExamNode -> {
                startMiniExamSession(nodeData.module, nodeData.exam, nodeData.status)
            }
        }
    }

    /**
     * Запускает режим мини-экзамена (последовательное открытие 5–8 задач без подсказок).
     */
    private fun startMiniExamSession(module: CourseModule, exam: CourseExam, status: ItemStatus) {
        if (status == ItemStatus.LOCKED && !profile.state.completedPractices.contains(module.id)) {
            exitMiniExamMode()
            currentModule = module
            currentLesson = null
            currentTask = null
            theoryHtmlPane.text = wrapHtml(
                """
                <h2>🔒 Мини-экзамен модуля ${module.id} пока закрыт</h2>
                <p>Сначала завершите финальную практику модуля <b>«${escapeHtml(module.title)}»</b>, чтобы открыть мини-экзамен.</p>
                """.trimIndent()
            )
            consoleOutputArea.text = "Завершите практику модуля ${module.id} перед началом мини-экзамена."
            return
        }

        val record = progressManager.startMiniExam(module)
        val nowSec = System.currentTimeMillis() / 1000
        if (!record.active && record.retryAt > nowSec && record.result?.passed != true) {
            val minutesLeft = ((record.retryAt - nowSec + 59) / 60).coerceAtLeast(1)
            theoryHtmlPane.text = wrapHtml(
                """
                <h2>⏳ Перерыв перед пересдачей мини-экзамена</h2>
                <p>До следующей попытки осталось около <b>$minutesLeft мин.</b> Повторите уроки модуля ${module.id} и попробуйте снова.</p>
                """.trimIndent()
            )
            return
        }

        activeExamModule = module
        activeExam = exam
        examStepIndex = 0
        examCollectedAnswers.clear()
        hintBtn.isEnabled = false
        hintBtn.text = "Без подсказок (Экзамен)"
        examNextBtn.isVisible = true
        loadCurrentExamTask()
    }

    private fun exitMiniExamMode() {
        activeExamModule = null
        activeExam = null
        examStepIndex = 0
        examCollectedAnswers.clear()
        hintBtn.isEnabled = true
        hintBtn.text = "Подсказка"
        examNextBtn.isVisible = false
        checkSolutionBtn.text = "Проверить решение"
    }

    private fun loadCurrentExamTask() {
        val exam = activeExam ?: return
        val task = exam.tasks.getOrNull(examStepIndex) ?: return
        currentTask = task
        currentLesson = null
        val isLast = examStepIndex == exam.tasks.lastIndex
        examNextBtn.text = if (isLast) "Завершить мини-экзамен (${exam.tasks.size}/${exam.tasks.size})" else "Сохранить ответ и далее (${examStepIndex + 1}/${exam.tasks.size}) →"
        checkSolutionBtn.text = "Проверить текущий шаг"

        renderTheoryAndTaskView()
        if (task.isCodeTask) {
            openTaskInEditor(task, focusEditor = true)
        }
        consoleOutputArea.text = "Режим мини-экзамена: задание ${examStepIndex + 1} из ${exam.tasks.size}. Подсказки отключены. Для сдачи необходимо набрать не менее 80%."
    }

    /**
     * Сохраняет ответ на текущую задачу мини-экзамена и переходит к следующей (или завершает экзамен).
     */
    private fun advanceMiniExamStep() {
        val exam = activeExam ?: return
        val module = activeExamModule ?: return
        val task = currentTask ?: return

        val answer = collectCurrentUserAnswer(task)
        if (answer != null) {
            examCollectedAnswers[task.id] = answer
        }

        if (examStepIndex < exam.tasks.lastIndex) {
            examStepIndex += 1
            loadCurrentExamTask()
        } else {
            finishAndGradeMiniExam(module, exam)
        }
    }

    private fun finishAndGradeMiniExam(module: CourseModule, exam: CourseExam) {
        checkSolutionBtn.isEnabled = false
        examNextBtn.isEnabled = false
        consoleOutputArea.text = "Проверка всех ${exam.tasks.size} заданий мини-экзамена…"

        backgroundExecutor.execute {
            val details = mutableListOf<ExamTaskDetail>()
            for (task in exam.tasks) {
                val rawAnswer = examCollectedAnswers[task.id] ?: collectCurrentUserAnswer(task) ?: ""
                val checkRes = taskChecker.checkTask(task, rawAnswer)
                details.add(
                    ExamTaskDetail(
                        id = task.id,
                        correct = checkRes.success,
                        explanation = task.explanation,
                        error = checkRes.error,
                        output = checkRes.output
                    )
                )
            }

            val summary = progressManager.submitMiniExam(module, exam, details)
            val updatedProfile = progressManager.getProfile()

            SwingUtilities.invokeLater {
                profile = updatedProfile
                checkSolutionBtn.isEnabled = true
                examNextBtn.isEnabled = true
                exitMiniExamMode()
                refreshUiFromProfile(updatedProfile)

                val statusHeader = if (summary.passed) {
                    "🎉 Мини-экзамен сдан! Набрано ${summary.percent}% (${summary.correct}/${summary.total}). Начислено +${summary.xp} XP, следующий модуль разблокирован!"
                } else {
                    "❌ Мини-экзамен не сдан: набрано ${summary.percent}% (${summary.correct}/${summary.total}), требуется минимум ${exam.passingPercent}%."
                }

                val breakdownHtml = buildString {
                    append("<h2>").append(escapeHtml(statusHeader)).append("</h2>")
                    append("<ul>")
                    for (item in summary.details) {
                        val mark = if (item.correct) "✅" else "❌"
                        append("<li><b>$mark ${escapeHtml(item.id)}</b>: ")
                        if (item.correct) {
                            append(escapeHtml(item.explanation.ifBlank { "Верно" }))
                        } else {
                            append(escapeHtml(item.error.ifBlank { "Неверный ответ" }))
                        }
                        append("</li>")
                    }
                    append("</ul>")
                }
                theoryHtmlPane.text = wrapHtml(breakdownHtml)
                theoryHtmlPane.caretPosition = 0
                consoleOutputArea.text = statusHeader
            }
        }
    }

    /**
     * Автоматически открывает файл `Main.java` в редакторе IDEA с начальным шаблоном или сохранённым черновиком,
     * а также подписывается на изменения документа для автосохранения черновика в `.javada/progress.json`.
     */
    private fun openTaskInEditor(task: CourseTask, focusEditor: Boolean) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val savedDraft = progressManager.getDraft(task.id)
            val defaultTemplate = when {
                task.starterCode.isNotBlank() -> task.starterCode
                task.sourceCode.isNotBlank() && task.type == "copy" -> "// Перепишите код образца ниже:\n\n"
                else -> "public class Main {\n    public static void main(String[] args) {\n        \n    }\n}\n"
            }
            val initialContent = savedDraft ?: defaultTemplate

            val virtualFile = openTaskFiles.computeIfAbsent(task.id) {
                val created = TaskVirtualFile(task.id, task.targetFileName, initialContent)
                val document = FileDocumentManager.getInstance().getDocument(created)
                document?.addDocumentListener(object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        scheduleDraftSave(task.id, event.document.text)
                    }
                }, this@JavadaWorkspacePanel)
                created
            }

            FileEditorManager.getInstance(project).openFile(virtualFile, focusEditor)
        }
    }

    /**
     * Отложенное сохранение черновика кода в `.javada/progress.json` (debounce 600 мс).
     */
    private fun scheduleDraftSave(taskId: String, code: String) {
        pendingDraftSaves.remove(taskId)?.cancel(false)
        val future = backgroundExecutor.schedule({
            progressManager.saveDraft(taskId, code)
        }, 600, TimeUnit.MILLISECONDS)
        pendingDraftSaves[taskId] = future
    }

    /**
     * Запускает проверку текущего выбранного задания через `TaskChecker` в фоновом потоке.
     */
    fun triggerCheckCurrentTask() {
        val task = currentTask ?: run {
            consoleOutputArea.text = "Сначала выберите задачу в дереве курса."
            return
        }

        val userAnswer = collectCurrentUserAnswer(task) ?: run {
            consoleOutputArea.text = "Не удалось прочитать ответ для задачи ${task.id}."
            return
        }

        // Если это кодовая задача, немедленно фиксируем актуальный черновик перед проверкой
        if (task.isCodeTask && userAnswer is String) {
            pendingDraftSaves.remove(task.id)?.cancel(false)
            progressManager.saveDraft(task.id, userAnswer)
        }

        checkSolutionBtn.isEnabled = false
        consoleOutputArea.text = "Запуск проверки задачи ${task.id} (${task.verificationMode})…"

        backgroundExecutor.execute {
            val result = taskChecker.checkTask(task, userAnswer)
            if (isExamMode) {
                examCollectedAnswers[task.id] = userAnswer
            } else {
                progressManager.recordTaskResult(task, currentModule, result.success)
            }
            val updatedProfile = progressManager.getProfile()

            SwingUtilities.invokeLater {
                if (project.isDisposed) return@invokeLater
                profile = updatedProfile
                checkSolutionBtn.isEnabled = true
                refreshUiFromProfile(updatedProfile)
                renderTheoryAndTaskView()

                val header = if (result.success) {
                    "✓ Решение зачтено (${result.executionTimeMs} мс) · ${task.id} · Всего XP: ${updatedProfile.state.xp}"
                } else {
                    "✗ Проверка не пройдена (${result.kind}) · ${task.id}"
                }

                consoleOutputArea.text = buildString {
                    append(header).append("\n\n")
                    if (result.error.isNotBlank()) {
                        append(result.error).append("\n\n")
                    }
                    if (result.output.isNotBlank()) {
                        append("Вывод программы / отчёт:\n").append(result.output)
                    }
                }.trim()
                consoleOutputArea.caretPosition = 0
            }
        }
    }

    private fun collectCurrentUserAnswer(task: CourseTask): Any? {
        if (task.isCodeTask) {
            val vf = openTaskFiles[task.id]
            if (vf != null) {
                var docText: String? = null
                ApplicationManager.getApplication().runReadAction {
                    val doc: Document? = FileDocumentManager.getInstance().getDocument(vf)
                    docText = doc?.text
                }
                if (docText != null) return docText
            }
            return progressManager.getDraft(task.id) ?: task.starterCode
        }

        return when (task.type) {
            "choice" -> choiceButtons.filter { it.isSelected }.mapNotNull { it.actionCommand }
            "fill" -> fillInputs.mapValues { (_, getter) -> getter() }
            "matching" -> matchingCombos.mapValues { (_, combo) ->
                val selectedRight = combo.selectedItem?.toString() ?: ""
                task.matchingPairs.find { it.right == selectedRight }?.id ?: ""
            }
            "parsons" -> {
                val linesById = task.parsonsLines.associateBy { it.id }
                parsonsOrderArea.text.lines()
                    .mapNotNull { raw ->
                        val trimmed = raw.trim()
                        if (trimmed.isEmpty()) return@mapNotNull null
                        val parts = trimmed.split(":")
                        val id = parts[0].trim()
                        val indent = parts.getOrNull(1)?.trim()?.toIntOrNull()
                            ?: task.parsonsSolution.find { it.id == id }?.indent ?: 0
                        linesById[id]?.copy(indent = indent)
                    }
            }
            else -> ""
        }
    }

    /**
     * Формирует и отображает теорию текущего урока и условие текущей задачи в формате HTML/Markdown в Split View,
     * а также перестраивает панель интерактивного ответа.
     */
    private fun renderTheoryAndTaskView() {
        val module = currentModule
        val lesson = currentLesson
        val task = currentTask

        openFileBtn.isVisible = task?.isCodeTask == true
        rebuildInteractiveControls(task)

        val html = buildString {
            if (isExamMode && activeExam != null) {
                append("<div style='padding:6px 10px;background:#2b3b4e;color:#fff;border-radius:4px;margin-bottom:10px;'>")
                append("<b>Режим мини-экзамена:</b> ${escapeHtml(activeExam!!.title)} · Вопрос ${examStepIndex + 1} из ${activeExam!!.tasks.size} · Подсказки отключены")
                append("</div>")
            }

            if (module != null) {
                append("<h2>Модуль ${module.id}. ${escapeHtml(module.title)}</h2>")
                if (module.subtitle.isNotBlank()) {
                    append("<p><i>${escapeHtml(module.subtitle)}</i></p>")
                }
            }

            // Отображение теории урока (только вне режима экзамена)
            if (!isExamMode && lesson != null) {
                val t = lesson.theory
                append("<h3>📖 Урок: ${escapeHtml(lesson.title)} (${lesson.durationMinutes} мин)</h3>")
                if (t.objective.isNotBlank()) {
                    append("<p><b>Цель урока:</b> ${escapeHtml(t.objective)}</p>")
                }
                for (p in t.paragraphs) {
                    append("<p>${renderInlineMarkdown(p)}</p>")
                }
                if (t.code.isNotBlank()) {
                    append("<pre style='background:#1e1f22;color:#dfe1e5;padding:10px;border-radius:5px;'><code>")
                    append(escapeHtml(t.code))
                    append("</code></pre>")
                }
                if (t.walkthrough.isNotBlank()) {
                    append("<p><b>Разбор примера:</b> ${escapeHtml(t.walkthrough)}</p>")
                }
                if (t.pitfall.isNotBlank()) {
                    append("<p><b>⚠️ Частая ошибка:</b> ${escapeHtml(t.pitfall)}</p>")
                }
                if (t.deepDive.isNotBlank()) {
                    append("<p><b>Глубже в тему:</b> ${escapeHtml(t.deepDive)}</p>")
                }
                if (t.useCase.isNotBlank()) {
                    append("<p><b>На практике:</b> ${escapeHtml(t.useCase)}</p>")
                }
                if (t.guideSteps.isNotEmpty()) {
                    append("<h4>Пошаговое руководство</h4>")
                    if (t.guideWhy.isNotBlank()) append("<p>${escapeHtml(t.guideWhy)}</p>")
                    append("<ol>")
                    for (step in t.guideSteps) {
                        append("<li>${escapeHtml(step)}</li>")
                    }
                    append("</ol>")
                }
                if (t.guideExampleCode.isNotBlank()) {
                    append("<p><b>${escapeHtml(t.guideExampleLabel.ifBlank { "Полный рабочий пример" })}:</b></p>")
                    append("<pre style='background:#1e1f22;color:#dfe1e5;padding:10px;border-radius:5px;'><code>")
                    append(escapeHtml(t.guideExampleCode))
                    append("</code></pre>")
                    if (t.guideExampleStdout.isNotBlank()) {
                        append("<p><b>Ожидаемый вывод примера:</b> <code>${escapeHtml(t.guideExampleStdout)}</code></p>")
                    }
                }
                append("<hr/>")
            } else if (!isExamMode && module != null && module.practicePrimerWhy.isNotBlank()) {
                append("<h3>🛠 Подготовка к практике модуля</h3>")
                append("<p>${escapeHtml(module.practicePrimerWhy)}</p>")
                if (module.practicePrimerExampleCode.isNotBlank()) {
                    append("<pre style='background:#1e1f22;color:#dfe1e5;padding:10px;border-radius:5px;'><code>")
                    append(escapeHtml(module.practicePrimerExampleCode))
                    append("</code></pre>")
                }
                append("<hr/>")
            }

            // Отображение условия текущей задачи
            if (task != null) {
                val solved = profile.state.awardedTasks.contains(task.id) ||
                        profile.state.completedLabs.contains(task.id) ||
                        profile.state.attempts[task.id]?.correct == true
                val badge = if (solved && !isExamMode) "✅ Решено" else "📝 Задание"
                append("<h3>$badge: ${escapeHtml(task.title)}</h3>")
                append("<p>${renderInlineMarkdown(task.prompt)}</p>")

                if (task.type == "copy" && task.sourceCode.isNotBlank()) {
                    append("<p><b>Образец для переписывания в редакторе:</b></p>")
                    append("<pre style='background:#1e1f22;color:#dfe1e5;padding:10px;border-radius:5px;'><code>")
                    append(escapeHtml(task.sourceCode))
                    append("</code></pre>")
                }

                if (task.sampleInput.isNotBlank() || task.sampleOutput.isNotBlank()) {
                    append("<p><b>Пример ввода:</b><br/><code>${escapeHtml(task.sampleInput).replace("\n", "<br/>")}</code></p>")
                    append("<p><b>Пример вывода:</b><br/><code>${escapeHtml(task.sampleOutput).replace("\n", "<br/>")}</code></p>")
                }

                if (showHint && !isExamMode && task.hint.isNotBlank()) {
                    append("<div style='padding:8px;background:#3b3322;color:#ffd97d;border-radius:4px;margin-top:8px;'>")
                    append("<b>💡 Подсказка:</b> ${escapeHtml(task.hint)}")
                    append("</div>")
                }
            }
        }

        theoryHtmlPane.text = wrapHtml(html)
        theoryHtmlPane.caretPosition = 0
    }

    private fun rebuildInteractiveControls(task: CourseTask?) {
        interactiveAnswerPanel.removeAll()
        choiceButtons.clear()
        fillInputs.clear()
        matchingCombos.clear()

        if (task == null || task.isCodeTask) {
            interactiveAnswerPanel.revalidate()
            interactiveAnswerPanel.repaint()
            return
        }

        val container = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        when (task.type) {
            "choice" -> {
                val group = if (task.multipleChoice) null else ButtonGroup()
                for (opt in task.options) {
                    val btn: AbstractButton = if (task.multipleChoice) {
                        JCheckBox("${opt.id}) ${opt.label}")
                    } else {
                        JRadioButton("${opt.id}) ${opt.label}")
                    }
                    btn.actionCommand = opt.id
                    group?.add(btn)
                    choiceButtons.add(btn)
                    container.add(btn)
                }
            }
            "fill" -> {
                val templatePreview = task.fillTemplate.joinToString("[___]")
                container.add(JBLabel("Шаблон: $templatePreview"))
                for (blank in task.fillBlanks) {
                    val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2))
                    row.add(JBLabel("${blank.id}:"))
                    if (blank.kind == "select" && blank.options.isNotEmpty()) {
                        val combo = JComboBox(blank.options.toTypedArray())
                        fillInputs[blank.id] = { combo.selectedItem?.toString() ?: "" }
                        row.add(combo)
                    } else {
                        val tf = JBTextField(16)
                        fillInputs[blank.id] = { tf.text.trim() }
                        row.add(tf)
                    }
                    container.add(row)
                }
            }
            "matching" -> {
                val rightOptions = task.matchingPairs.map { it.right }.shuffled().toTypedArray()
                val grid = JPanel(GridLayout(task.matchingPairs.size, 2, 6, 4))
                for (pair in task.matchingPairs) {
                    grid.add(JBLabel(pair.left))
                    val combo = JComboBox(rightOptions)
                    matchingCombos[pair.id] = combo
                    grid.add(combo)
                }
                container.add(grid)
            }
            "parsons" -> {
                val info = task.parsonsLines.joinToString("\n") { "${it.id}:0  // ${it.text}" }
                container.add(JBLabel("Укажите порядок строк и отступы (формат id:отступ по одной на строке):"))
                parsonsOrderArea.text = task.parsonsSolution.joinToString("\n") { "${it.id}:${it.indent}" }
                    .ifBlank { info }
                container.add(JBScrollPane(parsonsOrderArea))
            }
        }

        interactiveAnswerPanel.add(container, BorderLayout.CENTER)
        interactiveAnswerPanel.revalidate()
        interactiveAnswerPanel.repaint()
    }

    private fun wrapHtml(bodyContent: String): String {
        val isDark = UIUtil.isUnderDarcula()
        val fg = if (isDark) "#dfe1e5" else "#1f2328"
        val fontFamily = UIUtil.getLabelFont().family
        return """
            <html>
            <head>
                <style>
                    body { font-family: '$fontFamily', sans-serif; font-size: 13px; color: $fg; line-height: 1.5; margin: 0; }
                    h2 { font-size: 16px; margin-top: 4px; margin-bottom: 6px; }
                    h3 { font-size: 14px; margin-top: 10px; margin-bottom: 6px; }
                    h4 { font-size: 13px; margin-top: 8px; margin-bottom: 4px; }
                    p { margin-top: 4px; margin-bottom: 8px; }
                    code { font-family: monospace; font-size: 12px; }
                    ul, ol { margin-top: 4px; margin-bottom: 8px; padding-left: 20px; }
                </style>
            </head>
            <body>$bodyContent</body>
            </html>
        """.trimIndent()
    }

    private fun renderInlineMarkdown(text: String): String {
        return escapeHtml(text)
            .replace(Regex("`([^`]+)`"), "<code>$1</code>")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "<b>$1</b>")
    }

    private fun escapeHtml(raw: String): String {
        return raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    override fun dispose() {
        for ((taskId, future) in pendingDraftSaves) {
            future.cancel(false)
            val vf = openTaskFiles[taskId] ?: continue
            val doc = FileDocumentManager.getInstance().getDocument(vf) ?: continue
            progressManager.saveDraft(taskId, doc.text)
        }
        pendingDraftSaves.clear()
        backgroundExecutor.shutdownNow()
    }
}
