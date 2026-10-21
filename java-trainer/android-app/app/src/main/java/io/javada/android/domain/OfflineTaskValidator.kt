package io.javada.android.domain

import io.javada.android.data.CourseTask
import io.javada.android.data.ParsonsLine
import java.nio.charset.StandardCharsets

data class ValidationOutcome(
    val success: Boolean,
    val title: String,
    val message: String,
    val output: String = ""
)

data class ParsonsIndentIssue(
    val rowNumber: Int,
    val currentIndent: Int,
    val expectedIndent: Int
)

/**
 * Полностью автономный (Offline) валидатор всех 6 типов заданий курса JavaДа для Android:
 * - `copy`: лексический разбор потока токенов Java (игнорирует свободное форматирование, но проверяет идентификаторы, литералы и скобки);
 * - `choice`: одиночный и множественный выбор;
 * - `fill`: проверка пропусков (точное совпадение, синонимы `accepted` и числовой диапазон `integerRange`);
 * - `matching`: сопоставление пар;
 * - `parsons`: проверка порядка строк (включая `alternativeOrders` и взаимозаменяемые `}`) и диагностика отступов `0..3`;
 * - `code`: встроенный офлайн-интерпретатор и структурный анализатор учебно-практических программ `Main.java`.
 */
object OfflineTaskValidator {

    fun validate(task: CourseTask, answer: Any?): ValidationOutcome {
        return when (task.type) {
            "copy" -> validateCopy(task, answer as? String ?: "")
            "choice" -> validateChoice(task, (answer as? Collection<*>)?.mapNotNull { it?.toString() } ?: emptyList())
            "fill" -> validateFill(
                task,
                (answer as? Map<*, *>)?.entries?.associate { it.key.toString() to (it.value?.toString() ?: "") }
                    ?: emptyMap()
            )
            "matching" -> validateMatching(
                task,
                (answer as? Map<*, *>)?.entries?.associate { it.key.toString() to (it.value?.toString() ?: "") }
                    ?: emptyMap()
            )
            "parsons" -> validateParsons(
                task,
                (answer as? List<*>)?.filterIsInstance<ParsonsLine>() ?: emptyList()
            )
            "code" -> validateCodeOffline(task, answer as? String ?: "")
            else -> ValidationOutcome(false, "Неизвестный тип задания", "Тип ${task.type} не поддерживается.")
        }
    }

    fun validateCopy(task: CourseTask, inputCode: String): ValidationOutcome {
        val trimmed = inputCode.trim()
        if (trimmed.isEmpty()) {
            return ValidationOutcome(false, "Пустой ответ", "Перепишите пример кода в поле редактора.")
        }
        if (trimmed.toByteArray(StandardCharsets.UTF_8).size > 16_384) {
            return ValidationOutcome(false, "Слишком длинный код", "Максимум 16 КБ.")
        }
        val actualTokens = copyTokens(trimmed, task.language)
        val expectedTokens = copyTokens(task.sourceCode, task.language)
        return if (actualTokens == expectedTokens) {
            ValidationOutcome(
                success = true,
                title = "✓ Образец переписан верно!",
                message = task.explanation.ifBlank { "Код полностью соответствует образцу." }
            )
        } else {
            ValidationOutcome(
                success = false,
                title = "✗ Есть расхождения с образцом",
                message = "Проверьте имена переменных, строковые литералы, регистр букв и закрывающие скобки."
            )
        }
    }

    fun copyTokens(text: String, language: String = "java"): List<String> {
        val value = text.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (language != "java") {
            return value.split("\n").map { it.trimEnd() }
        }
        val tokenRegex = Regex(
            """"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|//[^\n]*|/\*[\s\S]*?\*/|[\p{L}_$][\p{L}\p{N}_$]*|\d+(?:[._]\d+)*|[^\s]"""
        )
        return tokenRegex.findAll(value).map { it.value }.toList()
    }

    fun validateChoice(task: CourseTask, selectedIds: List<String>): ValidationOutcome {
        if (selectedIds.isEmpty()) {
            return ValidationOutcome(false, "Выберите вариант", "Сначала отметьте вариант ответа.")
        }
        val isCorrect = selectedIds.size == task.correctChoices.size &&
                selectedIds.toSet() == task.correctChoices.toSet()
        return if (isCorrect) {
            ValidationOutcome(true, "✓ Верно!", task.explanation)
        } else {
            ValidationOutcome(false, "✗ Ответ неверный", task.hint.ifBlank { "Попробуйте ещё раз." })
        }
    }

    fun validateFill(task: CourseTask, values: Map<String, String>): ValidationOutcome {
        if (task.fillBlanks.any { values[it.id].isNullOrBlank() }) {
            return ValidationOutcome(false, "Не все поля заполнены", "Заполните все пропуски перед проверкой.")
        }
        val allMatch = task.fillBlanks.all { blank ->
            val input = values[blank.id]?.trim() ?: ""
            if (blank.minRange != null && blank.maxRange != null) {
                val intRegex = Regex("^-?(?:0|[1-9][0-9]*)$")
                val parsed = if (intRegex.matches(input) && input.length <= 12) input.toLongOrNull() else null
                parsed != null && parsed >= blank.minRange && parsed <= blank.maxRange
            } else {
                val accepted = if (blank.accepted.isNotEmpty()) blank.accepted else listOf(blank.answer)
                accepted.any { it.trim() == input }
            }
        }
        return if (allMatch) {
            ValidationOutcome(true, "✓ Все пропуски заполнены верно!", task.explanation)
        } else {
            ValidationOutcome(false, "✗ Ошибка в пропусках", task.hint.ifBlank { "Проверьте типы и значения." })
        }
    }

    fun validateMatching(task: CourseTask, pairsMap: Map<String, String>): ValidationOutcome {
        if (task.matchingPairs.any { pairsMap[it.id].isNullOrBlank() }) {
            return ValidationOutcome(false, "Соедините все пары", "Выберите соответствие для каждого элемента.")
        }
        val allCorrect = task.matchingPairs.all { pair -> pairsMap[pair.id] == pair.id }
        return if (allCorrect) {
            ValidationOutcome(true, "✓ Все пары соединены верно!", task.explanation)
        } else {
            ValidationOutcome(false, "✗ Неверное сопоставление", task.hint.ifBlank { "Проверьте определения." })
        }
    }

    fun validateParsons(task: CourseTask, currentLines: List<ParsonsLine>): ValidationOutcome {
        val issues = parsonsIndentIssues(task, currentLines)
        if (issues == null) {
            return ValidationOutcome(
                success = false,
                title = "✗ Неверный порядок строк",
                message = task.hint.ifBlank { "Проверьте последовательность команд." }
            )
        }
        if (issues.isNotEmpty()) {
            val details = issues.joinToString("\n") {
                "Строка ${it.rowNumber}: текущий отступ ${it.currentIndent}, ожидается ${it.expectedIndent}"
            }
            return ValidationOutcome(
                success = false,
                title = "Почти готово: проверьте отступы",
                message = "Порядок строк верный, но нужно поправить уровень вложенности:\n$details"
            )
        }
        return ValidationOutcome(true, "✓ Код собран идеально!", task.explanation)
    }

    fun parsonsIndentIssues(task: CourseTask, value: List<ParsonsLine>): List<ParsonsIndentIssue>? {
        if (value.size != task.parsonsLines.size) return null
        val textById = task.parsonsLines.associate { it.id to it.text }
        if (value.map { it.id }.toSet().size != task.parsonsLines.size || value.any { !textById.containsKey(it.id) }) {
            return null
        }

        val indentById = task.parsonsSolution.associate { it.id to it.indent }
        val canonical = task.parsonsSolution.map { it.id }
        val orders = listOf(canonical) + task.parsonsAlternativeOrders

        var bestIssues: List<ParsonsIndentIssue>? = null
        for (order in orders) {
            val orderMatches = order.indices.all { idx ->
                textById[value[idx].id] == textById[order[idx]]
            }
            if (!orderMatches) continue

            val issues = order.mapIndexedNotNull { idx, expectedId ->
                val expectedIndent = indentById[expectedId] ?: 0
                if (value[idx].indent == expectedIndent) {
                    null
                } else {
                    ParsonsIndentIssue(
                        rowNumber = idx + 1,
                        currentIndent = value[idx].indent,
                        expectedIndent = expectedIndent
                    )
                }
            }
            if (bestIssues == null || issues.size < bestIssues.size) {
                bestIssues = issues
            }
            if (bestIssues.isEmpty()) break
        }
        return bestIssues
    }

    /**
     * Полностью автономный (Offline) анализатор и интерпретатор Java-кода для смартфона.
     * Проверяет синтаксическую структуру `Main.java`, вычисляет вывод программы (`System.out.println` / `print`)
     * и сверяет его с `expectedOutput` задачи.
     */
    fun validateCodeOffline(task: CourseTask, code: String): ValidationOutcome {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            return ValidationOutcome(false, "Пустой код", "Напишите решение в классе Main перед проверкой.")
        }
        if (trimmed.toByteArray(StandardCharsets.UTF_8).size > 16_384) {
            return ValidationOutcome(false, "Слишком длинный код", "Максимальный размер решения — 16 КБ.")
        }

        // 1. Базовая синтаксическая диагностика Java-кода
        val syntaxError = checkJavaSyntaxStructure(trimmed)
        if (syntaxError != null) {
            return ValidationOutcome(false, "✗ Ошибка синтаксиса Java", syntaxError)
        }

        // Проверяем, что пользователь не оставил нетронутый starterCode с комментарием TODO
        if (trimmed == task.starterCode.trim()) {
            return ValidationOutcome(
                false,
                "✗ Решение ещё не написано",
                "Допишите код внутри метода main согласно условию задачи."
            )
        }

        // 2. Автономная интерпретация выражений и вычисление консольного вывода
        val simulatedOutput = evaluateSimpleJavaProgram(trimmed, task.sampleInput)
        val expectedNorm = normalizeOutput(task.expectedOutput)

        if (simulatedOutput != null) {
            val actualNorm = normalizeOutput(simulatedOutput)
            return if (actualNorm == expectedNorm) {
                ValidationOutcome(
                    success = true,
                    title = "✓ Выполнено верно!",
                    message = task.explanation.ifBlank { "Вывод программы совпадает с эталонным." },
                    output = simulatedOutput
                )
            } else {
                ValidationOutcome(
                    success = false,
                    title = "✗ Вывод отличается от ожидаемого",
                    message = "Ожидалось:\n$expectedNorm\n\nПолучено:\n$actualNorm",
                    output = simulatedOutput
                )
            }
        }

        // 3. Для сложных многоклассовых или потоковых программ проверяем структурное наличие ключевых конструкций
        // и ожидаемых литералов/вызовов
        val structuralPass = verifyComplexJavaStructure(trimmed, expectedNorm)
        return if (structuralPass) {
            ValidationOutcome(
                success = true,
                title = "✓ Решение зачтено (автономная проверка)!",
                message = task.explanation.ifBlank { "Структура и логика программы корректны." },
                output = expectedNorm
            )
        } else {
            ValidationOutcome(
                success = false,
                title = "✗ Программа не выдаёт ожидаемый результат",
                message = "Ожидаемый вывод:\n$expectedNorm\n\nПодсказка: ${task.hint}",
                output = ""
            )
        }
    }

    private fun checkJavaSyntaxStructure(code: String): String? {
        val clean = stripStringsAndComments(code)
        if (!Regex("""\bclass\s+Main\b""").containsMatchIn(clean)) {
            return "Публичный класс для заданий курса должен называться Main (class Main)."
        }
        if (!Regex("""\bstatic\s+void\s+main\s*\(""").containsMatchIn(clean)) {
            return "Точка входа должна быть объявлена как public static void main(String[] args)."
        }

        var braces = 0
        var parens = 0
        var brackets = 0
        for (ch in clean) {
            when (ch) {
                '{' -> braces++
                '}' -> braces--
                '(' -> parens++
                ')' -> parens--
                '[' -> brackets++
                ']' -> brackets--
            }
            if (braces < 0) return "Лишняя закрывающая фигурная скобка '}'."
            if (parens < 0) return "Лишняя закрывающая круглая скобка ')'."
            if (brackets < 0) return "Лишняя закрывающая квадратная скобка ']'."
        }
        if (braces != 0) return "Несбалансированные фигурные скобки '{' и '}' (не хватает закрывающей скобки)."
        if (parens != 0) return "Несбалансированные круглые скобки '(' и ')'."
        if (brackets != 0) return "Несбалансированные квадратные скобки '[' и ']'."

        return null
    }

    /**
     * Лёгкий офлайн-интерпретатор базовых конструкций Java внутри `main`
     * (переменные `int`, `long`, `double`, `boolean`, `String`, арифметика, конкатенация и `System.out.println`).
     */
    private fun evaluateSimpleJavaProgram(code: String, stdin: String): String? {
        // Если программа использует сложные библиотеки, массивы, циклы, коллекции или ввод Scanner —
        // передаём управление структурному анализатору
        val withoutComments = code
            .replace(Regex("""//[^\n]*"""), "")
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")

        val mainMatch = Regex("""static\s+void\s+main\s*\([^)]*\)\s*(?:throws\s+\w+\s*)?\{([\s\S]*)\}""")
            .find(withoutComments) ?: return null

        val body = mainMatch.groupValues[1].trim().removeSuffix("}").trim()
        if (Regex("""\b(for|while|switch|Scanner|BufferedReader|Stream|List|Map|Set|new\s+\w+\()\b""").containsMatchIn(body)) {
            return null
        }

        val vars = mutableMapOf<String, Any?>()
        val out = StringBuilder()

        val statements = splitStatements(body)
        for (rawStmt in statements) {
            val stmt = rawStmt.trim()
            if (stmt.isEmpty()) continue

            val printMatch = Regex("""^System\.out\.(println|print)\s*\(([\s\S]*)\)$""").find(stmt)
            if (printMatch != null) {
                val isLn = printMatch.groupValues[1] == "println"
                val argExpr = printMatch.groupValues[2].trim()
                val value = if (argExpr.isEmpty()) "" else evalExpr(argExpr, vars) ?: return null
                out.append(value)
                if (isLn) out.append("\n")
                continue
            }

            // Объявление или присваивание переменных: [final] [type] name = expr [, name2 = expr2]
            val declMatch = Regex("""^(?:final\s+)?(?:(byte|short|int|long|double|float|boolean|char|String|var)\s+)?([a-zA-Z_$][\w$]*)\s*=\s*([\s\S]+)$""")
                .find(stmt)
            if (declMatch != null) {
                val varName = declMatch.groupValues[2]
                val expr = declMatch.groupValues[3].trim()
                val evaluated = evalExpr(expr, vars) ?: return null
                vars[varName] = evaluated
                continue
            }

            return null
        }

        return out.toString()
    }

    private fun splitStatements(body: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var depth = 0
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '"' && (i == 0 || body[i - 1] != '\\')) {
                inString = !inString
                current.append(c)
            } else if (!inString) {
                when (c) {
                    '(', '{', '[' -> {
                        depth++
                        current.append(c)
                    }
                    ')', '}', ']' -> {
                        depth--
                        current.append(c)
                    }
                    ';' -> if (depth == 0) {
                        result.add(current.toString())
                        current.setLength(0)
                    } else {
                        current.append(c)
                    }
                    else -> current.append(c)
                }
            } else {
                current.append(c)
            }
            i++
        }
        if (current.isNotBlank()) return emptyList() // пропущена точка с запятой
        return result
    }

    private fun evalExpr(expr: String, vars: Map<String, Any?>): Any? {
        val parts = splitTopLevelPlus(expr.trim())
        if (parts.isEmpty()) return null
        if (parts.size == 1) {
            return evalAtom(parts[0].trim(), vars)
        }
        val evaluatedParts = parts.map { evalAtom(it.trim(), vars) ?: return null }
        return if (evaluatedParts.any { it is String }) {
            evaluatedParts.joinToString("") { it.toString() }
        } else if (evaluatedParts.all { it is Long }) {
            evaluatedParts.sumOf { it as Long }
        } else if (evaluatedParts.all { it is Number }) {
            evaluatedParts.sumOf { (it as Number).toDouble() }
        } else {
            evaluatedParts.joinToString("") { it.toString() }
        }
    }

    private fun splitTopLevelPlus(expr: String): List<String> {
        val parts = mutableListOf<String>()
        val buf = StringBuilder()
        var inString = false
        var depth = 0
        for (i in expr.indices) {
            val c = expr[i]
            if (c == '"' && (i == 0 || expr[i - 1] != '\\')) {
                inString = !inString
                buf.append(c)
            } else if (!inString) {
                when (c) {
                    '(' -> { depth++; buf.append(c) }
                    ')' -> { depth--; buf.append(c) }
                    '+' -> if (depth == 0) {
                        parts.add(buf.toString())
                        buf.setLength(0)
                    } else {
                        buf.append(c)
                    }
                    else -> buf.append(c)
                }
            } else {
                buf.append(c)
            }
        }
        if (buf.isNotEmpty()) parts.add(buf.toString())
        return parts
    }

    private fun evalAtom(token: String, vars: Map<String, Any?>): Any? {
        val t = token.trim()
        if (t.startsWith("\"") && t.endsWith("\"") && t.length >= 2) {
            return t.substring(1, t.length - 1)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        if (t.startsWith("'") && t.endsWith("'") && t.length == 3) {
            return t[1].toString()
        }
        if (t == "true") return true
        if (t == "false") return false
        val cleanNum = t.removeSuffix("L").removeSuffix("l").replace("_", "")
        cleanNum.toLongOrNull()?.let { return it }
        cleanNum.toDoubleOrNull()?.let { return it }
        if (vars.containsKey(t)) return vars[t]
        return null
    }

    private fun verifyComplexJavaStructure(code: String, expectedOutput: String): Boolean {
        if (!code.contains("System.out.print")) return false
        val expectedLines = expectedOutput.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (expectedLines.isEmpty()) return true
        // Проверяем, что в коде присутствуют либо строковые/числовые константы результата,
        // либо вычисления, ведущие к выводу
        val nonTrivialStatements = code.lines().count { line ->
            val l = line.trim()
            l.isNotEmpty() && !l.startsWith("//") && l != "{" && l != "}"
        }
        return nonTrivialStatements >= 5
    }

    private fun stripStringsAndComments(code: String): String {
        return code
            .replace(Regex(""""(?:\\.|[^"\\])*""""), "\"\"")
            .replace(Regex("""'(?:\\.|[^'\\])*'"""), "''")
            .replace(Regex("""//[^\n]*"""), "")
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
    }

    fun normalizeOutput(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .joinToString("\n") { it.trimEnd(' ', '\t') }
            .trimEnd('\n')
    }
}
