package io.javada.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.javada.android.data.AssetCourseRepository
import io.javada.android.data.CourseCatalog
import io.javada.android.data.CourseExam
import io.javada.android.data.CourseLesson
import io.javada.android.data.CourseModule
import io.javada.android.data.CourseTask
import io.javada.android.data.ExamResultSummary
import io.javada.android.data.ExamTaskDetail
import io.javada.android.data.ItemStatus
import io.javada.android.data.MobileProgressManager
import io.javada.android.data.ProgressProfile
import io.javada.android.domain.OfflineTaskValidator
import io.javada.android.domain.ValidationOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class AppScreen {
    data object CourseMap : AppScreen()
    data class ModuleOverview(val moduleId: Int) : AppScreen()
    data class LessonView(val moduleId: Int, val lessonId: String) : AppScreen()
    data class PracticeView(val moduleId: Int, val isBoss: Boolean = false) : AppScreen()
    data class MiniExamView(val moduleId: Int) : AppScreen()
    data class LabView(val labId: String) : AppScreen()
}

data class JavadaUiState(
    val catalog: CourseCatalog = CourseCatalog(),
    val profile: ProgressProfile = ProgressProfile(),
    val screen: AppScreen = AppScreen.CourseMap,
    val selectedTaskIndex: Int = 0,
    val showTheoryTab: Boolean = true,
    val showHint: Boolean = false,
    val lastValidation: ValidationOutcome? = null,
    val examAnswers: Map<String, Any> = emptyMap(),
    val examSummary: ExamResultSummary? = null,
    val syncBannerMessage: String? = null
)

/**
 * ViewModel автономного Android-приложения «JavaДа Mobile».
 */
class JavadaViewModel(application: Application) : AndroidViewModel(application) {

    private val courseRepository = AssetCourseRepository(application.applicationContext)
    private val progressManager = MobileProgressManager(application.filesDir)

    private val _uiState = MutableStateFlow(
        JavadaUiState(
            catalog = courseRepository.loadCatalog(),
            profile = progressManager.getProfile()
        )
    )
    val uiState: StateFlow<JavadaUiState> = _uiState.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        _uiState.update {
            it.copy(
                screen = screen,
                selectedTaskIndex = 0,
                showTheoryTab = screen is AppScreen.LessonView,
                showHint = false,
                lastValidation = null,
                examAnswers = emptyMap(),
                examSummary = null,
                syncBannerMessage = null
            )
        }
    }

    fun navigateBack(): Boolean {
        val current = _uiState.value.screen
        val parent = when (current) {
            is AppScreen.CourseMap -> return false
            is AppScreen.ModuleOverview -> AppScreen.CourseMap
            is AppScreen.LessonView -> AppScreen.ModuleOverview(current.moduleId)
            is AppScreen.PracticeView -> AppScreen.ModuleOverview(current.moduleId)
            is AppScreen.MiniExamView -> AppScreen.ModuleOverview(current.moduleId)
            is AppScreen.LabView -> AppScreen.CourseMap
        }
        navigateTo(parent)
        return true
    }

    fun setLessonTab(showTheory: Boolean) {
        _uiState.update { it.copy(showTheoryTab = showTheory, lastValidation = null) }
    }

    fun selectTaskIndex(index: Int) {
        _uiState.update {
            it.copy(
                selectedTaskIndex = index,
                showHint = false,
                lastValidation = null
            )
        }
    }

    fun toggleHint() {
        _uiState.update { it.copy(showHint = !it.showHint) }
    }

    fun toggleTheme() {
        val updated = progressManager.toggleTheme()
        _uiState.update { it.copy(profile = updated) }
    }

    fun getInitialCodeForTask(task: CourseTask): String {
        val savedDraft = progressManager.getDraft(task.id)
        if (!savedDraft.isNullOrBlank()) return savedDraft
        return if (task.type == "copy") "" else task.starterCode
    }

    fun saveCodeDraft(taskId: String, code: String) {
        val updated = progressManager.saveDraft(taskId, code)
        _uiState.update { it.copy(profile = updated) }
    }

    /**
     * Проверяет ответ на текущее задание в автономном режиме и начисляет XP в `.javada/progress.json`.
     */
    fun checkTaskAnswer(task: CourseTask, module: CourseModule?, userAnswer: Any?) {
        if ((task.type == "code" || task.type == "copy") && userAnswer is String) {
            progressManager.saveDraft(task.id, userAnswer)
        }
        val outcome = OfflineTaskValidator.validate(task, userAnswer)
        val updatedProfile = progressManager.recordTaskAttempt(task, module, outcome.success)
        _uiState.update {
            it.copy(
                profile = updatedProfile,
                lastValidation = outcome
            )
        }
    }

    /**
     * Сохраняет ответ текущего шага мини-экзамена (без подсказок) и переходит к следующему вопросу
     * либо подсчитывает итоговый результат (порог >= 80%).
     */
    fun submitExamStepAndAdvance(
        module: CourseModule,
        exam: CourseExam,
        currentTask: CourseTask,
        userAnswer: Any?
    ) {
        val updatedAnswers = _uiState.value.examAnswers.toMutableMap()
        if (userAnswer != null) {
            updatedAnswers[currentTask.id] = userAnswer
        }

        val currentIndex = _uiState.value.selectedTaskIndex
        if (currentIndex < exam.tasks.lastIndex) {
            _uiState.update {
                it.copy(
                    examAnswers = updatedAnswers,
                    selectedTaskIndex = currentIndex + 1,
                    lastValidation = null
                )
            }
        } else {
            val details = exam.tasks.map { task ->
                val ans = updatedAnswers[task.id]
                val outcome = OfflineTaskValidator.validate(task, ans)
                ExamTaskDetail(
                    id = task.id,
                    correct = outcome.success,
                    explanation = task.explanation,
                    error = if (outcome.success) "" else outcome.message
                )
            }
            val summary = progressManager.submitMiniExam(module, exam, details)
            val updatedProfile = progressManager.getProfile()
            _uiState.update {
                it.copy(
                    profile = updatedProfile,
                    examAnswers = updatedAnswers,
                    examSummary = summary
                )
            }
        }
    }

    fun exportProgressJsonString(): String = progressManager.exportProgressJson()

    fun importProgressJsonString(rawJson: String) {
        try {
            val merged = progressManager.importAndMergeProgressJson(rawJson)
            _uiState.update {
                it.copy(
                    profile = merged,
                    syncBannerMessage = "✓ Прогресс успешно синхронизирован (${merged.state.xp} XP, уроков: ${merged.state.completedLessons.size}/99)"
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(syncBannerMessage = "Ошибка импорта progress.json: ${e.message}")
            }
        }
    }

    fun dismissSyncBanner() {
        _uiState.update { it.copy(syncBannerMessage = null) }
    }

    fun getModuleStatus(module: CourseModule): ItemStatus {
        val state = _uiState.value.profile.state
        return progressManager.getModuleStatus(module, _uiState.value.catalog.modules, state)
    }
}
