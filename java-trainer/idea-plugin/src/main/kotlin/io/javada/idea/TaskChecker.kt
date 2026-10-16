package io.javada.idea

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Результат проверки задачи в TaskChecker.
 */
data class CheckResult(
    val success: Boolean,
    val kind: String, // "ok", "compile_error", "wrong_output", "junit_failed", "runtime_error", "timeout", "cancelled", "unavailable"
    val output: String,
    val error: String,
    val executionTimeMs: Long = 0L
)

/**
 * Нативный движок проверки задач (TaskChecker) для IntelliJ IDEA.
 *
 * Реализует два основных механизма валидации Java-кода через `GeneralCommandLine`:
 * 1. **Сравнение stdout с `expected.txt` / эталонным выводом курса (`STDOUT_EXPECTED`)**:
 *    компилирует код через `javac` и запускает `java Main`, сверяя нормализованный `stdout`
 *    с содержимым файла `expected.txt` (или `expectedOutput` / `private/lab_cases.json`).
 * 2. **Запуск модульных тестов JUnit 5 (`JUNIT_TESTS`)**:
 *    компилирует код студента вместе с тестовым классом `*Test.java` и запускает
 *    `org.junit.platform.console.ConsoleLauncher` (`JUnit Platform Console Standalone`),
 *    анализируя отчёт о прохождении тестов.
 *
 * Также поддерживает нативную проверку интерактивных заданий курса (`copy`, `choice`, `fill`, `matching`, `parsons`).
 */
class TaskChecker(
    private val project: Project,
    private val trainerRoot: Path
) {
    private val log = Logger.getInstance(TaskChecker::class.java)

    companion object {
        private const val SECURITY_CONSENT_KEY = "io.javada.security.firstRunAccepted"
        private const val COMPILE_TIMEOUT_MS = 10_000L
        private const val RUN_TIMEOUT_MS = 5_000L
        private const val MAX_OUTPUT_BYTES = 12_000

        private val JVM_UTF8_FLAGS = listOf(
            "-Dfile.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8",
            "-Dstdout.encoding=UTF-8",
            "-Dstderr.encoding=UTF-8"
        )

        private val JAVAC_UTF8_FLAGS = JVM_UTF8_FLAGS.map { "-J$it" }
    }

    /**
     * Показывает предупреждение безопасности при первом запуске кода пользователя.
     */
    fun ensureFirstRunConsent(): Boolean {
        val props = PropertiesComponent.getInstance(project)
        if (props.getBoolean(SECURITY_CONSENT_KEY, false)) {
            return true
        }
        var accepted = false
        val askRunnable = Runnable {
            val answer = Messages.showYesNoDialog(
                project,
                """
                Код задачи будет скомпилирован и запущен локально на вашем компьютере с правами текущего пользователя ОС.
                
                Ограничение времени выполнения (5 секунд) и памяти JVM предотвращает зависания при бесконечных циклах, но не является изолированной песочницей.
                Запускайте только собственный доверенный код.
                
                Продолжить выполнение кода?
                """.trimIndent(),
                "JavaДа — Предупреждение безопасности при первом запуске",
                "Понятно, запустить код",
                "Отмена",
                Messages.getWarningIcon()
            )
            accepted = (answer == Messages.YES)
            if (accepted) {
                props.setValue(SECURITY_CONSENT_KEY, true)
            }
        }
        if (ApplicationManager.getApplication().isDispatchThread) {
            askRunnable.run()
        } else {
            ApplicationManager.getApplication().invokeAndWait(askRunnable)
        }
        return accepted
    }

    /**
     * Главная точка входа проверки любого задания курса.
     */
    fun checkTask(task: CourseTask, userCodeOrAnswer: Any): CheckResult {
        if (!task.isCodeTask) {
            return checkNonCodeTask(task, userCodeOrAnswer)
        }

        val code = (userCodeOrAnswer as? String) ?: ""
        if (code.isBlank()) {
            return CheckResult(false, "bad_request", "", "Введите Java-код решения перед запуском проверки.")
        }
        if (code.toByteArray(StandardCharsets.UTF_8).size > 16_384) {
            return CheckResult(false, "bad_request", "", "Код решения превышает допустимый лимит 16 КБ.")
        }

        if (task.type == "copy") {
            return checkCopyTask(task, code)
        }

        // Проверяем согласие пользователя при первом запуске
        if (!ensureFirstRunConsent()) {
            return CheckResult(false, "cancelled", "", "Запуск отменён пользователем.")
        }

        val (javacPath, javaPath, isJava25OrNewer) = resolveJdkBinaries()
            ?: return CheckResult(
                false,
                "unavailable",
                "",
                "JDK не найден. Настройте Project SDK (Java 17–25) в File -> Project Structure или переменную JAVA_HOME."
            )

        return when (task.verificationMode) {
            VerificationMode.JUNIT_TESTS ->
                checkWithJUnitConsole(task, code, javacPath, javaPath, isJava25OrNewer)
            VerificationMode.COMPILE_ONLY ->
                checkCompileOnly(task, code, javacPath, isJava25OrNewer)
            VerificationMode.STDOUT_EXPECTED ->
                checkStdoutWithExpectedTxt(task, code, javacPath, javaPath, isJava25OrNewer)
        }
    }

    /**
     * Вариант 1: Проверка задачи путём компиляции (`javac`), запуска (`java`) через `GeneralCommandLine`
     * и сравнения `stdout` с файлом `expected.txt` (или эталонными данными из репозитория).
     */
    fun checkStdoutWithExpectedTxt(
        task: CourseTask,
        code: String,
        javacPath: String,
        javaPath: String,
        enablePreview: Boolean = false
    ): CheckResult {
        val tempDir = Files.createTempDirectory("javada-stdout-")
        val startTime = System.currentTimeMillis()
        try {
            val sourceFile = tempDir.resolve(task.targetFileName)
            Files.writeString(sourceFile, code, StandardCharsets.UTF_8)

            // 1. Компиляция через GeneralCommandLine
            val compileResult = runCompileCommand(
                javacPath = javacPath,
                workDir = tempDir,
                sourceFiles = listOf(sourceFile),
                classpath = emptyList(),
                enablePreview = enablePreview
            )
            if (compileResult.timedOut) {
                return CheckResult(
                    false,
                    "compile_timeout",
                    "",
                    "Компиляция превысила лимит ${COMPILE_TIMEOUT_MS / 1000} секунд."
                )
            }
            if (compileResult.exitCode != 0) {
                val cleanDiag = sanitizeDiagnostics(compileResult.stderr.ifBlank { compileResult.stdout }, tempDir)
                return CheckResult(
                    false,
                    "compile_error",
                    "",
                    cleanDiag.ifBlank { "Ошибка компиляции ${task.targetFileName}." }
                )
            }

            // 2. Чтение тестовых данных: приоритет у файла expected.txt (и опционального input.txt),
            //    затем у набора testCases (для лабораторных) и поля expectedOutput из JSON курса
            val cases: List<TestCaseData> = resolveExpectedCases(task, tempDir)

            // 3. Запуск с общим бюджетом времени 5 секунд на все тест-кейсы
            val mainClass = task.targetFileName.removeSuffix(".java")
            val deadlineMs = System.currentTimeMillis() + RUN_TIMEOUT_MS

            var lastStdout = ""
            for ((index, testCase) in cases.withIndex()) {
                val remainingMs = deadlineMs - System.currentTimeMillis()
                if (remainingMs <= 0) {
                    return CheckResult(
                        false,
                        "timeout",
                        lastStdout,
                        "Превышен общий лимит выполнения 5 секунд. Проверьте программу на зацикливание."
                    )
                }

                val runCmd = buildJavaCommandLine(
                    javaPath = javaPath,
                    workDir = tempDir,
                    classpath = listOf(tempDir),
                    mainClass = mainClass,
                    enablePreview = enablePreview
                )

                val exec = executeWithTimeout(
                    commandLine = runCmd,
                    timeoutMs = remainingMs,
                    stdinData = testCase.input.takeIf { it.isNotEmpty() || task.testCases.isNotEmpty() }
                )
                lastStdout = exec.stdout
                val cleanErr = sanitizeDiagnostics(exec.stderr, tempDir)

                if (exec.timedOut) {
                    val prefix = if (cases.size > 1) "Тест ${index + 1}/${cases.size}. Ввод:\n${testCase.input}\n" else ""
                    return CheckResult(
                        false,
                        "timeout",
                        lastStdout,
                        "${prefix}Время выполнения превысило 5 секунд (процесс принудительно завершён через Process.destroy())."
                    )
                }

                if (exec.exitCode != 0) {
                    val prefix = if (cases.size > 1) "Тест ${index + 1}/${cases.size}. Ввод:\n${testCase.input}\n" else ""
                    return CheckResult(
                        false,
                        "runtime_error",
                        lastStdout,
                        prefix + cleanErr.ifBlank { "Программа завершилась с кодом ${exec.exitCode}." }
                    )
                }

                val actualNorm = normalizeOutput(lastStdout)
                val expectedNorm = normalizeOutput(testCase.expectedOutput)
                if (actualNorm != expectedNorm) {
                    val errorDetail = if (cases.size > 1) {
                        "Проверка ${index + 1}/${cases.size} не пройдена.\nВходные данные:\n${testCase.input.trimEnd()}\n\n" +
                                "Ожидаемый вывод (expected.txt / эталон):\n$expectedNorm\n\nФактический вывод:\n$actualNorm"
                    } else {
                        "Вывод программы отличается от ожидаемого.\n\n" +
                                "Ожидалось:\n$expectedNorm\n\nПолучено:\n$actualNorm"
                    }
                    return CheckResult(
                        false,
                        "wrong_output",
                        lastStdout,
                        errorDetail,
                        System.currentTimeMillis() - startTime
                    )
                }
            }

            val finalOutput = if (cases.size > 1) {
                "Пройдено ${cases.size} из ${cases.size} проверок.\nПоследний вывод:\n$lastStdout"
            } else {
                lastStdout
            }
            return CheckResult(
                true,
                "ok",
                finalOutput,
                "",
                System.currentTimeMillis() - startTime
            )
        } finally {
            deleteDirectoryQuietly(tempDir)
        }
    }

    /**
     * Вариант 2: Проверка задачи через запуск JUnit 5 тестов (`JUnit Platform Console Standalone`)
     * с использованием `GeneralCommandLine` и таймаута 5 секунд (`Process.destroy()`).
     */
    fun checkWithJUnitConsole(
        task: CourseTask,
        code: String,
        javacPath: String,
        javaPath: String,
        enablePreview: Boolean = false
    ): CheckResult {
        val tempDir = Files.createTempDirectory("javada-junit-")
        val startTime = System.currentTimeMillis()
        try {
            val junitJar = ensureJUnitConsoleJar()
                ?: return CheckResult(
                    false,
                    "unavailable",
                    "",
                    "Не найден junit-platform-console-standalone.jar для запуска JUnit-тестов."
                )

            // Записываем решение пользователя
            val userSourceFile = tempDir.resolve(task.targetFileName)
            Files.writeString(userSourceFile, code, StandardCharsets.UTF_8)

            // Определяем файл JUnit-тестов (из пути задачи или генерируем адаптер тестов)
            val testSourceFile = resolveOrCreateJUnitTestFile(task, tempDir)
            val testClassName = task.junitTestClassName
                ?: testSourceFile.fileName.toString().removeSuffix(".java")

            // 1. Компилируем решение + JUnit-тесты с junit-platform-console-standalone.jar в classpath
            val compileResult = runCompileCommand(
                javacPath = javacPath,
                workDir = tempDir,
                sourceFiles = listOf(userSourceFile, testSourceFile),
                classpath = listOf(junitJar),
                enablePreview = enablePreview
            )
            if (compileResult.timedOut) {
                return CheckResult(false, "compile_timeout", "", "Компиляция JUnit-тестов превысила лимит 10 секунд.")
            }
            if (compileResult.exitCode != 0) {
                val diag = sanitizeDiagnostics(compileResult.stderr.ifBlank { compileResult.stdout }, tempDir)
                return CheckResult(false, "compile_error", "", diag.ifBlank { "Ошибка компиляции решения или JUnit-тестов." })
            }

            // 2. Запускаем JUnit Platform Console Launcher через GeneralCommandLine
            val junitCmd = GeneralCommandLine().apply {
                exePath = javaPath
                withWorkDirectory(tempDir.toFile())
                withCharset(StandardCharsets.UTF_8)
                addParameters("-Xms16m", "-Xmx128m")
                addParameters(JVM_UTF8_FLAGS)
                if (enablePreview) {
                    addParameter("--enable-preview")
                }
                addParameters(
                    "-cp",
                    "${tempDir.toAbsolutePath()}${File.pathSeparator}${junitJar.toAbsolutePath()}",
                    "org.junit.platform.console.ConsoleLauncher",
                    "execute",
                    "--disable-banner",
                    "--details=tree",
                    "--details-theme=unicode",
                    "--select-class=$testClassName"
                )
            }

            val exec = executeWithTimeout(junitCmd, RUN_TIMEOUT_MS, stdinData = null)
            val elapsed = System.currentTimeMillis() - startTime

            if (exec.timedOut) {
                return CheckResult(
                    false,
                    "timeout",
                    exec.stdout,
                    "Выполнение JUnit-тестов превысило 5 секунд и было остановлено через Process.destroy().",
                    elapsed
                )
            }

            val combinedReport = buildString {
                if (exec.stdout.isNotBlank()) append(exec.stdout.trim())
                if (exec.stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(sanitizeDiagnostics(exec.stderr.trim(), tempDir))
                }
            }

            return if (exec.exitCode == 0) {
                CheckResult(
                    true,
                    "ok",
                    combinedReport.ifBlank { "Все JUnit-тесты успешно пройдены." },
                    "",
                    elapsed
                )
            } else {
                CheckResult(
                    false,
                    "junit_failed",
                    exec.stdout,
                    "Часть JUnit-тестов не пройдена:\n$combinedReport",
                    elapsed
                )
            }
        } finally {
            deleteDirectoryQuietly(tempDir)
        }
    }

    private fun checkCompileOnly(
        task: CourseTask,
        code: String,
        javacPath: String,
        enablePreview: Boolean
    ): CheckResult {
        val tempDir = Files.createTempDirectory("javada-compile-")
        try {
            val sourceFile = tempDir.resolve(task.targetFileName)
            Files.writeString(sourceFile, code, StandardCharsets.UTF_8)
            val compiled = runCompileCommand(javacPath, tempDir, listOf(sourceFile), emptyList(), enablePreview)
            if (compiled.timedOut) {
                return CheckResult(false, "compile_timeout", "", "Компиляция превысила лимит 10 секунд.")
            }
            if (compiled.exitCode != 0) {
                val diag = sanitizeDiagnostics(compiled.stderr.ifBlank { compiled.stdout }, tempDir)
                return CheckResult(false, "compile_error", "", diag)
            }
            return CheckResult(true, "ok", "Компиляция прошла успешно.", "")
        } finally {
            deleteDirectoryQuietly(tempDir)
        }
    }

    /**
     * Загружает тестовые данные для сверки `stdout`:
     * 1. Если для задачи существует файл `expected.txt` (в `task.expectedTxtPath` или `tasks/<id>/expected.txt`),
     *    читает ожидаемый вывод из `expected.txt` (и входные данные из `input.txt`, если он есть рядом).
     * 2. Если задача содержит массив `testCases` (например, лабораторная из `private/lab_cases.json`), использует их.
     * 3. Иначе использует поле `expectedOutput` из JSON-описания задачи курса.
     */
    private fun resolveExpectedCases(task: CourseTask, workDir: Path): List<TestCaseData> {
        val explicitExpectedTxt = task.expectedTxtPath
            ?: trainerRoot.resolve("tasks").resolve(task.id).resolve("expected.txt").takeIf { Files.isRegularFile(it) }
            ?: workDir.resolve("expected.txt").takeIf { Files.isRegularFile(it) }

        if (explicitExpectedTxt != null && Files.isRegularFile(explicitExpectedTxt)) {
            val expectedText = Files.readString(explicitExpectedTxt, StandardCharsets.UTF_8)
            val siblingInput = explicitExpectedTxt.parent?.resolve("input.txt")
            val inputText = if (siblingInput != null && Files.isRegularFile(siblingInput)) {
                Files.readString(siblingInput, StandardCharsets.UTF_8)
            } else {
                task.sampleInput
            }
            return listOf(TestCaseData(input = inputText, expectedOutput = expectedText))
        }

        if (task.testCases.isNotEmpty()) {
            return task.testCases
        }

        return listOf(TestCaseData(input = task.sampleInput, expectedOutput = task.expectedOutput))
    }

    /**
     * Находит существующий файл JUnit-теста для задачи или создаёт стандартный JUnit 5 тест `MainTest.java`,
     * проверяющий выполнение `Main.main(new String[0])` и соответствие вывода `expected.txt` / `expectedOutput`.
     */
    private fun resolveOrCreateJUnitTestFile(task: CourseTask, tempDir: Path): Path {
        val existing = task.junitTestSourcePath?.takeIf { Files.isRegularFile(it) }
            ?: trainerRoot.resolve("tasks").resolve(task.id).resolve("MainTest.java").takeIf { Files.isRegularFile(it) }

        val targetTestFile = tempDir.resolve("MainTest.java")
        if (existing != null) {
            Files.copy(existing, targetTestFile, StandardCopyOption.REPLACE_EXISTING)
            return targetTestFile
        }

        val expectedEscaped = task.expectedOutput
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n")

        val generatedTest = """
            import org.junit.jupiter.api.Assertions;
            import org.junit.jupiter.api.Test;
            import java.io.ByteArrayOutputStream;
            import java.io.PrintStream;
            import java.nio.charset.StandardCharsets;

            public class MainTest {
                @Test
                public void testMainOutputMatchesExpected() throws Exception {
                    PrintStream originalOut = System.out;
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    try {
                        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
                        Main.main(new String[0]);
                    } finally {
                        System.setOut(originalOut);
                    }
                    String actual = buffer.toString(StandardCharsets.UTF_8)
                            .replace("\r\n", "\n")
                            .trim();
                    String expected = "$expectedEscaped".trim();
                    Assertions.assertEquals(expected, actual, "Вывод Main.main() не совпадает с ожидаемым эталоном");
                }
            }
        """.trimIndent()

        Files.writeString(targetTestFile, generatedTest, StandardCharsets.UTF_8)
        return targetTestFile
    }

    /**
     * Извлекает упакованный в ресурсы плагина `junit-platform-console-standalone.jar` во временный кэш.
     */
    private fun ensureJUnitConsoleJar(): Path? {
        val cacheJar = Path.of(System.getProperty("java.io.tmpdir"), "javada-junit-platform-console-standalone.jar")
        if (Files.isRegularFile(cacheJar) && Files.size(cacheJar) > 100_000L) {
            return cacheJar
        }
        val resourceStream = javaClass.classLoader.getResourceAsStream("lib/junit-platform-console-standalone.jar")
        if (resourceStream != null) {
            resourceStream.use { input ->
                Files.copy(input, cacheJar, StandardCopyOption.REPLACE_EXISTING)
            }
            return cacheJar
        }
        return null
    }

    private fun runCompileCommand(
        javacPath: String,
        workDir: Path,
        sourceFiles: List<Path>,
        classpath: List<Path>,
        enablePreview: Boolean
    ): RawProcessOutput {
        val cmd = GeneralCommandLine().apply {
            exePath = javacPath
            withWorkDirectory(workDir.toFile())
            withCharset(StandardCharsets.UTF_8)
            addParameters(JAVAC_UTF8_FLAGS)
            addParameters("-J-Xmx256m", "-encoding", "UTF-8")
            if (enablePreview) {
                addParameters("--enable-preview", "--release", "25")
            }
            if (classpath.isNotEmpty()) {
                addParameters("-cp", classpath.joinToString(File.pathSeparator) { it.toAbsolutePath().toString() })
            }
            addParameters("-d", workDir.toAbsolutePath().toString())
            sourceFiles.forEach { addParameter(it.toAbsolutePath().toString()) }
        }
        return executeWithTimeout(cmd, COMPILE_TIMEOUT_MS, stdinData = null)
    }

    private fun buildJavaCommandLine(
        javaPath: String,
        workDir: Path,
        classpath: List<Path>,
        mainClass: String,
        enablePreview: Boolean
    ): GeneralCommandLine {
        return GeneralCommandLine().apply {
            exePath = javaPath
            withWorkDirectory(workDir.toFile())
            withCharset(StandardCharsets.UTF_8)
            addParameters(
                "-Xms16m",
                "-Xmx96m",
                "-XX:MaxMetaspaceSize=128m",
                "-XX:CompressedClassSpaceSize=64m",
                "-XX:ReservedCodeCacheSize=64m",
                "-XX:ActiveProcessorCount=2"
            )
            addParameters(JVM_UTF8_FLAGS)
            if (enablePreview) {
                addParameter("--enable-preview")
            }
            addParameters(
                "-cp",
                classpath.joinToString(File.pathSeparator) { it.toAbsolutePath().toString() },
                mainClass
            )
        }
    }

    private data class RawProcessOutput(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean
    )

    /**
     * Запускает внешний процесс через `GeneralCommandLine` с жёстким контролем времени выполнения
     * (по умолчанию 5 секунд) и принудительным завершением `Process.destroy()` / `destroyForcibly()`.
     */
    private fun executeWithTimeout(
        commandLine: GeneralCommandLine,
        timeoutMs: Long,
        stdinData: String?
    ): RawProcessOutput {
        val process = commandLine.createProcess()
        val stdoutBuffer = ByteArrayOutputStream()
        val stderrBuffer = ByteArrayOutputStream()

        val stdoutThread = Thread({
            process.inputStream.use { input ->
                val buf = ByteArray(4096)
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    synchronized(stdoutBuffer) {
                        if (stdoutBuffer.size() < MAX_OUTPUT_BYTES + 1) {
                            val canWrite = (MAX_OUTPUT_BYTES + 1 - stdoutBuffer.size()).coerceAtMost(read)
                            stdoutBuffer.write(buf, 0, canWrite)
                        }
                    }
                }
            }
        }, "javada-stdout-reader").apply { isDaemon = true; start() }

        val stderrThread = Thread({
            process.errorStream.use { input ->
                val buf = ByteArray(4096)
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    synchronized(stderrBuffer) {
                        if (stderrBuffer.size() < MAX_OUTPUT_BYTES + 1) {
                            val canWrite = (MAX_OUTPUT_BYTES + 1 - stderrBuffer.size()).coerceAtMost(read)
                            stderrBuffer.write(buf, 0, canWrite)
                        }
                    }
                }
            }
        }, "javada-stderr-reader").apply { isDaemon = true; start() }

        try {
            process.outputStream.use { out ->
                if (!stdinData.isNullOrEmpty()) {
                    out.write(stdinData.toByteArray(StandardCharsets.UTF_8))
                    out.flush()
                }
            }
        } catch (_: IOException) {
            // Процесс мог завершиться до чтения всего stdin
        }

        val finished = process.waitFor(max(100L, timeoutMs), TimeUnit.MILLISECONDS)
        if (!finished) {
            // Сначала мягкое завершение через Process.destroy(), затем жёсткое завершение дерева процессов
            process.descendants().forEach { handle ->
                try { handle.destroy() } catch (_: Exception) {}
            }
            process.destroy()
            if (!process.waitFor(300, TimeUnit.MILLISECONDS)) {
                process.descendants().forEach { handle ->
                    try { handle.destroyForcibly() } catch (_: Exception) {}
                }
                process.destroyForcibly()
                process.waitFor(500, TimeUnit.MILLISECONDS)
            }
        }

        stdoutThread.join(500)
        stderrThread.join(500)

        val outText = decodeCapped(stdoutBuffer.toByteArray())
        val errText = decodeCapped(stderrBuffer.toByteArray())
        val exitCode = if (finished) process.exitValue() else -1

        return RawProcessOutput(
            exitCode = exitCode,
            stdout = outText,
            stderr = errText,
            timedOut = !finished
        )
    }

    private fun decodeCapped(bytes: ByteArray): String {
        val truncated = bytes.size > MAX_OUTPUT_BYTES
        val length = bytes.size.coerceAtMost(MAX_OUTPUT_BYTES)
        val decoded = String(bytes, 0, length, StandardCharsets.UTF_8)
        return if (truncated) "$decoded\n… Вывод обрезан (лимит 12 КБ)." else decoded
    }

    /**
     * Нормализует вывод перед сравнением (игнорирует CRLF/LF и хвостовые пробелы в конце строк,
     * сохраняя внутренние пробелы и порядок строк — в точности как `server.py:normalized_output`).
     */
    fun normalizeOutput(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .joinToString("\n") { it.trimEnd(' ', '\t') }
            .trimEnd('\n')
    }

    private fun sanitizeDiagnostics(text: String, tempDir: Path): String {
        val prefixWithSep = tempDir.toAbsolutePath().toString() + File.separator
        return text
            .replace(prefixWithSep, "")
            .replace(tempDir.toAbsolutePath().toString(), "")
            .trim()
    }

    /**
     * Проверка задания на переписывание образца кода (`copy`) по потоку значимых токенов.
     */
    private fun checkCopyTask(task: CourseTask, code: String): CheckResult {
        val expectedTokens = tokenizeCopyCode(task.sourceCode, task.language)
        val actualTokens = tokenizeCopyCode(code, task.language)
        return if (expectedTokens == actualTokens) {
            CheckResult(true, "ok", "Образец переписан верно!\n\n${task.explanation}", "")
        } else {
            CheckResult(
                false,
                "wrong_output",
                "",
                "Код отличается от образца. Проверьте имена переменных, строковые литералы, регистр букв и скобки."
            )
        }
    }

    private fun tokenizeCopyCode(text: String, language: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (language != "java") {
            return normalized.split("\n").map { it.trimEnd() }
        }
        val tokenRegex = Regex(
            """"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|//[^\n]*|/\*[\s\S]*?\*/|[\p{L}_$][\p{L}\p{N}_$]*|\d+(?:[._]\d+)*|[^\s]"""
        )
        return tokenRegex.findAll(normalized).map { it.value }.toList()
    }

    /**
     * Нативная проверка тестовых и интерактивных заданий (`choice`, `fill`, `matching`, `parsons`) для уроков и мини-экзаменов.
     */
    @Suppress("UNCHECKED_CAST")
    private fun checkNonCodeTask(task: CourseTask, answer: Any): CheckResult {
        val isCorrect = when (task.type) {
            "choice" -> {
                val selected = (answer as? Collection<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                selected.size == task.correctChoices.size && selected.toSet() == task.correctChoices.toSet()
            }
            "fill" -> {
                val map = (answer as? Map<*, *>)?.entries?.associate {
                    it.key.toString() to (it.value?.toString()?.trim() ?: "")
                } ?: emptyMap()
                task.fillBlanks.all { blank ->
                    val input = map[blank.id] ?: ""
                    if (blank.minRange != null && blank.maxRange != null) {
                        val intRegex = Regex("^-?(?:0|[1-9][0-9]*)$")
                        val parsed = if (intRegex.matches(input) && input.length <= 12) input.toLongOrNull() else null
                        parsed != null && parsed >= blank.minRange && parsed <= blank.maxRange
                    } else {
                        val allowed = if (blank.accepted.isNotEmpty()) blank.accepted else listOf(blank.answer)
                        allowed.any { it.trim() == input }
                    }
                }
            }
            "matching" -> {
                val map = (answer as? Map<*, *>)?.entries?.associate {
                    it.key.toString() to (it.value?.toString() ?: "")
                } ?: emptyMap()
                task.matchingPairs.all { pair -> map[pair.id] == pair.id }
            }
            "parsons" -> {
                val rows = (answer as? List<*>)?.filterIsInstance<ParsonsLine>() ?: emptyList()
                checkParsonsOrder(task, rows)
            }
            else -> false
        }

        return if (isCorrect) {
            CheckResult(true, "ok", task.explanation.ifBlank { "Ответ верный!" }, "")
        } else {
            CheckResult(
                false,
                "wrong_output",
                "",
                "Ответ неверен. ${task.hint.takeIf { it.isNotBlank() } ?: "Проверьте условие задания."}"
            )
        }
    }

    private fun checkParsonsOrder(task: CourseTask, rows: List<ParsonsLine>): Boolean {
        if (rows.size != task.parsonsLines.size) return false
        val textById = task.parsonsLines.associate { it.id to it.text }
        if (rows.map { it.id }.toSet().size != task.parsonsLines.size) return false
        val indentById = task.parsonsSolution.associate { it.id to it.indent }
        val canonicalOrder = task.parsonsSolution.map { it.id }
        val validOrders = listOf(canonicalOrder) + task.parsonsAlternativeOrders

        return validOrders.any { order ->
            order.indices.all { idx ->
                val expectedId = order[idx]
                val userRow = rows[idx]
                textById[userRow.id] == textById[expectedId] && userRow.indent == indentById[expectedId]
            }
        }
    }

    private data class JdkBinaries(
        val javacPath: String,
        val javaPath: String,
        val isJava25OrNewer: Boolean
    )

    /**
     * Определяет пути к `javac` и `java`: сначала проверяет SDK, настроенный в открытом проекте IDEA,
     * затем переменную окружения `JAVA_HOME` и системный `PATH`.
     */
    private fun resolveJdkBinaries(): JdkBinaries? {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val javacName = if (isWindows) "javac.exe" else "javac"
        val javaName = if (isWindows) "java.exe" else "java"

        val sdkHome = ProjectRootManager.getInstance(project).projectSdk?.homePath
        val candidates = listOfNotNull(
            sdkHome?.let { Path.of(it, "bin") },
            System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { Path.of(it, "bin") }
        )

        for (binDir in candidates) {
            val javac = binDir.resolve(javacName)
            val java = binDir.resolve(javaName)
            if (Files.isExecutable(javac) && Files.isExecutable(java)) {
                val versionString = ProjectRootManager.getInstance(project).projectSdk?.versionString ?: ""
                val is25 = versionString.contains("25")
                return JdkBinaries(javac.toAbsolutePath().toString(), java.toAbsolutePath().toString(), is25)
            }
        }

        // Фолбэк на системный PATH
        val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator)
        for (dir in pathDirs) {
            if (dir.isBlank()) continue
            val binDir = Path.of(dir)
            val javac = binDir.resolve(javacName)
            val java = binDir.resolve(javaName)
            if (Files.isExecutable(javac) && Files.isExecutable(java)) {
                return JdkBinaries(javac.toAbsolutePath().toString(), java.toAbsolutePath().toString(), false)
            }
        }
        return null
    }

    private fun deleteDirectoryQuietly(dir: Path) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        } catch (e: Exception) {
            log.debug("Не удалось удалить временную директорию $dir", e)
        }
    }
}
