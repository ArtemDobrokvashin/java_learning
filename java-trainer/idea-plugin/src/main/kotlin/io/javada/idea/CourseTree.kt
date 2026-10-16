package io.javada.idea

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Типизированные узлы дерева курса "JavaДа" (Модули -> Уроки -> Задания / Практика / Мини-экзамены + Лаборатории).
 */
sealed class CourseTreeNodeData(
    open val id: String,
    open val title: String,
    open val status: ItemStatus
) {
    data class ModuleNode(
        val module: CourseModule,
        override val status: ItemStatus,
        val completedSteps: Int,
        val totalSteps: Int
    ) : CourseTreeNodeData("module-${module.id}", "Модуль ${module.id}. ${module.title}", status)

    data class LessonNode(
        val module: CourseModule,
        val lesson: CourseLesson,
        override val status: ItemStatus
    ) : CourseTreeNodeData(lesson.id, "${lesson.id.uppercase()} · ${lesson.title}", status)

    data class SectionNode(
        val module: CourseModule?,
        val sectionId: String,
        override val title: String,
        override val status: ItemStatus
    ) : CourseTreeNodeData(sectionId, title, status)

    data class TaskNode(
        val module: CourseModule?,
        val lesson: CourseLesson?,
        val task: CourseTask,
        override val status: ItemStatus
    ) : CourseTreeNodeData(task.id, task.title, status)

    data class ExamNode(
        val module: CourseModule,
        val exam: CourseExam,
        override val status: ItemStatus,
        val bestPercent: Int?
    ) : CourseTreeNodeData(exam.id, "Мини-экзамен · ${exam.tasks.size} заданий (+${exam.xpBonus} XP)", status)
}

/**
 * Компонент древовидного отображения курса (Tool Window "JavaДа").
 *
 * Поддерживает:
 * - Иерархию: Модули (22) -> Уроки (99) -> Задания (825, включая практику и стоп-уроки) -> Мини-экзамены (22) + Лабораторные (5).
 * - Иконки статусов: выполнено (`TestPassed`), доступно (`Execute`), заблокировано (`Lock`).
 * - Быстрый поиск по дереву (`TreeSpeedSearch`) и фильтрацию по тексту.
 * - Сохранение раскрытых узлов и текущего выделения при обновлении `progress.json` из браузера.
 */
class CourseTree(
    private val progressManager: ProgressManager,
    private val onNodeSelected: (CourseTreeNodeData) -> Unit
) : JPanel(BorderLayout()) {

    private val rootNode = DefaultMutableTreeNode("Курс JavaДа")
    private val treeModel = DefaultTreeModel(rootNode)
    val tree: Tree = Tree(treeModel)

    private var currentCatalog: FullCourseCatalog? = null
    private var searchQuery: String = ""

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = CourseTreeRenderer()

        // Включаем встроенный поиск IntelliJ Platform при наборе с клавиатуры
        TreeSpeedSearch.installOn(tree, true) { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode
            val data = node?.userObject as? CourseTreeNodeData
            data?.let { "${it.id} ${it.title}" } ?: ""
        }

        tree.addTreeSelectionListener {
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val data = node.userObject as? CourseTreeNodeData ?: return@addTreeSelectionListener
            onNodeSelected(data)
        }

        add(tree, BorderLayout.CENTER)
    }

    /**
     * Обновляет фильтр поиска по дереву курса.
     */
    fun setSearchFilter(query: String, profile: ProgressProfile) {
        searchQuery = query.trim().lowercase()
        val catalog = currentCatalog ?: return
        rebuildTree(catalog, profile)
    }

    /**
     * Полная пересборка дерева с сохранением раскрытых веток и выбранного элемента.
     */
    fun rebuildTree(catalog: FullCourseCatalog, profile: ProgressProfile) {
        currentCatalog = catalog
        val state = profile.state

        // Запоминаем раскрытые узлы и текущее выделение
        val expandedIds = collectExpandedIds()
        val selectedId = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)
            ?.let { it.userObject as? CourseTreeNodeData }?.id

        rootNode.removeAllChildren()

        for (module in catalog.modules) {
            val moduleStatus = progressManager.getModuleStatus(module, catalog.modules, state)
            val lessonsDone = module.lessons.count { state.completedLessons.contains(it.id) }
            val practiceDone = if (state.completedPractices.contains(module.id)) 1 else 0
            val examDone = if (module.exam != null && state.examScores[module.id.toString()]?.passed == true) 1 else 0
            val totalSteps = module.lessons.size + 1 + (if (module.exam != null) 1 else 0)
            val completedSteps = lessonsDone + practiceDone + examDone

            val moduleNodeData = CourseTreeNodeData.ModuleNode(module, moduleStatus, completedSteps, totalSteps)
            val moduleTreeNode = DefaultMutableTreeNode(moduleNodeData)

            // 1. Уроки модуля и их задания
            for (lesson in module.lessons) {
                val lessonStatus = progressManager.getLessonStatus(module, lesson, moduleStatus, state)
                val lessonNodeData = CourseTreeNodeData.LessonNode(module, lesson, lessonStatus)
                val lessonTreeNode = DefaultMutableTreeNode(lessonNodeData)

                for (task in lesson.tasks) {
                    if (!matchesFilter(module, lesson, task)) continue
                    val taskStatus = progressManager.getTaskStatus(task, lessonStatus, state)
                    lessonTreeNode.add(
                        DefaultMutableTreeNode(
                            CourseTreeNodeData.TaskNode(module, lesson, task, taskStatus)
                        )
                    )
                }

                if (lessonTreeNode.childCount > 0 || matchesFilterText("${lesson.id} ${lesson.title}")) {
                    moduleTreeNode.add(lessonTreeNode)
                }
            }

            // 2. Стоп-урок (Boss) для модулей 17–22
            if (module.bossTasks.isNotEmpty()) {
                val allLessonsDone = module.lessons.all { state.completedLessons.contains(it.id) }
                val bossPassed = state.bossScores[module.id.toString()]?.passed == true
                val bossStatus = when {
                    bossPassed -> ItemStatus.COMPLETED
                    moduleStatus != ItemStatus.LOCKED && allLessonsDone -> ItemStatus.AVAILABLE
                    else -> ItemStatus.LOCKED
                }
                val bossSection = DefaultMutableTreeNode(
                    CourseTreeNodeData.SectionNode(
                        module = module,
                        sectionId = "m%02d-boss".format(module.id),
                        title = "Стоп-урок (проверка перед практикой)",
                        status = bossStatus
                    )
                )
                for (task in module.bossTasks) {
                    if (!matchesFilter(module, null, task)) continue
                    val taskStatus = progressManager.getTaskStatus(task, bossStatus, state)
                    bossSection.add(DefaultMutableTreeNode(CourseTreeNodeData.TaskNode(module, null, task, taskStatus)))
                }
                if (bossSection.childCount > 0) {
                    moduleTreeNode.add(bossSection)
                }
            }

            // 3. Финальная практика модуля (7 заданий)
            if (module.practiceTasks.isNotEmpty()) {
                val allLessonsDone = module.lessons.all { state.completedLessons.contains(it.id) }
                val bossReady = module.bossTasks.isEmpty() || state.bossScores[module.id.toString()]?.passed == true
                val practiceStatus = when {
                    state.completedPractices.contains(module.id) -> ItemStatus.COMPLETED
                    moduleStatus != ItemStatus.LOCKED && allLessonsDone && bossReady -> ItemStatus.AVAILABLE
                    else -> ItemStatus.LOCKED
                }
                val practiceSection = DefaultMutableTreeNode(
                    CourseTreeNodeData.SectionNode(
                        module = module,
                        sectionId = "m%02d-practice".format(module.id),
                        title = "Финальная практика (${module.practiceTasks.size} заданий)",
                        status = practiceStatus
                    )
                )
                for (task in module.practiceTasks) {
                    if (!matchesFilter(module, null, task)) continue
                    val taskStatus = progressManager.getTaskStatus(task, practiceStatus, state)
                    practiceSection.add(
                        DefaultMutableTreeNode(CourseTreeNodeData.TaskNode(module, null, task, taskStatus))
                    )
                }
                if (practiceSection.childCount > 0) {
                    moduleTreeNode.add(practiceSection)
                }
            }

            // 4. Мини-экзамен модуля (5–8 заданий без подсказок)
            module.exam?.let { exam ->
                val examScore = state.examScores[module.id.toString()]
                val examStatus = when {
                    examScore?.passed == true -> ItemStatus.COMPLETED
                    state.completedPractices.contains(module.id) -> ItemStatus.AVAILABLE
                    else -> ItemStatus.LOCKED
                }
                if (searchQuery.isEmpty() || matchesFilterText("${exam.id} ${exam.title} экзамен")) {
                    moduleTreeNode.add(
                        DefaultMutableTreeNode(
                            CourseTreeNodeData.ExamNode(
                                module = module,
                                exam = exam,
                                status = examStatus,
                                bestPercent = examScore?.best
                            )
                        )
                    )
                }
            }

            if (moduleTreeNode.childCount > 0 || matchesFilterText("${module.id} ${module.title}")) {
                rootNode.add(moduleTreeNode)
            }
        }

        // 5. Раздел лабораторных работ «К junior» (5 лабораторных)
        if (catalog.labs.isNotEmpty()) {
            val allLabsDone = catalog.labs.all { state.completedLabs.contains(it.id) }
            val labsSection = DefaultMutableTreeNode(
                CourseTreeNodeData.SectionNode(
                    module = null,
                    sectionId = "section-labs",
                    title = "Лабораторные работы «К junior» (${catalog.labs.size})",
                    status = if (allLabsDone) ItemStatus.COMPLETED else ItemStatus.AVAILABLE
                )
            )
            for (lab in catalog.labs) {
                if (searchQuery.isNotEmpty() && !matchesFilterText("${lab.id} ${lab.title} ${lab.prompt}")) continue
                val labStatus = if (state.completedLabs.contains(lab.id)) ItemStatus.COMPLETED else ItemStatus.AVAILABLE
                labsSection.add(DefaultMutableTreeNode(CourseTreeNodeData.TaskNode(null, null, lab, labStatus)))
            }
            if (labsSection.childCount > 0) {
                rootNode.add(labsSection)
            }
        }

        treeModel.reload()
        restoreExpansionAndSelection(expandedIds, selectedId)
    }

    private fun matchesFilter(module: CourseModule, lesson: CourseLesson?, task: CourseTask): Boolean {
        if (searchQuery.isEmpty()) return true
        val haystack = "${module.title} ${lesson?.title ?: ""} ${task.id} ${task.title} ${task.prompt}".lowercase()
        return haystack.contains(searchQuery)
    }

    private fun matchesFilterText(text: String): Boolean {
        return searchQuery.isEmpty() || text.lowercase().contains(searchQuery)
    }

    private fun collectExpandedIds(): Set<String> {
        val result = mutableSetOf<String>()
        val enumeration = rootNode.preorderEnumeration()
        while (enumeration.hasMoreElements()) {
            val node = enumeration.nextElement() as? DefaultMutableTreeNode ?: continue
            val data = node.userObject as? CourseTreeNodeData ?: continue
            val path = TreePath(node.path)
            if (tree.isExpanded(path)) {
                result.add(data.id)
            }
        }
        return result
    }

    private fun restoreExpansionAndSelection(expandedIds: Set<String>, selectedId: String?) {
        var selectedPath: TreePath? = null
        val enumeration = rootNode.preorderEnumeration()
        while (enumeration.hasMoreElements()) {
            val node = enumeration.nextElement() as? DefaultMutableTreeNode ?: continue
            val data = node.userObject as? CourseTreeNodeData ?: continue
            val path = TreePath(node.path)
            if (expandedIds.contains(data.id) || (expandedIds.isEmpty() && data.id == "module-1")) {
                tree.expandPath(path)
            }
            if (selectedId != null && data.id == selectedId) {
                selectedPath = path
            }
        }
        if (selectedPath != null) {
            tree.selectionPath = selectedPath
        }
    }

    /**
     * Кастомный рендерер узлов дерева курса с иконками статусов (выполнено / доступно / заблокировано).
     */
    private class CourseTreeRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val node = value as? DefaultMutableTreeNode ?: return
            val data = node.userObject as? CourseTreeNodeData ?: return

            icon = statusIcon(data)

            val mainAttr = when (data.status) {
                ItemStatus.LOCKED -> SimpleTextAttributes.GRAYED_ATTRIBUTES
                ItemStatus.COMPLETED -> SimpleTextAttributes.REGULAR_ATTRIBUTES
                ItemStatus.AVAILABLE -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
            }
            append(data.title, mainAttr)

            when (data) {
                is CourseTreeNodeData.ModuleNode -> {
                    append(
                        "  [${data.completedSteps}/${data.totalSteps}]",
                        SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES
                    )
                }
                is CourseTreeNodeData.ExamNode -> {
                    data.bestPercent?.let { best ->
                        append("  (лучший: $best%)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                    }
                }
                is CourseTreeNodeData.TaskNode -> {
                    if (data.task.optional || data.task.difficulty == "hardcore") {
                        append("  ★ Хард", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                    }
                }
                else -> {}
            }
        }

        private fun statusIcon(data: CourseTreeNodeData): Icon {
            return when (data.status) {
                ItemStatus.COMPLETED -> AllIcons.RunConfigurations.TestPassed
                ItemStatus.LOCKED -> AllIcons.Diff.Lock
                ItemStatus.AVAILABLE -> when (data) {
                    is CourseTreeNodeData.ModuleNode -> AllIcons.Nodes.Module
                    is CourseTreeNodeData.LessonNode -> AllIcons.Nodes.Book
                    is CourseTreeNodeData.ExamNode -> AllIcons.General.InspectionsOK
                    is CourseTreeNodeData.SectionNode -> AllIcons.Nodes.Folder
                    is CourseTreeNodeData.TaskNode -> if (data.task.isCodeTask) {
                        AllIcons.Actions.Execute
                    } else {
                        AllIcons.Nodes.Test
                    }
                }
            }
        }
    }
}
