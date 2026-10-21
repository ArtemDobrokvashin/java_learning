package io.javada.android

import io.javada.android.data.ChoiceOption
import io.javada.android.data.CourseExam
import io.javada.android.data.CourseModule
import io.javada.android.data.CourseTask
import io.javada.android.data.ExamTaskDetail
import io.javada.android.data.FillBlank
import io.javada.android.data.MatchingPair
import io.javada.android.data.MobileProgressManager
import io.javada.android.data.ParsonsLine
import io.javada.android.domain.OfflineTaskValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class OfflineValidatorAndProgressTest {

    @Test
    fun `validateCopy accepts reformatted Java with identical tokens and rejects altered literals`() {
        val sample = """
            public class Main {
                public static void main(String[] args) {
                    int age = 16;
                    String name = "Мира";
                    System.out.println(age);
                    System.out.println(name);
                }
            }
        """.trimIndent()

        val task = CourseTask(
            id = "m01-l01-copy",
            moduleId = 1,
            lessonId = "m01-l01",
            title = "Образец",
            type = "copy",
            sourceCode = sample
        )

        val reformatted = sample.replace("\n", " ")
        assertTrue(OfflineTaskValidator.validate(task, reformatted).success)

        val wrongLiteral = sample.replace("Мира", "Лена")
        assertFalse(OfflineTaskValidator.validate(task, wrongLiteral).success)
    }

    @Test
    fun `validateFill supports integerRange and accepted synonyms`() {
        val task = CourseTask(
            id = "m01-l01-q2",
            moduleId = 1,
            lessonId = "m01-l01",
            title = "Пропуски",
            type = "fill",
            fillTemplate = listOf("", " age = ", ";"),
            fillBlanks = listOf(
                FillBlank(id = "type", kind = "select", options = listOf("String", "int"), answer = "int"),
                FillBlank(id = "value", kind = "text", answer = "25", minRange = 0, maxRange = 120)
            )
        )

        assertTrue(OfflineTaskValidator.validate(task, mapOf("type" to "int", "value" to "3")).success)
        assertTrue(OfflineTaskValidator.validate(task, mapOf("type" to "int", "value" to "120")).success)
        assertFalse(OfflineTaskValidator.validate(task, mapOf("type" to "int", "value" to "121")).success)
        assertFalse(OfflineTaskValidator.validate(task, mapOf("type" to "String", "value" to "25")).success)
    }

    @Test
    fun `validateParsons distinguishes wrong order from indentation issues`() {
        val lines = listOf(
            ParsonsLine("a", "public class Main {"),
            ParsonsLine("b", "public static void main(String[] args) {"),
            ParsonsLine("c", "System.out.println(1);"),
            ParsonsLine("d", "}"),
            ParsonsLine("e", "}")
        )
        val solution = listOf(
            ParsonsLine("a", "public class Main {", 0),
            ParsonsLine("b", "public static void main(String[] args) {", 1),
            ParsonsLine("c", "System.out.println(1);", 2),
            ParsonsLine("d", "}", 1),
            ParsonsLine("e", "}", 0)
        )
        val task = CourseTask(
            id = "m01-p04",
            moduleId = 1,
            lessonId = "m01-practice",
            title = "Соберите код",
            type = "parsons",
            parsonsLines = lines,
            parsonsSolution = solution
        )

        assertTrue(OfflineTaskValidator.validate(task, solution).success)

        // Взаимозаменяемые закрывающие скобки d и e
        val swappedBraces = listOf(
            solution[0],
            solution[1],
            solution[2],
            ParsonsLine("e", "}", 1),
            ParsonsLine("d", "}", 0)
        )
        assertTrue(OfflineTaskValidator.validate(task, swappedBraces).success)

        // Нулевые отступы при верном порядке
        val zeroIndents = solution.map { it.copy(indent = 0) }
        val indentIssues = OfflineTaskValidator.parsonsIndentIssues(task, zeroIndents)
        assertEquals(3, indentIssues?.size)
    }

    @Test
    fun `validateCodeOffline evaluates Main and compares stdout in UTF-8`() {
        val task = CourseTask(
            id = "m01-l01-hardcore",
            moduleId = 1,
            lessonId = "m01-l01",
            title = "Хард",
            type = "code",
            difficulty = "hardcore",
            starterCode = "public class Main {\n    public static void main(String[] args) {\n        // TODO\n    }\n}",
            expectedOutput = "Мяч\n7\ntrue"
        )

        val validCode = """
            public class Main {
                public static void main(String[] args) {
                    int units = 7;
                    String item = "Мяч";
                    boolean inStock = true;
                    System.out.println(item);
                    System.out.println(units);
                    System.out.println(inStock);
                }
            }
        """.trimIndent()

        val outcome = OfflineTaskValidator.validate(task, validCode)
        assertTrue(outcome.success)
        assertEquals("Мяч\n7\ntrue\n", outcome.output)
    }

    @Test
    fun `MobileProgressManager awards XP once, grades 80 percent exam and merges external progress_json`() {
        val tempDir = Files.createTempDirectory("javada-android-test-").toFile()
        try {
            val manager = MobileProgressManager(tempDir)
            val task = CourseTask(
                id = "m01-l01-hardcore",
                moduleId = 1,
                lessonId = "m01-l01",
                title = "Хард",
                type = "code",
                difficulty = "hardcore"
            )

            val afterFirst = manager.recordTaskAttempt(task, null, true)
            assertEquals(90, afterFirst.state.xp)
            assertTrue(afterFirst.state.achievements.contains("mastery:m01-l01"))

            // Повторное решение той же задачи не начисляет XP второй раз
            val afterSecond = manager.recordTaskAttempt(task, null, true)
            assertEquals(90, afterSecond.state.xp)

            // Сдача мини-экзамена ровно на 80% (4 из 5)
            val examTasks = (1..5).map { idx ->
                CourseTask(
                    id = "m-exam-01-0$idx",
                    moduleId = 1,
                    lessonId = "m01-exam",
                    title = "Вопрос $idx",
                    type = "choice",
                    options = listOf(ChoiceOption("a", "A")),
                    correctChoices = listOf("a")
                )
            }
            val exam = CourseExam(
                id = "m01-exam",
                moduleId = 1,
                title = "Мини-экзамен",
                passingPercent = 80,
                xpBonus = 310,
                tasks = examTasks
            )
            val mod = CourseModule(id = 1, title = "Модуль 1", subtitle = "", lessons = emptyList(), exam = exam)
            val details = examTasks.mapIndexed { i, t ->
                ExamTaskDetail(id = t.id, correct = i < 4)
            }

            val summary = manager.submitMiniExam(mod, exam, details)
            assertTrue(summary.passed)
            assertEquals(80.0, summary.percent, 0.01)
            assertEquals(90 + 310, manager.getProfile().state.xp)

            // Импорт внешнего progress.json с ПК объединяет награды без потери мобильного прогресса
            val pcJson = """
                {
                  "version": 1,
                  "revision": 5,
                  "state": {
                    "version": 1,
                    "xp": 50,
                    "legacyXp": 0,
                    "xpLedger": { "task:m01-p06": 50 },
                    "completedLessons": ["m01-l01"],
                    "awardedTasks": ["m01-p06"]
                  },
                  "drafts": {
                    "m01-p07": { "code": "// PC draft", "updatedAt": 100 }
                  }
                }
            """.trimIndent()

            val merged = manager.importAndMergeProgressJson(pcJson)
            assertEquals(90 + 310 + 50, merged.state.xp)
            assertTrue(merged.state.completedLessons.contains("m01-l01"))
            assertEquals("// PC draft", merged.drafts["m01-p07"]?.code)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
