package io.javada.idea

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Перечисление статусов доступности элементов курса (модулей, уроков, заданий, экзаменов).
 */
enum class ItemStatus {
    LOCKED,
    AVAILABLE,
    COMPLETED
}

/**
 * Режим верификации кодовой задачи:
 * - STDOUT_EXPECTED: компиляция javac + запуск java и сверка stdout с expected.txt / expectedOutput / lab_cases.json
 * - JUNIT_TESTS: компиляция решения и тестового класса + запуск JUnit Platform Console
 * - COMPILE_ONLY: проверка только компиляции без запуска
 */
enum class VerificationMode {
    STDOUT_EXPECTED,
    JUNIT_TESTS,
    COMPILE_ONLY
}

data class TestCaseData(
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
 * Универсальная модель задания курса (поддерживает все 825 заданий курса + 5 лабораторных + JUnit-задачи).
 */
data class CourseTask(
    val id: String,
    val moduleId: Int,
    val lessonId: String?,
    val title: String,
    val type: String, // "code", "copy", "choice", "fill", "matching", "parsons", "junit"
    val mode: String = "run",
    val difficulty: String? = null,
    val optional: Boolean = false,
    val prompt: String = "",
    val hint: String = "",
    val explanation: String = "",
    val starterCode: String = "",
    val sourceCode: String = "",
    val language: String = "java",
    val targetFileName: String = "Main.java",
    val expectedOutput: String = "",
    val expectedTxtPath: Path? = null,
    val junitTestSourcePath: Path? = null,
    val junitTestClassName: String? = null,
    val sampleInput: String = "",
    val sampleOutput: String = "",
    val testCases: List<TestCaseData> = emptyList(),
    val options: List<ChoiceOption> = emptyList(),
    val correctChoices: List<String> = emptyList(),
    val multipleChoice: Boolean = false,
    val fillTemplate: List<String> = emptyList(),
    val fillBlanks: List<FillBlank> = emptyList(),
    val matchingPairs: List<MatchingPair> = emptyList(),
    val parsonsLines: List<ParsonsLine> = emptyList(),
    val parsonsSolution: List<ParsonsLine> = emptyList(),
    val parsonsAlternativeOrders: List<List<String>> = emptyList()
) {
    val isCodeTask: Boolean
        get() = type == "code" || type == "junit" || type == "copy"

    val verificationMode: VerificationMode
        get() = when {
            type == "junit" || junitTestSourcePath != null || !junitTestClassName.isNullOrBlank() ->
                VerificationMode.JUNIT_TESTS
            mode == "compile" ->
                VerificationMode.COMPILE_ONLY
            else ->
                VerificationMode.STDOUT_EXPECTED
        }
}

/**
 * Полная модель теории урока для отображения в Split View (Markdown / HTML).
 */
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
    val durationMinutes: Int,
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
    val contentFile: String,
    val lessons: List<CourseLesson>,
    val bossTasks: List<CourseTask> = emptyList(),
    val practicePrimerWhy: String = "",
    val practicePrimerSteps: List<String> = emptyList(),
    val practicePrimerExampleCode: String = "",
    val practicePrimerExampleStdout: String = "",
    val practiceTasks: List<CourseTask> = emptyList(),
    val exam: CourseExam? = null
)

data class FullCourseCatalog(
    val version: Int,
    val trainerRoot: Path,
    val modules: List<CourseModule>,
    val labs: List<CourseTask>
)

/**
 * Репозиторий загрузки курса напрямую из файловой системы проекта (`java-trainer/frontend/data` и `private/lab_cases.json`).
 * Обеспечивает 100% автономность плагина без необходимости запускать Flask-сервер.
 */
object CourseRepository {
    private val LOG = Logger.getInstance(CourseRepository::class.java)

    /**
     * Находит корневую директорию `java-trainer` относительно открытого проекта в IDEA.
     */
    fun resolveTrainerRoot(projectBasePath: String?): Path {
        val base = projectBasePath?.let { Path.of(it) } ?: Path.of(System.getProperty("user.dir"))
        val candidates = listOf(
            base,
            base.resolve("java-trainer"),
            base.parent ?: base,
            (base.parent ?: base).resolve("java-trainer")
        )
        return candidates.firstOrNull {
            Files.isRegularFile(it.resolve("frontend/data/modules.json"))
        } ?: base.resolve("java-trainer").takeIf { Files.isDirectory(it) } ?: base
    }

    /**
     * Загружает все 22 модуля, 99 уроков, 825 заданий, 22 мини-экзамена и 5 лабораторных работ
     * на выбранном языке (ru, en, es, de, zh).
     */
    fun loadCatalog(trainerRoot: Path, langCode: String = JavadaI18n.currentLang.code): FullCourseCatalog {
        val baseDataDir = trainerRoot.resolve("frontend/data")
        val i18nDataDir = baseDataDir.resolve("i18n").resolve(langCode)
        val dataDir = if (langCode != "ru" && Files.isRegularFile(i18nDataDir.resolve("modules.json"))) {
            i18nDataDir
        } else {
            baseDataDir
        }
        val privateDir = trainerRoot.resolve("private")
        val modulesIndexFile = dataDir.resolve("modules.json")

        if (!Files.isRegularFile(modulesIndexFile)) {
            LOG.warn("Файл курса не найден по пути: $modulesIndexFile")
            return FullCourseCatalog(9, trainerRoot, emptyList(), emptyList())
        }

        val indexJson = readJson(modulesIndexFile)
        val version = indexJson.intOrNull("version") ?: 9
        val modulesArray = indexJson.getAsJsonArray("modules") ?: JsonArray()

        val modules = mutableListOf<CourseModule>()
        for (modEl in modulesArray) {
            val meta = modEl.asJsonObject
            val moduleId = meta.intOrNull("id") ?: continue
            val contentFile = meta.stringOrEmpty("contentFile")
            val moduleFile = dataDir.resolve(contentFile)
            if (!Files.isRegularFile(moduleFile)) continue

            val modJson = readJson(moduleFile)
            val lessons = parseLessons(moduleId, modJson.getAsJsonArray("lessons"), trainerRoot)
            val bossTasks = modJson.getAsJsonObject("boss")?.let { bossObj ->
                parseTasks(moduleId, "m%02d-boss".format(moduleId), bossObj.getAsJsonArray("tasks"), trainerRoot)
            } ?: emptyList()

            val practiceObj = modJson.getAsJsonObject("practice")
            val primerObj = practiceObj?.getAsJsonObject("primer")
            val primerExample = primerObj?.getAsJsonObject("example")
            val practiceTasks = practiceObj?.let {
                parseTasks(moduleId, "m%02d-practice".format(moduleId), it.getAsJsonArray("tasks"), trainerRoot)
            } ?: emptyList()

            val exam = modJson.getAsJsonObject("exam")?.let { examObj ->
                CourseExam(
                    id = examObj.stringOrEmpty("id").ifEmpty { "m%02d-exam".format(moduleId) },
                    moduleId = moduleId,
                    title = examObj.stringOrEmpty("title").ifEmpty { "Мини-экзамен модуля $moduleId" },
                    passingPercent = examObj.intOrNull("passingPercent") ?: 80,
                    cooldownSeconds = examObj.intOrNull("cooldownSeconds") ?: 3600,
                    xpBonus = examObj.intOrNull("xpBonus") ?: (300 + moduleId * 10),
                    tasks = parseTasks(moduleId, "m%02d-exam".format(moduleId), examObj.getAsJsonArray("tasks"), trainerRoot)
                )
            }

            modules.add(
                CourseModule(
                    id = moduleId,
                    title = meta.stringOrEmpty("title"),
                    subtitle = meta.stringOrEmpty("subtitle"),
                    contentFile = contentFile,
                    lessons = lessons,
                    bossTasks = bossTasks,
                    practicePrimerWhy = primerObj?.stringOrEmpty("why") ?: "",
                    practicePrimerSteps = primerObj?.stringList("steps") ?: emptyList(),
                    practicePrimerExampleCode = primerExample?.stringOrEmpty("code") ?: "",
                    practicePrimerExampleStdout = primerExample?.stringOrEmpty("stdout") ?: "",
                    practiceTasks = practiceTasks,
                    exam = exam
                )
            )
        }

        val labs = loadLabs(dataDir.resolve("labs.json"), privateDir.resolve("lab_cases.json"), trainerRoot)
        return FullCourseCatalog(version, trainerRoot, modules, labs)
    }

    private fun parseLessons(moduleId: Int, lessonsArray: JsonArray?, trainerRoot: Path): List<CourseLesson> {
        if (lessonsArray == null) return emptyList()
        val result = mutableListOf<CourseLesson>()
        for (el in lessonsArray) {
            val obj = el.asJsonObject
            val lessonId = obj.stringOrEmpty("id")
            val theoryObj = obj.getAsJsonObject("theory")
            val guideObj = theoryObj?.getAsJsonObject("guide")
            val exampleObj = guideObj?.getAsJsonObject("example")

            val theory = LessonTheory(
                paragraphs = theoryObj?.stringList("paragraphs") ?: emptyList(),
                code = theoryObj?.stringOrEmpty("code") ?: "",
                caption = theoryObj?.stringOrEmpty("caption") ?: "",
                objective = theoryObj?.stringOrEmpty("objective") ?: "",
                walkthrough = theoryObj?.stringOrEmpty("walkthrough") ?: "",
                pitfall = theoryObj?.stringOrEmpty("pitfall") ?: "",
                deepDive = theoryObj?.stringOrEmpty("deepDive") ?: "",
                useCase = theoryObj?.stringOrEmpty("useCase") ?: "",
                guideWhy = guideObj?.stringOrEmpty("why") ?: "",
                guideSteps = guideObj?.stringList("steps") ?: emptyList(),
                guideExampleCode = exampleObj?.stringOrEmpty("code") ?: "",
                guideExampleLabel = exampleObj?.stringOrEmpty("label") ?: "",
                guideExampleStdout = exampleObj?.stringOrEmpty("stdout") ?: "",
                guideResult = guideObj?.stringOrEmpty("result") ?: "",
                guideBridge = guideObj?.stringOrEmpty("bridge") ?: ""
            )

            val tasks = parseTasks(moduleId, lessonId, obj.getAsJsonArray("tasks"), trainerRoot)
            result.add(
                CourseLesson(
                    id = lessonId,
                    moduleId = moduleId,
                    title = obj.stringOrEmpty("title"),
                    durationMinutes = obj.intOrNull("duration") ?: 5,
                    theory = theory,
                    tasks = tasks
                )
            )
        }
        return result
    }

    private fun parseTasks(moduleId: Int, parentId: String?, tasksArray: JsonArray?, trainerRoot: Path): List<CourseTask> {
        if (tasksArray == null) return emptyList()
        val result = mutableListOf<CourseTask>()
        for ((idx, el) in tasksArray.withIndex()) {
            val obj = el.asJsonObject
            val id = obj.stringOrEmpty("id")
            val type = obj.stringOrEmpty("type").ifEmpty { "code" }
            val lessonId = obj.stringOrEmpty("lessonId").ifEmpty { parentId }

            // Проверяем наличие локального файла expected.txt или JUnit-теста для задачи
            val taskFolder = trainerRoot.resolve("tasks").resolve(id)
            val expectedTxt = taskFolder.resolve("expected.txt").takeIf { Files.isRegularFile(it) }
            val junitFile = obj.stringOrEmpty("testFile").takeIf { it.isNotBlank() }
                ?.let { trainerRoot.resolve(it) }
                ?: taskFolder.resolve("MainTest.java").takeIf { Files.isRegularFile(it) }

            val options = obj.getAsJsonArray("options")?.map { optEl ->
                val o = optEl.asJsonObject
                ChoiceOption(o.stringOrEmpty("id"), o.stringOrEmpty("label"))
            } ?: emptyList()

            val blanks = obj.getAsJsonArray("blanks")?.map { bEl ->
                val b = bEl.asJsonObject
                val valObj = b.getAsJsonObject("validation")
                FillBlank(
                    id = b.stringOrEmpty("id"),
                    kind = b.stringOrEmpty("kind").ifEmpty { "text" },
                    options = b.stringList("options"),
                    answer = b.stringOrEmpty("answer"),
                    accepted = b.stringList("accepted"),
                    minRange = valObj?.intOrNull("min"),
                    maxRange = valObj?.intOrNull("max")
                )
            } ?: emptyList()

            val pairs = obj.getAsJsonArray("pairs")?.map { pEl ->
                val p = pEl.asJsonObject
                MatchingPair(p.stringOrEmpty("id"), p.stringOrEmpty("left"), p.stringOrEmpty("right"))
            } ?: emptyList()

            val parsonsLines = obj.getAsJsonArray("lines")?.map { lEl ->
                val l = lEl.asJsonObject
                ParsonsLine(l.stringOrEmpty("id"), l.stringOrEmpty("text"), l.intOrNull("indent") ?: 0)
            } ?: emptyList()

            val parsonsSolution = obj.getAsJsonArray("solution")?.map { sEl ->
                val s = sEl.asJsonObject
                ParsonsLine(s.stringOrEmpty("id"), s.stringOrEmpty("text"), s.intOrNull("indent") ?: 0)
            } ?: emptyList()

            val altOrders = obj.getAsJsonArray("alternativeOrders")?.map { arrEl ->
                arrEl.asJsonArray.map { it.asString }
            } ?: emptyList()

            val titleLabel = JavadaI18n.taskTitle(type, id, obj.stringOrEmpty("difficulty").ifEmpty { null }, idx)

            result.add(
                CourseTask(
                    id = id,
                    moduleId = moduleId,
                    lessonId = lessonId,
                    title = titleLabel,
                    type = type,
                    mode = obj.stringOrEmpty("mode").ifEmpty { "run" },
                    difficulty = obj.stringOrEmpty("difficulty").ifEmpty { null },
                    optional = obj.booleanOrFalse("optional"),
                    prompt = obj.stringOrEmpty("prompt"),
                    hint = obj.stringOrEmpty("hint"),
                    explanation = obj.stringOrEmpty("explanation"),
                    starterCode = obj.stringOrEmpty("starterCode"),
                    sourceCode = obj.stringOrEmpty("sourceCode"),
                    language = obj.stringOrEmpty("language").ifEmpty { "java" },
                    targetFileName = obj.stringOrEmpty("targetFileName").ifEmpty { "Main.java" },
                    expectedOutput = obj.stringOrEmpty("expectedOutput"),
                    expectedTxtPath = expectedTxt,
                    junitTestSourcePath = junitFile,
                    junitTestClassName = obj.stringOrEmpty("testClass").ifEmpty { null },
                    sampleInput = obj.stringOrEmpty("sampleInput"),
                    sampleOutput = obj.stringOrEmpty("sampleOutput"),
                    options = options,
                    correctChoices = obj.stringList("correct"),
                    multipleChoice = obj.booleanOrFalse("multiple"),
                    fillTemplate = obj.stringList("template"),
                    fillBlanks = blanks,
                    matchingPairs = pairs,
                    parsonsLines = parsonsLines,
                    parsonsSolution = parsonsSolution,
                    parsonsAlternativeOrders = altOrders
                )
            )
        }
        return result
    }

    private fun loadLabs(labsFile: Path, labCasesFile: Path, trainerRoot: Path): List<CourseTask> {
        if (!Files.isRegularFile(labsFile)) return emptyList()
        val labsJson = readJson(labsFile)
        val casesJson = if (Files.isRegularFile(labCasesFile)) readJson(labCasesFile) else JsonObject()
        val labsArray = labsJson.getAsJsonArray("labs") ?: return emptyList()

        val result = mutableListOf<CourseTask>()
        for (el in labsArray) {
            val obj = el.asJsonObject
            val id = obj.stringOrEmpty("id")
            val cases = casesJson.getAsJsonArray(id)?.map { cEl ->
                val c = cEl.asJsonObject
                TestCaseData(
                    input = c.stringOrEmpty("input"),
                    expectedOutput = c.stringOrEmpty("expectedOutput")
                )
            } ?: listOf(
                TestCaseData(
                    input = obj.stringOrEmpty("sampleInput"),
                    expectedOutput = obj.stringOrEmpty("sampleOutput")
                )
            )
            val expectedTxt = trainerRoot.resolve("tasks").resolve(id).resolve("expected.txt")
                .takeIf { Files.isRegularFile(it) }

            result.add(
                CourseTask(
                    id = id,
                    moduleId = 0,
                    lessonId = id,
                    title = "Лабораторная: ${obj.stringOrEmpty("title")}",
                    type = "code",
                    mode = obj.stringOrEmpty("mode").ifEmpty { "run" },
                    prompt = buildString {
                        append(obj.stringOrEmpty("description"))
                        val goal = obj.stringOrEmpty("goal")
                        if (goal.isNotBlank()) append("\n\nЦель: ").append(goal)
                        val inFmt = obj.stringOrEmpty("inputFormat")
                        if (inFmt.isNotBlank()) append("\nФормат ввода: ").append(inFmt)
                        val outFmt = obj.stringOrEmpty("outputFormat")
                        if (outFmt.isNotBlank()) append("\nФормат вывода: ").append(outFmt)
                    },
                    hint = obj.stringOrEmpty("hint"),
                    starterCode = obj.stringOrEmpty("starterCode"),
                    sampleInput = obj.stringOrEmpty("sampleInput"),
                    sampleOutput = obj.stringOrEmpty("sampleOutput"),
                    expectedOutput = obj.stringOrEmpty("sampleOutput"),
                    expectedTxtPath = expectedTxt,
                    testCases = cases
                )
            )
        }
        return result
    }

    private fun readJson(path: Path): JsonObject {
        val content = Files.readString(path, StandardCharsets.UTF_8)
        return JsonParser.parseString(content).asJsonObject
    }

    private fun JsonObject.stringOrEmpty(key: String): String {
        val el: JsonElement = get(key) ?: return ""
        return if (el.isJsonPrimitive && !el.isJsonNull) el.asString else ""
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val el: JsonElement = get(key) ?: return null
        return try {
            if (el.isJsonPrimitive && !el.isJsonNull) el.asInt else null
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.booleanOrFalse(key: String): Boolean {
        val el: JsonElement = get(key) ?: return false
        return try {
            if (el.isJsonPrimitive && !el.isJsonNull) el.asBoolean else false
        } catch (_: Exception) {
            false
        }
    }

    private fun JsonObject.stringList(key: String): List<String> {
        val arr = getAsJsonArray(key) ?: return emptyList()
        return arr.mapNotNull { if (it.isJsonPrimitive && !it.isJsonNull) it.asString else null }
    }
}
