package io.javada.idea

/**
 * Многоязычная локализация плагина IntelliJ IDEA «JavaДа» (RU, EN, ES, DE, ZH).
 */
enum class PluginLang(val code: String, val displayLabel: String) {
    RU("ru", "RU · Русский"),
    EN("en", "EN · English"),
    ES("es", "ES · Español"),
    DE("de", "DE · Deutsch"),
    ZH("zh", "ZH · 中文");

    companion object {
        fun fromCode(code: String?): PluginLang =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: RU
    }
}

object JavadaI18n {
    @Volatile
    var currentLang: PluginLang = PluginLang.RU

    fun tr(
        ru: String,
        en: String,
        es: String = en,
        de: String = en,
        zh: String = en
    ): String = when (currentLang) {
        PluginLang.RU -> ru
        PluginLang.EN -> en
        PluginLang.ES -> es
        PluginLang.DE -> de
        PluginLang.ZH -> zh
    }

    fun taskTitle(type: String, id: String, difficulty: String?, idx: Int): String = when (type) {
        "copy" -> tr(
            "Образец кода ($id)", "Code Sample ($id)",
            "Ejemplo de código ($id)", "Code-Vorlage ($id)", "代码示例 ($id)"
        )
        "code" -> if (difficulty == "hardcore") {
            tr(
                "Хард-задача ($id)", "Hardcore Task ($id)",
                "Tarea Hardcore ($id)", "Hardcore-Aufgabe ($id)", "高难挑战 ($id)"
            )
        } else {
            tr("Код ($id)", "Code ($id)", "Código ($id)", "Code ($id)", "编程题 ($id)")
        }
        "junit" -> tr(
            "JUnit-задача ($id)", "JUnit Task ($id)",
            "Tarea JUnit ($id)", "JUnit-Aufgabe ($id)", "JUnit 测试题 ($id)"
        )
        "choice" -> tr(
            "Тест #${idx + 1} ($id)", "Quiz #${idx + 1} ($id)",
            "Test #${idx + 1} ($id)", "Quiz #${idx + 1} ($id)", "选择题 #${idx + 1} ($id)"
        )
        "fill" -> tr(
            "Пропуски #${idx + 1} ($id)", "Fill-in #${idx + 1} ($id)",
            "Espacios #${idx + 1} ($id)", "Lückentext #${idx + 1} ($id)", "填空题 #${idx + 1} ($id)"
        )
        "matching" -> tr(
            "Пары #${idx + 1} ($id)", "Matching #${idx + 1} ($id)",
            "Parejas #${idx + 1} ($id)", "Zuordnung #${idx + 1} ($id)", "配对题 #${idx + 1} ($id)"
        )
        "parsons" -> tr(
            "Конструктор кода #${idx + 1} ($id)", "Parsons Puzzle #${idx + 1} ($id)",
            "Constructor de código #${idx + 1} ($id)", "Code-Baukasten #${idx + 1} ($id)", "代码拼图 #${idx + 1} ($id)"
        )
        else -> tr("Задание $id", "Task $id", "Tarea $id", "Aufgabe $id", "任务 $id")
    }
}
