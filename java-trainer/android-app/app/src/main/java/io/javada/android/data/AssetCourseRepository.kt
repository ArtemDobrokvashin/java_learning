package io.javada.android.data

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets

/**
 * Автономный загрузчик курса из `assets/` APK.
 * Не требует интернет-соединения или внешнего сервера: все 22 модуля, 99 уроков,
 * 825 заданий, 22 мини-экзамена и 5 лабораторных упакованы локально.
 */
class AssetCourseRepository(private val context: Context) {

    @Volatile
    private var cachedCatalog: CourseCatalog? = null

    fun loadCatalog(): CourseCatalog {
        cachedCatalog?.let { return it }
        synchronized(this) {
            cachedCatalog?.let { return it }

            val indexObj = readAssetJson("data/modules.json") ?: return CourseCatalog()
            val version = indexObj.intOrNull("version") ?: 9
            val modulesArray = indexObj.getAsJsonArray("modules") ?: JsonArray()

            val modules = mutableListOf<CourseModule>()
            for (modEl in modulesArray) {
                val meta = modEl.asJsonObject
                val moduleId = meta.intOrNull("id") ?: continue
                val contentFile = meta.stringOrEmpty("contentFile")
                val modJson = readAssetJson("data/$contentFile") ?: continue

                val lessons = parseLessons(moduleId, modJson.getAsJsonArray("lessons"))
                val bossTasks = modJson.getAsJsonObject("boss")?.let { bossObj ->
                    parseTasks(moduleId, "m%02d-boss".format(moduleId), bossObj.getAsJsonArray("tasks"))
                } ?: emptyList()

                val practiceObj = modJson.getAsJsonObject("practice")
                val primerObj = practiceObj?.getAsJsonObject("primer")
                val primerEx = primerObj?.getAsJsonObject("example")
                val practiceTasks = practiceObj?.let {
                    parseTasks(moduleId, "m%02d-practice".format(moduleId), it.getAsJsonArray("tasks"))
                } ?: emptyList()

                val exam = modJson.getAsJsonObject("exam")?.let { examObj ->
                    CourseExam(
                        id = examObj.stringOrEmpty("id").ifEmpty { "m%02d-exam".format(moduleId) },
                        moduleId = moduleId,
                        title = examObj.stringOrEmpty("title").ifEmpty { "Мини-экзамен модуля $moduleId" },
                        passingPercent = examObj.intOrNull("passingPercent") ?: 80,
                        cooldownSeconds = examObj.intOrNull("cooldownSeconds") ?: 3600,
                        xpBonus = examObj.intOrNull("xpBonus") ?: (300 + moduleId * 10),
                        tasks = parseTasks(moduleId, "m%02d-exam".format(moduleId), examObj.getAsJsonArray("tasks"))
                    )
                }

                modules.add(
                    CourseModule(
                        id = moduleId,
                        title = meta.stringOrEmpty("title"),
                        subtitle = meta.stringOrEmpty("subtitle"),
                        lessons = lessons,
                        bossTasks = bossTasks,
                        practicePrimerWhy = primerObj?.stringOrEmpty("why") ?: "",
                        practicePrimerSteps = primerObj?.stringList("steps") ?: emptyList(),
                        practicePrimerExampleCode = primerEx?.stringOrEmpty("code") ?: "",
                        practicePrimerExampleStdout = primerEx?.stringOrEmpty("stdout") ?: "",
                        practiceTasks = practiceTasks,
                        exam = exam
                    )
                )
            }

            val labs = parseLabs()
            return CourseCatalog(version = version, modules = modules, labs = labs).also {
                cachedCatalog = it
            }
        }
    }

    private fun parseLessons(moduleId: Int, lessonsArray: JsonArray?): List<CourseLesson> {
        if (lessonsArray == null) return emptyList()
        val list = mutableListOf<CourseLesson>()
        for (el in lessonsArray) {
            val obj = el.asJsonObject
            val lessonId = obj.stringOrEmpty("id")
            val thObj = obj.getAsJsonObject("theory")
            val guideObj = thObj?.getAsJsonObject("guide")
            val exObj = guideObj?.getAsJsonObject("example")

            val theory = LessonTheory(
                paragraphs = thObj?.stringList("paragraphs") ?: emptyList(),
                code = thObj?.stringOrEmpty("code") ?: "",
                caption = thObj?.stringOrEmpty("caption") ?: "",
                objective = thObj?.stringOrEmpty("objective") ?: "",
                walkthrough = thObj?.stringOrEmpty("walkthrough") ?: "",
                pitfall = thObj?.stringOrEmpty("pitfall") ?: "",
                deepDive = thObj?.stringOrEmpty("deepDive") ?: "",
                useCase = thObj?.stringOrEmpty("useCase") ?: "",
                guideWhy = guideObj?.stringOrEmpty("why") ?: "",
                guideSteps = guideObj?.stringList("steps") ?: emptyList(),
                guideExampleCode = exObj?.stringOrEmpty("code") ?: "",
                guideExampleLabel = exObj?.stringOrEmpty("label") ?: "",
                guideExampleStdout = exObj?.stringOrEmpty("stdout") ?: "",
                guideResult = guideObj?.stringOrEmpty("result") ?: "",
                guideBridge = guideObj?.stringOrEmpty("bridge") ?: ""
            )

            list.add(
                CourseLesson(
                    id = lessonId,
                    moduleId = moduleId,
                    title = obj.stringOrEmpty("title"),
                    durationMinutes = obj.intOrNull("duration") ?: 5,
                    theory = theory,
                    tasks = parseTasks(moduleId, lessonId, obj.getAsJsonArray("tasks"))
                )
            )
        }
        return list
    }

    private fun parseTasks(moduleId: Int, parentId: String?, tasksArray: JsonArray?): List<CourseTask> {
        if (tasksArray == null) return emptyList()
        return tasksArray.mapIndexed { idx, el ->
            val obj = el.asJsonObject
            val id = obj.stringOrEmpty("id")
            val type = obj.stringOrEmpty("type").ifEmpty { "code" }
            val difficulty = obj.stringOrEmpty("difficulty").ifEmpty { null }

            val options = obj.getAsJsonArray("options")?.map { oEl ->
                val o = oEl.asJsonObject
                ChoiceOption(o.stringOrEmpty("id"), o.stringOrEmpty("label"))
            } ?: emptyList()

            val blanks = obj.getAsJsonArray("blanks")?.map { bEl ->
                val b = bEl.asJsonObject
                val vObj = b.getAsJsonObject("validation")
                FillBlank(
                    id = b.stringOrEmpty("id"),
                    kind = b.stringOrEmpty("kind").ifEmpty { "text" },
                    options = b.stringList("options"),
                    answer = b.stringOrEmpty("answer"),
                    accepted = b.stringList("accepted"),
                    minRange = vObj?.intOrNull("min"),
                    maxRange = vObj?.intOrNull("max")
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

            val altOrders = obj.getAsJsonArray("alternativeOrders")?.map { arr ->
                arr.asJsonArray.map { it.asString }
            } ?: emptyList()

            val title = when (type) {
                "copy" -> "Образец кода"
                "choice" -> "Тест #${idx + 1}"
                "fill" -> "Заполните пропуски #${idx + 1}"
                "matching" -> "Соедините пары #${idx + 1}"
                "parsons" -> "Соберите код #${idx + 1}"
                "code" -> if (difficulty == "hardcore") "★ Необязательный хард" else "Напишите программу"
                else -> "Задание #${idx + 1}"
            }

            CourseTask(
                id = id,
                moduleId = moduleId,
                lessonId = obj.stringOrEmpty("lessonId").ifEmpty { parentId },
                title = title,
                type = type,
                difficulty = difficulty,
                optional = obj.booleanOrFalse("optional") || difficulty == "hardcore",
                prompt = obj.stringOrEmpty("prompt"),
                hint = obj.stringOrEmpty("hint"),
                explanation = obj.stringOrEmpty("explanation"),
                starterCode = obj.stringOrEmpty("starterCode"),
                sourceCode = obj.stringOrEmpty("sourceCode"),
                language = obj.stringOrEmpty("language").ifEmpty { "java" },
                expectedOutput = obj.stringOrEmpty("expectedOutput"),
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
        }
    }

    private fun parseLabs(): List<CourseTask> {
        val labsObj = readAssetJson("data/labs.json") ?: return emptyList()
        val casesObj = readAssetJson("private/lab_cases.json") ?: JsonObject()
        val labsArray = labsObj.getAsJsonArray("labs") ?: return emptyList()

        return labsArray.map { el ->
            val obj = el.asJsonObject
            val id = obj.stringOrEmpty("id")
            val cases = casesObj.getAsJsonArray(id)?.map { cEl ->
                val c = cEl.asJsonObject
                LabTestCase(
                    input = c.stringOrEmpty("input"),
                    expectedOutput = c.stringOrEmpty("expectedOutput")
                )
            } ?: listOf(
                LabTestCase(
                    input = obj.stringOrEmpty("sampleInput"),
                    expectedOutput = obj.stringOrEmpty("sampleOutput")
                )
            )

            CourseTask(
                id = id,
                moduleId = 0,
                lessonId = id,
                title = obj.stringOrEmpty("title"),
                type = "code",
                prompt = buildString {
                    append(obj.stringOrEmpty("description"))
                    val goal = obj.stringOrEmpty("goal")
                    if (goal.isNotBlank()) append("\n\nЦель: ").append(goal)
                    val inputFmt = obj.stringOrEmpty("inputFormat")
                    if (inputFmt.isNotBlank()) append("\nВвод: ").append(inputFmt)
                    val outputFmt = obj.stringOrEmpty("outputFormat")
                    if (outputFmt.isNotBlank()) append("\nВывод: ").append(outputFmt)
                },
                hint = obj.stringOrEmpty("hint"),
                starterCode = obj.stringOrEmpty("starterCode"),
                sampleInput = obj.stringOrEmpty("sampleInput"),
                sampleOutput = obj.stringOrEmpty("sampleOutput"),
                expectedOutput = obj.stringOrEmpty("sampleOutput"),
                testCases = cases
            )
        }
    }

    private fun readAssetJson(assetPath: String): JsonObject? {
        return try {
            context.assets.open(assetPath).bufferedReader(StandardCharsets.UTF_8).use { reader ->
                JsonParser.parseReader(reader).asJsonObject
            }
        } catch (_: Exception) {
            null
        }
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
