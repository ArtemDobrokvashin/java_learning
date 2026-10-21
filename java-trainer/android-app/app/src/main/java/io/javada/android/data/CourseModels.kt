package io.javada.android.data

/**
 * Статус доступности элемента курса (модуля, урока, практики, экзамена, задания).
 */
enum class ItemStatus {
    LOCKED,
    AVAILABLE,
    COMPLETED
}

data class LabTestCase(
    val input: String = "",
    val expectedOutput: String = ""
)

data class ChoiceOption(
    val id: String,
    val label: String
)

data class FillBlank(
    val id: String,
    val kind: String, // "select" | "text"
    val options: List<String> = emptyList(),
    val answer: String = "",
    val accepted: List<String> = emptyList(),
    val minRange: Int? = null,
    val maxRange: Int? = null
)

data class MatchingPair(
    val id: String,
    val left: String,
    val right: String
)

data class ParsonsLine(
    val id: String,
    val text: String,
    val indent: Int = 0
)

/**
 * Универсальная модель задания для Android-приложения (поддерживает все 825 заданий курса и 5 лабораторных).
 */
data class CourseTask(
    val id: String,
    val moduleId: Int,
    val lessonId: String?,
    val title: String,
    val type: String, // "copy", "choice", "fill", "matching", "parsons", "code"
    val difficulty: String? = null,
    val optional: Boolean = false,
    val prompt: String = "",
    val hint: String = "",
    val explanation: String = "",
    val starterCode: String = "",
    val sourceCode: String = "",
    val language: String = "java",
    val expectedOutput: String = "",
    val sampleInput: String = "",
    val sampleOutput: String = "",
    val testCases: List<LabTestCase> = emptyList(),
    val options: List<ChoiceOption> = emptyList(),
    val correctChoices: List<String> = emptyList(),
    val multipleChoice: Boolean = false,
    val fillTemplate: List<String> = emptyList(),
    val fillBlanks: List<FillBlank> = emptyList(),
    val matchingPairs: List<MatchingPair> = emptyList(),
    val parsonsLines: List<ParsonsLine> = emptyList(),
    val parsonsSolution: List<ParsonsLine> = emptyList(),
    val parsonsAlternativeOrders: List<List<String>> = emptyList()
)

data class LessonTheory(
    val paragraphs: List<String> = emptyList(),
    val code: String = "",
    val caption: String = "",
    val objective: String = "",
    val walkthrough: String = "",
    val pitfall: String = "",
    val deepDive: String = "",
    val useCase: String = "",
    val guideWhy: String = "",
    val guideSteps: List<String> = emptyList(),
    val guideExampleCode: String = "",
    val guideExampleLabel: String = "",
    val guideExampleStdout: String = "",
    val guideResult: String = "",
    val guideBridge: String = ""
)

data class CourseLesson(
    val id: String,
    val moduleId: Int,
    val title: String,
    val durationMinutes: Int = 5,
    val theory: LessonTheory,
    val tasks: List<CourseTask>
)

data class CourseExam(
    val id: String,
    val moduleId: Int,
    val title: String,
    val passingPercent: Int = 80,
    val cooldownSeconds: Int = 3600,
    val xpBonus: Int = 310,
    val tasks: List<CourseTask>
)

data class CourseModule(
    val id: Int,
    val title: String,
    val subtitle: String,
    val lessons: List<CourseLesson>,
    val bossTasks: List<CourseTask> = emptyList(),
    val practicePrimerWhy: String = "",
    val practicePrimerSteps: List<String> = emptyList(),
    val practicePrimerExampleCode: String = "",
    val practicePrimerExampleStdout: String = "",
    val practiceTasks: List<CourseTask> = emptyList(),
    val exam: CourseExam? = null
)

data class CourseCatalog(
    val version: Int = 9,
    val modules: List<CourseModule> = emptyList(),
    val labs: List<CourseTask> = emptyList()
)
