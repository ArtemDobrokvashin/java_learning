package io.javada.android.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import io.javada.android.data.AssetCourseRepository
import io.javada.android.data.CourseCatalog
import io.javada.android.data.CourseExam
import io.javada.android.data.CourseModule
import io.javada.android.data.CourseTask
import io.javada.android.data.ExamResultSummary
import io.javada.android.data.ExamTaskDetail
import io.javada.android.data.ItemStatus
import io.javada.android.data.MobileProgressManager
import io.javada.android.data.ProgressProfile
import io.javada.android.data.ProgressState
import io.javada.android.domain.OfflineTaskValidator
import io.javada.android.domain.ValidationOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max
import kotlin.math.roundToInt

/** Основные разделы повторяют мобильную навигацию веб-тренажёра JavaДа. */
enum class RootTab {
    DASHBOARD,
    REVIEW,
    CAREER,
    SETTINGS
}

enum class ModuleFilter { ALL, READY }

/** Адресуемые экраны приложения — единый источник истины для системной и верхней кнопки «Назад». */
sealed class AppScreen {
    data class Root(val tab: RootTab = RootTab.DASHBOARD) : AppScreen()
    data class ModuleOverview(val moduleId: Int) : AppScreen()
    data class LessonView(val moduleId: Int, val lessonId: String, val masteryOnly: Boolean = false) : AppScreen()
    data class PracticeView(val moduleId: Int, val isBoss: Boolean = false) : AppScreen()
    data class MiniExamView(val moduleId: Int) : AppScreen()
    data class ReviewSession(val tasks: List<CourseTask>) : AppScreen()
    data class LabView(val labId: String) : AppScreen()
    data class SessionResultView(val summary: SessionCompletionSummary) : AppScreen()
}

data class SessionCompletionSummary(
    val mode: String,
    val moduleId: Int?,
    val title: String,
    val passed: Boolean,
    val earnedXp: Int,
    val accuracy: Int,
    val correctFirstTry: Int,
    val total: Int,
    val seconds: Int,
    val nextScreen: AppScreen,
    val retryScreen: AppScreen? = null
)

data class JavadaUiState(
    val lang: String = "ru",
    val catalog: CourseCatalog = CourseCatalog(),
    val profile: ProgressProfile = ProgressProfile(),
    val activeTab: RootTab = RootTab.DASHBOARD,
    val moduleFilter: ModuleFilter = ModuleFilter.ALL,
    val screen: AppScreen = AppScreen.Root(RootTab.DASHBOARD),
    /** Independent history per bottom-nav tab, like four stable top-level destinations. */
    val tabStacks: Map<RootTab, List<AppScreen>> = RootTab.entries.associateWith { listOf(AppScreen.Root(it)) },
    val selectedTaskIndex: Int = 0,
    val showTheoryTab: Boolean = true,
    val showHint: Boolean = false,
    val lastValidation: ValidationOutcome? = null,
    val sessionAnswers: Map<String, Any> = emptyMap(),
    val examAnswers: Map<String, Any> = emptyMap(),
    val examSummary: ExamResultSummary? = null,
    val syncBannerMessage: String? = null,
    val sessionStartedAtMs: Long = System.currentTimeMillis(),
    val sessionEarnedXp: Int = 0,
    val sessionFirstTryCorrect: Int = 0,
    val sessionAttemptedIds: Set<String> = emptySet(),
    val sessionCorrectIds: Set<String> = emptySet()
)

/**
 * Автономное состояние UI, навигация и учебный процесс Android-клиента.
 * Хранилище прогресса — общий `.javada/progress.json`, совместимый с сайтом и плагином IDEA.
 */
class JavadaViewModel(application: Application) : AndroidViewModel(application) {
    private val courseRepository = AssetCourseRepository(application.applicationContext)
    private val progressManager = MobileProgressManager(application.filesDir)
    private val preferences = application.getSharedPreferences("javada.preferences", Context.MODE_PRIVATE)
    private val supportedLangs = listOf("ru", "en", "es", "de", "zh")

    private val initialLang = preferences.getString("language", "ru")
        ?.takeIf { it in supportedLangs } ?: "ru"
    private val _uiState = MutableStateFlow(
        JavadaUiState(
            lang = initialLang,
            catalog = courseRepository.loadCatalog(initialLang),
            profile = progressManager.getProfile()
        )
    )
    val uiState: StateFlow<JavadaUiState> = _uiState.asStateFlow()

    fun setLanguage(langCode: String) {
        val language = langCode.takeIf { it in supportedLangs } ?: "ru"
        preferences.edit().putString("language", language).apply()
        val catalog = courseRepository.loadCatalog(language)
        val localizedReviewTasks = (catalog.modules.flatMap { mod ->
            mod.lessons.flatMap { it.tasks } + mod.practiceTasks
        } + catalog.labs).associateBy { it.id }
        _uiState.update { current ->
            fun localize(screen: AppScreen): AppScreen = if (screen is AppScreen.ReviewSession) {
                screen.copy(tasks = screen.tasks.map { localizedReviewTasks[it.id] ?: it })
            } else screen
            val stacks = current.tabStacks.mapValues { (_, stack) -> stack.map(::localize) }
            val activeScreen = stacks[current.activeTab]?.lastOrNull()?.let(::localize) ?: localize(current.screen)
            current.copy(
                lang = language,
                catalog = catalog,
                tabStacks = stacks,
                screen = activeScreen,
                lastValidation = null
            )
        }
    }

    fun setModuleFilter(filter: ModuleFilter) {
        _uiState.update { it.copy(moduleFilter = filter) }
    }

    /**
     * Переключает вкладку, сохраняя её собственный стек. Повторное нажатие активной вкладки
     * возвращает к корню раздела — это предсказуемая мобильная модель навигации.
     */
    fun selectRootTab(tab: RootTab) {
        _uiState.update { current ->
            val stack = current.tabStacks[tab].orEmpty().ifEmpty { listOf(AppScreen.Root(tab)) }
            val isReselect = current.activeTab == tab
            val nextStack = if (isReselect) listOf(AppScreen.Root(tab)) else stack
            val stacks = current.tabStacks + (tab to nextStack)
            current.copy(
                activeTab = tab,
                screen = nextStack.last(),
                tabStacks = stacks,
                selectedTaskIndex = 0,
                showTheoryTab = true,
                showHint = false,
                lastValidation = null,
                examSummary = null,
                syncBannerMessage = null
            )
        }
    }

    /** Добавляет адресуемый экран в стек текущего верхнего раздела. */
    fun navigateTo(target: AppScreen, pushCurrent: Boolean = true) {
        if (target is AppScreen.Root) {
            selectRootTab(target.tab)
            return
        }
        val current = _uiState.value
        val tab = when (target) {
            is AppScreen.LabView -> RootTab.CAREER
            is AppScreen.ReviewSession -> RootTab.REVIEW
            else -> current.activeTab
        }
        val oldStack = current.tabStacks[tab].orEmpty().ifEmpty { listOf(AppScreen.Root(tab)) }
        val newStack = when {
            oldStack.lastOrNull() == target -> oldStack
            pushCurrent -> (oldStack + target).takeLast(32)
            oldStack.size > 1 -> oldStack.dropLast(1) + target
            else -> listOf(AppScreen.Root(tab), target)
        }
        _uiState.update {
            it.copy(
                activeTab = tab,
                screen = target,
                tabStacks = it.tabStacks + (tab to newStack),
                selectedTaskIndex = 0,
                showTheoryTab = target is AppScreen.LessonView && !target.masteryOnly,
                showHint = false,
                lastValidation = null,
                sessionAnswers = emptyMap(),
                examAnswers = if (target is AppScreen.MiniExamView) emptyMap() else it.examAnswers,
                examSummary = null,
                syncBannerMessage = null,
                sessionStartedAtMs = System.currentTimeMillis(),
                sessionEarnedXp = 0,
                sessionFirstTryCorrect = 0,
                sessionAttemptedIds = emptySet(),
                sessionCorrectIds = emptySet()
            )
        }
    }

    /** Returns false only at the dashboard root, allowing the Activity to finish normally. */
    fun navigateBack(): Boolean {
        val current = _uiState.value
        val activeStack = current.tabStacks[current.activeTab].orEmpty()
        if (activeStack.size > 1) {
            val stack = activeStack.dropLast(1)
            _uiState.update {
                it.copy(
                    screen = stack.last(),
                    tabStacks = it.tabStacks + (current.activeTab to stack),
                    selectedTaskIndex = 0,
                    showHint = false,
                    lastValidation = null,
                    syncBannerMessage = null
                )
            }
            return true
        }
        if (current.activeTab != RootTab.DASHBOARD) {
            selectRootTab(RootTab.DASHBOARD)
            return true
        }
        return false
    }

    fun continueLearning() {
        val current = _uiState.value
        val module = current.catalog.modules.firstOrNull { moduleStatus(it) == ItemStatus.AVAILABLE }
            ?: current.catalog.modules.firstOrNull { !progressManager.isModuleCompleted(it, current.profile.state) }
            ?: return
        val target = nextStepScreenForModule(module, current.profile.state)
        navigateTo(AppScreen.ModuleOverview(module.id))
        if (target !is AppScreen.ModuleOverview) navigateTo(target)
    }

    fun startSpacedReviewSession() {
        val due = dueReviewTasks().take(5)
        if (due.isNotEmpty()) navigateTo(AppScreen.ReviewSession(due))
    }

    fun dueReviewTasks(): List<CourseTask> = progressManager.dueReviewTasks(_uiState.value.catalog)

    fun setLessonTab(showTheory: Boolean) {
        _uiState.update { it.copy(showTheoryTab = showTheory, lastValidation = null, showHint = false) }
    }

    fun selectTaskIndex(index: Int) {
        _uiState.update {
            it.copy(selectedTaskIndex = index.coerceAtLeast(0), showTheoryTab = false, showHint = false, lastValidation = null)
        }
    }

    fun toggleHint() {
        _uiState.update { it.copy(showHint = !it.showHint) }
    }

    fun dismissSyncBanner() {
        _uiState.update { it.copy(syncBannerMessage = null) }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(profile = progressManager.toggleTheme()) }
    }

    fun toggleCareerChecklist(itemId: String) {
        _uiState.update { it.copy(profile = progressManager.toggleCareerChecklist(itemId)) }
    }

    fun resetAllProgress() {
        val fresh = progressManager.resetAll()
        _uiState.update {
            it.copy(
                profile = fresh,
                activeTab = RootTab.DASHBOARD,
                screen = AppScreen.Root(RootTab.DASHBOARD),
                tabStacks = RootTab.entries.associateWith { tab -> listOf(AppScreen.Root(tab)) },
                selectedTaskIndex = 0,
                lastValidation = null,
                sessionAnswers = emptyMap(),
                examAnswers = emptyMap(),
                examSummary = null,
                sessionAttemptedIds = emptySet(),
                sessionCorrectIds = emptySet(),
                syncBannerMessage = UiText.get(it.lang, "reset_done")
            )
        }
    }

    fun saveDraft(taskId: String, code: String) {
        _uiState.update { it.copy(profile = progressManager.saveDraft(taskId, code)) }
    }

    fun getDraftOrStarter(task: CourseTask): String {
        progressManager.getDraft(task.id)?.takeIf { it.isNotBlank() }?.let { return it }
        return if (task.type == "copy") "" else task.starterCode
    }

    fun recordSessionAnswer(taskId: String, answer: Any) {
        _uiState.update { it.copy(sessionAnswers = it.sessionAnswers + (taskId to answer)) }
    }

    fun checkTaskAnswer(task: CourseTask, module: CourseModule?, answer: Any?, isReview: Boolean = false) {
        val outcome = OfflineTaskValidator.validate(task, answer)
        val current = _uiState.value
        val firstAttempt = task.id !in current.sessionAttemptedIds
        val updated = progressManager.recordTaskAttempt(task, module, outcome.success, isReview)
        val earned = max(0, updated.state.xp - current.profile.state.xp)
        _uiState.update {
            it.copy(
                profile = updated,
                lastValidation = outcome,
                sessionAttemptedIds = it.sessionAttemptedIds + task.id,
                sessionCorrectIds = if (outcome.success) it.sessionCorrectIds + task.id else it.sessionCorrectIds,
                sessionEarnedXp = it.sessionEarnedXp + earned,
                sessionFirstTryCorrect = it.sessionFirstTryCorrect + if (firstAttempt && outcome.success) 1 else 0
            )
        }
    }

    fun canAdvanceSession(mode: String, tasks: List<CourseTask>): Boolean {
        val current = _uiState.value
        return when (mode) {
            "review" -> current.sessionAttemptedIds.containsAll(tasks.map { it.id })
            "boss" -> current.sessionAttemptedIds.containsAll(tasks.map { it.id })
            "mastery" -> tasks.filterNot { it.optional }.all { it.id in current.sessionCorrectIds || current.profile.state.attempts[it.id]?.correct == true }
            else -> tasks.filterNot { it.optional }.all { it.id in current.sessionCorrectIds || current.profile.state.attempts[it.id]?.correct == true }
        }
    }

    fun isTaskSolved(task: CourseTask): Boolean =
        _uiState.value.profile.state.awardedTasks.contains(task.id) ||
            _uiState.value.profile.state.attempts[task.id]?.correct == true

    fun canOpenTask(tasks: List<CourseTask>, index: Int): Boolean {
        if (index !in tasks.indices) return false
        return tasks.take(index).filterNot { it.optional }.all(::isTaskSolved)
    }

    fun finishCurrentSession(mode: String, module: CourseModule?, title: String, tasks: List<CourseTask>) {
        val current = _uiState.value
        val requiredCount = tasks.count { !it.optional }.coerceAtLeast(1)
        val accuracy = (current.sessionFirstTryCorrect * 100.0 / requiredCount).roundToInt().coerceIn(0, 100)
        val seconds = max(1, ((System.currentTimeMillis() - current.sessionStartedAtMs) / 1000L).toInt())
        val bossPassed = if (mode == "boss") {
            module?.let { current.profile.state.bossScores[it.id.toString()]?.passed == true } ?: (accuracy >= 80)
        } else true

        val next = when {
            mode == "review" -> AppScreen.Root(RootTab.REVIEW)
            module == null -> AppScreen.Root(RootTab.CAREER)
            else -> nextStepScreenForModule(module, current.profile.state).let { step ->
                if (step !is AppScreen.ModuleOverview) step else {
                    val nextModule = current.catalog.modules.firstOrNull { it.id > module.id && moduleStatus(it) == ItemStatus.AVAILABLE }
                    nextModule?.let { AppScreen.ModuleOverview(it.id) } ?: AppScreen.ModuleOverview(module.id)
                }
            }
        }

        val summary = SessionCompletionSummary(
            mode = mode,
            moduleId = module?.id,
            title = title,
            passed = bossPassed,
            earnedXp = current.sessionEarnedXp,
            accuracy = accuracy,
            correctFirstTry = current.sessionFirstTryCorrect,
            total = requiredCount,
            seconds = seconds,
            nextScreen = next,
            retryScreen = if (mode == "boss" && module != null && !bossPassed) AppScreen.PracticeView(module.id, isBoss = true) else null
        )
        navigateTo(AppScreen.SessionResultView(summary), pushCurrent = false)
    }

    fun recordExamAnswer(task: CourseTask, answer: Any?) {
        if (answer == null) return
        _uiState.update { it.copy(examAnswers = it.examAnswers + (task.id to answer)) }
    }

    fun submitMiniExam(module: CourseModule, exam: CourseExam) {
        val answers = _uiState.value.examAnswers
        val details = exam.tasks.map { task ->
            val outcome = OfflineTaskValidator.validate(task, answers[task.id])
            ExamTaskDetail(
                id = task.id,
                correct = outcome.success,
                explanation = task.explanation,
                error = if (outcome.success) "" else outcome.message
            )
        }
        val result = progressManager.submitMiniExam(module, exam, details)
        _uiState.update { it.copy(profile = progressManager.getProfile(), examSummary = result, selectedTaskIndex = 0) }
    }

    fun getModuleStatus(module: CourseModule): ItemStatus = moduleStatus(module)

    fun moduleStatus(module: CourseModule): ItemStatus =
        progressManager.getModuleStatus(module, _uiState.value.catalog.modules, _uiState.value.profile.state)

    fun moduleProgress(module: CourseModule): Triple<Int, Int, Int> {
        val state = _uiState.value.profile.state
        val lessons = module.lessons.count { it.id in state.completedLessons }
        val practice = if (module.id in state.completedPractices) 1 else 0
        val boss = if (module.bossTasks.isNotEmpty() && state.bossScores[module.id.toString()]?.passed == true) 1 else 0
        val exam = if (module.exam != null && state.examScores[module.id.toString()]?.passed == true) 1 else 0
        val total = module.lessons.size + 1 + (if (module.bossTasks.isNotEmpty()) 1 else 0) + (if (module.exam != null) 1 else 0)
        return Triple(lessons + practice + boss + exam, total.coerceAtLeast(1), lessons)
    }

    fun isModuleCompleted(module: CourseModule): Boolean = progressManager.isModuleCompleted(module, _uiState.value.profile.state)

    fun canOpenLesson(module: CourseModule, index: Int): Boolean {
        val lesson = module.lessons.getOrNull(index) ?: return false
        val completed = _uiState.value.profile.state.completedLessons
        val previousId = module.lessons.getOrNull(index - 1)?.id
        return index == 0 || lesson.id in completed || (previousId != null && previousId in completed)
    }

    fun canOpenBoss(module: CourseModule): Boolean = module.bossTasks.isNotEmpty() &&
        module.lessons.all { it.id in _uiState.value.profile.state.completedLessons }

    fun canOpenPractice(module: CourseModule): Boolean {
        val state = _uiState.value.profile.state
        return module.lessons.all { it.id in state.completedLessons } &&
            (module.bossTasks.isEmpty() || state.bossScores[module.id.toString()]?.passed == true)
    }

    fun canOpenExam(module: CourseModule): Boolean = module.exam != null && module.id in _uiState.value.profile.state.completedPractices

    fun nextStepScreenForModule(module: CourseModule, state: ProgressState = _uiState.value.profile.state): AppScreen {
        val nextLesson = module.lessons.firstOrNull { it.id !in state.completedLessons }
        if (nextLesson != null) return AppScreen.LessonView(module.id, nextLesson.id)
        if (module.bossTasks.isNotEmpty() && state.bossScores[module.id.toString()]?.passed != true) {
            return AppScreen.PracticeView(module.id, isBoss = true)
        }
        if (module.id !in state.completedPractices) return AppScreen.PracticeView(module.id)
        if (module.exam != null && state.examScores[module.id.toString()]?.passed != true) return AppScreen.MiniExamView(module.id)
        return AppScreen.ModuleOverview(module.id)
    }

    fun exportProgressJsonString(): String = progressManager.exportProgressJson()

    fun importProgressJsonString(rawJson: String) {
        try {
            val merged = progressManager.importAndMergeProgressJson(rawJson)
            _uiState.update {
                it.copy(
                    profile = merged,
                    syncBannerMessage = localizedSyncMessage(it.lang, merged.state.xp, null)
                )
            }
        } catch (error: Exception) {
            _uiState.update {
                it.copy(syncBannerMessage = localizedSyncMessage(it.lang, null, error.message))
            }
        }
    }

    private fun localizedSyncMessage(lang: String, xp: Int?, error: String?): String = when (lang) {
        "en" -> if (error == null) "progress.json synced · ${xp ?: 0} XP in total." else "Import error: $error"
        "es" -> if (error == null) "progress.json sincronizado · ${xp ?: 0} XP en total." else "Error de importación: $error"
        "de" -> if (error == null) "progress.json synchronisiert · insgesamt ${xp ?: 0} XP." else "Importfehler: $error"
        "zh" -> if (error == null) "progress.json 已同步 · 共 ${xp ?: 0} XP。" else "导入失败：$error"
        else -> if (error == null) "Профиль progress.json синхронизирован · всего ${xp ?: 0} XP." else "Ошибка импорта: $error"
    }
}
