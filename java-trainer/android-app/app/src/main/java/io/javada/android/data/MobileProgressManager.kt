package io.javada.android.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Полная модель `.javada/progress.json` — на 100% совместимая с веб-тренажёром (`shared_progress.py`)
 * и плагином IntelliJ IDEA (`ProgressManager.kt`).
 */
data class ProgressProfile(
    var version: Int = 1,
    var revision: Int = 0,
    var state: ProgressState = ProgressState(),
    var drafts: MutableMap<String, CodeDraft> = mutableMapOf(),
    var exams: MutableMap<String, ExamRecord> = mutableMapOf()
)

data class ProgressState(
    var version: Int = 1,
    var xp: Int = 0,
    var legacyXp: Int = 0,
    var xpLedger: MutableMap<String, Int> = mutableMapOf(),
    var streak: Int = 0,
    var lastActiveDate: String? = null,
    var milestones: MutableList<Int> = mutableListOf(),
    var completedLessons: MutableList<String> = mutableListOf(),
    var completedPractices: MutableList<Int> = mutableListOf(),
    var completedLabs: MutableList<String> = mutableListOf(),
    var legacyAwardedTasks: MutableList<String> = mutableListOf(),
    var careerChecklist: MutableList<String> = mutableListOf(),
    var bossScores: MutableMap<String, ScoreEntry> = mutableMapOf(),
    var examScores: MutableMap<String, ScoreEntry> = mutableMapOf(),
    var attempts: MutableMap<String, AttemptEntry> = mutableMapOf(),
    var awardedTasks: MutableList<String> = mutableListOf(),
    var reviewRewards: MutableMap<String, String> = mutableMapOf(),
    var achievements: MutableList<String> = mutableListOf(),
    var theme: String = "dark",
    var remindersEnabled: Boolean = false,
    var reminderTime: String = "19:00",
    var lastReminderDay: String? = null
)

data class ScoreEntry(
    var best: Int = 0,
    var passed: Boolean = false
)

data class AttemptEntry(
    var correct: Boolean = false,
    var type: String = "choice",
    var lastAttempt: String = "",
    var mistakes: Int = 0,
    var needsReview: Boolean = false
)

data class CodeDraft(
    var code: String = "",
    var updatedAt: Long = 0L
)

data class ExamTaskDetail(
    var id: String = "",
    var correct: Boolean = false,
    var explanation: String = "",
    var error: String = ""
)

data class ExamResultSummary(
    var correct: Int = 0,
    var total: Int = 0,
    var percent: Double = 0.0,
    var passed: Boolean = false,
    var xp: Int = 0,
    var details: MutableList<ExamTaskDetail> = mutableListOf()
)

data class ExamRecord(
    var attemptId: String = "",
    var active: Boolean = false,
    var startedAt: Long = 0L,
    var retryAt: Long = 0L,
    var result: ExamResultSummary? = null
)

/**
 * Автономный менеджер прогресса для Android-приложения «JavaДа Mobile».
 *
 * Хранит локальный профиль в `.javada/progress.json` с атомарной записью (`ATOMIC_MOVE` + `fsync`)
 * и поддерживает двусторонний импорт/экспорт файла `progress.json` для синхронизации с ПК
 * (браузером и плагином IntelliJ IDEA) без дублирования наград.
 */
class MobileProgressManager(storageDir: File) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create()
    private val lock = ReentrantReadWriteLock()
    private val profileDir = File(storageDir, ".javada").apply { mkdirs() }
    val progressFile = File(profileDir, "progress.json")

    @Volatile
    private var cachedProfile: ProgressProfile = readDiskSafe()

    fun getProfile(): ProgressProfile = lock.read {
        cachedProfile.deepCopy()
    }

    /**
     * Экспортирует актуальный профиль в JSON-строку для сохранения в файл `progress.json`
     * и переноса на ПК (в браузер или плагин IntelliJ IDEA).
     */
    fun exportProgressJson(): String = lock.read {
        gson.toJson(cachedProfile) + "\n"
    }

    /**
     * Импортирует внешний файл `progress.json` (например, с ПК) и монотонно объединяет
     * все заработанные XP, серии дней, пройденные уроки, экзамены и черновики.
     */
    fun importAndMergeProgressJson(rawJson: String): ProgressProfile = lock.write {
        val incoming = gson.fromJson(rawJson, ProgressProfile::class.java)
            ?: throw IllegalArgumentException("Некорректный формат файла progress.json")
        val merged = mergeProfiles(cachedProfile, incoming)
        merged.revision += 1
        recalculateTotalXp(merged.state)
        writeDiskAtomically(merged)
        cachedProfile = merged
        merged.deepCopy()
    }

    fun saveDraft(taskId: String, code: String): ProgressProfile = mutateAndPersist { profile ->
        if (code.toByteArray(StandardCharsets.UTF_8).size > 16_384) return@mutateAndPersist
        val nowMs = System.currentTimeMillis()
        val prev = profile.drafts[taskId]?.updatedAt ?: 0L
        profile.drafts[taskId] = CodeDraft(code = code, updatedAt = max(nowMs, prev + 1L))
    }

    fun getDraft(taskId: String): String? = lock.read {
        cachedProfile.drafts[taskId]?.code?.takeIf { it.isNotBlank() }
    }

    fun toggleTheme(): ProgressProfile = mutateAndPersist { profile ->
        profile.state.theme = if (profile.state.theme == "dark") "light" else "dark"
    }

    /**
     * Записывает попытку решения задания, начисляет XP по единым правилам курса
     * и автоматически отмечает завершение урока, стоп-урока или практики модуля.
     */
    fun recordTaskAttempt(
        task: CourseTask,
        module: CourseModule?,
        correct: Boolean
    ): ProgressProfile = mutateAndPersist { profile ->
        val state = profile.state
        val now = OffsetDateTime.now()
        val today = now.toLocalDate().toString()
        val isLab = task.id.startsWith("lab-")
        val isExam = task.id.startsWith("m-exam-")

        val prevAttempt = state.attempts[task.id]
        state.attempts[task.id] = AttemptEntry(
            correct = correct || (prevAttempt?.correct == true),
            type = task.type,
            lastAttempt = now.toString(),
            mistakes = (prevAttempt?.mistakes ?: 0) + (if (correct) 0 else 1),
            needsReview = !correct
        )

        if (!correct || isExam) return@mutateAndPersist

        if (!isLab) {
            updateStreak(state, now.toLocalDate())
        }

        if (!state.awardedTasks.contains(task.id)) {
            state.awardedTasks.add(task.id)
            val baseXp = when {
                isLab -> 0
                task.difficulty == "hardcore" -> 90
                task.difficulty == "hard" -> 35
                task.difficulty == "medium" -> 20
                task.difficulty == "easy" -> 10
                task.type == "code" -> 50
                task.type == "parsons" -> 25
                task.type == "matching" -> 20
                task.type == "fill" -> 15
                task.type == "choice" -> 10
                task.type == "copy" -> 8
                else -> 10
            }
            val amountWithStreak = if (state.streak >= 2) ((baseXp * 11 + 5) / 10) else baseXp
            if (amountWithStreak > 0) {
                state.xpLedger["task:${task.id}"] = max(state.xpLedger["task:${task.id}"] ?: 0, amountWithStreak)
            }
        }

        val milestoneBonus = when (state.streak) {
            7 -> 50
            30 -> 200
            else -> null
        }
        if (!isLab && milestoneBonus != null && !state.milestones.contains(state.streak)) {
            state.milestones.add(state.streak)
            state.xpLedger["milestone:${state.streak}:$today"] = milestoneBonus
        }

        if (task.difficulty == "hardcore" && !task.lessonId.isNullOrBlank()) {
            val badge = "mastery:${task.lessonId}"
            if (!state.achievements.contains(badge)) {
                state.achievements.add(badge)
            }
        }

        if (isLab && !state.completedLabs.contains(task.id)) {
            state.completedLabs.add(task.id)
        }

        if (module != null) {
            val lesson = module.lessons.find { it.id == task.lessonId }
            if (lesson != null) {
                val requiredTasks = lesson.tasks.filter { !it.optional && it.difficulty != "hardcore" }
                val allSolved = requiredTasks.all { req ->
                    state.awardedTasks.contains(req.id) || state.attempts[req.id]?.correct == true
                }
                if (allSolved && !state.completedLessons.contains(lesson.id)) {
                    state.completedLessons.add(lesson.id)
                }
            }

            if (module.bossTasks.any { it.id == task.id }) {
                val solvedCount = module.bossTasks.count { b ->
                    state.awardedTasks.contains(b.id) || state.attempts[b.id]?.correct == true
                }
                val pct = if (module.bossTasks.isNotEmpty()) (solvedCount * 100) / module.bossTasks.size else 100
                val prevBoss = state.bossScores[module.id.toString()] ?: ScoreEntry()
                state.bossScores[module.id.toString()] = ScoreEntry(
                    best = max(prevBoss.best, pct),
                    passed = prevBoss.passed || (solvedCount * 100 >= 80 * module.bossTasks.size)
                )
            }

            if (module.practiceTasks.any { it.id == task.id }) {
                val allPracticeDone = module.practiceTasks.all { p ->
                    state.awardedTasks.contains(p.id) || state.attempts[p.id]?.correct == true
                }
                if (allPracticeDone && !state.completedPractices.contains(module.id)) {
                    state.completedPractices.add(module.id)
                }
            }
        }

        recalculateTotalXp(state)
    }

    /**
     * Оценивает прохождение мини-экзамена (5–8 заданий без подсказок).
     * При результате >= 80% начисляет бонусные XP (310–520 XP) и разблокирует следующий модуль.
     */
    fun submitMiniExam(
        module: CourseModule,
        exam: CourseExam,
        details: List<ExamTaskDetail>
    ): ExamResultSummary {
        val total = exam.tasks.size.coerceAtLeast(1)
        val correct = details.count { it.correct }
        val passed = correct * 100 >= exam.passingPercent * total
        val percent = ((correct * 1000.0) / total).roundToInt() / 10.0
        val bonusXp = if (passed) exam.xpBonus else 0

        val summary = ExamResultSummary(
            correct = correct,
            total = total,
            percent = percent,
            passed = passed,
            xp = bonusXp,
            details = details.toMutableList()
        )

        mutateAndPersist { profile ->
            val key = module.id.toString()
            val nowSec = Instant.now().epochSecond
            profile.exams[key] = ExamRecord(
                attemptId = UUID.randomUUID().toString().replace("-", ""),
                active = false,
                startedAt = nowSec,
                retryAt = if (passed) 0L else nowSec + exam.cooldownSeconds,
                result = summary
            )

            val state = profile.state
            val prevScore = state.examScores[key] ?: ScoreEntry()
            state.examScores[key] = ScoreEntry(
                best = max(prevScore.best, percent.toInt()),
                passed = prevScore.passed || passed
            )

            if (passed) {
                if (!state.completedPractices.contains(module.id)) {
                    state.completedPractices.add(module.id)
                }
                if (module.bossTasks.isNotEmpty()) {
                    val prevBoss = state.bossScores[key] ?: ScoreEntry()
                    state.bossScores[key] = ScoreEntry(best = max(prevBoss.best, 80), passed = true)
                }
                state.xpLedger["exam:$key"] = bonusXp
                val badge = "exam:m%02d".format(module.id)
                if (!state.achievements.contains(badge)) {
                    state.achievements.add(badge)
                }
                recalculateTotalXp(state)
            }
        }
        return summary
    }

    fun getModuleStatus(module: CourseModule, allModules: List<CourseModule>, state: ProgressState): ItemStatus {
        var unlocked = true
        for (mod in allModules.sortedBy { it.id }) {
            val complete = isModuleCompleted(mod, state)
            if (mod.id == module.id) {
                return when {
                    !unlocked -> ItemStatus.LOCKED
                    complete -> ItemStatus.COMPLETED
                    else -> ItemStatus.AVAILABLE
                }
            }
            unlocked = unlocked && complete
        }
        return ItemStatus.AVAILABLE
    }

    fun isModuleCompleted(module: CourseModule, state: ProgressState): Boolean {
        val practiceDone = state.completedPractices.contains(module.id)
        val bossDone = module.bossTasks.isEmpty() || state.bossScores[module.id.toString()]?.passed == true
        val examDone = module.exam == null || state.examScores[module.id.toString()]?.passed == true
        return practiceDone && bossDone && examDone
    }

    fun mergeProfiles(leftRaw: ProgressProfile, rightRaw: ProgressProfile): ProgressProfile {
        val lState = leftRaw.state
        val rState = rightRaw.state

        val mergedState = ProgressState(
            version = 1,
            legacyXp = max(lState.legacyXp, rState.legacyXp),
            completedLessons = (lState.completedLessons + rState.completedLessons).distinct().toMutableList(),
            completedPractices = (lState.completedPractices + rState.completedPractices).distinct().toMutableList(),
            completedLabs = (lState.completedLabs + rState.completedLabs).distinct().toMutableList(),
            awardedTasks = (lState.awardedTasks + rState.awardedTasks).distinct().toMutableList(),
            legacyAwardedTasks = (lState.legacyAwardedTasks + rState.legacyAwardedTasks).distinct().toMutableList(),
            milestones = (lState.milestones + rState.milestones).distinct().toMutableList(),
            achievements = (lState.achievements + rState.achievements).distinct().toMutableList(),
            careerChecklist = (lState.careerChecklist + rState.careerChecklist).distinct().toMutableList(),
            theme = rState.theme.ifBlank { lState.theme }
        )

        val ledger = mutableMapOf<String, Int>()
        ledger.putAll(lState.xpLedger)
        for ((k, v) in rState.xpLedger) {
            ledger[k] = max(ledger[k] ?: 0, v)
        }
        mergedState.xpLedger = ledger

        val newestDay = listOfNotNull(lState.lastActiveDate, rState.lastActiveDate).maxOrNull()
        mergedState.lastActiveDate = newestDay
        mergedState.streak = max(lState.streak, rState.streak)

        for (k in (lState.bossScores.keys + rState.bossScores.keys)) {
            val a = lState.bossScores[k]
            val b = rState.bossScores[k]
            mergedState.bossScores[k] = ScoreEntry(
                best = max(a?.best ?: 0, b?.best ?: 0),
                passed = (a?.passed == true) || (b?.passed == true)
            )
        }

        for (k in (lState.examScores.keys + rState.examScores.keys)) {
            val a = lState.examScores[k]
            val b = rState.examScores[k]
            mergedState.examScores[k] = ScoreEntry(
                best = max(a?.best ?: 0, b?.best ?: 0),
                passed = (a?.passed == true) || (b?.passed == true)
            )
        }

        for (k in (lState.attempts.keys + rState.attempts.keys)) {
            val a = lState.attempts[k]
            val b = rState.attempts[k]
            if (a != null && b != null) {
                val newest = if (b.lastAttempt >= a.lastAttempt) b else a
                mergedState.attempts[k] = AttemptEntry(
                    correct = a.correct || b.correct,
                    type = newest.type,
                    lastAttempt = newest.lastAttempt,
                    mistakes = max(a.mistakes, b.mistakes),
                    needsReview = newest.needsReview
                )
            } else {
                mergedState.attempts[k] = (b ?: a)!!.copy()
            }
        }

        val mergedDrafts = mutableMapOf<String, CodeDraft>()
        for (k in (leftRaw.drafts.keys + rightRaw.drafts.keys)) {
            val dl = leftRaw.drafts[k]
            val dr = rightRaw.drafts[k]
            mergedDrafts[k] = when {
                dl == null -> dr!!.copy()
                dr == null -> dl.copy()
                dr.updatedAt >= dl.updatedAt -> dr.copy()
                else -> dl.copy()
            }
        }

        val mergedExams = mutableMapOf<String, ExamRecord>()
        mergedExams.putAll(leftRaw.exams)
        for ((k, rExam) in rightRaw.exams) {
            val lExam = mergedExams[k]
            if (lExam == null || rExam.startedAt >= lExam.startedAt || rExam.result?.passed == true) {
                mergedExams[k] = rExam
            }
        }

        recalculateTotalXp(mergedState)

        return ProgressProfile(
            version = 1,
            revision = max(leftRaw.revision, rightRaw.revision),
            state = mergedState,
            drafts = mergedDrafts,
            exams = mergedExams
        )
    }

    private fun mutateAndPersist(mutation: (ProgressProfile) -> Unit): ProgressProfile = lock.write {
        val current = cachedProfile.deepCopy()
        mutation(current)
        current.revision += 1
        recalculateTotalXp(current.state)
        writeDiskAtomically(current)
        cachedProfile = current
        current.deepCopy()
    }

    private fun writeDiskAtomically(profile: ProgressProfile) {
        profileDir.mkdirs()
        val tempFile = File.createTempFile(".progress-", ".tmp", profileDir)
        try {
            val bytes = (gson.toJson(profile) + "\n").toByteArray(StandardCharsets.UTF_8)
            FileOutputStream(tempFile).use { fos ->
                fos.write(bytes)
                fos.flush()
                fos.fd.sync()
            }
            try {
                Files.move(
                    tempFile.toPath(),
                    progressFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempFile.toPath(), progressFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun readDiskSafe(): ProgressProfile {
        if (!progressFile.isFile) return ProgressProfile()
        return try {
            val text = progressFile.readText(StandardCharsets.UTF_8)
            val loaded = gson.fromJson(text, ProgressProfile::class.java) ?: ProgressProfile()
            recalculateTotalXp(loaded.state)
            loaded
        } catch (_: Exception) {
            ProgressProfile()
        }
    }

    private fun updateStreak(state: ProgressState, today: LocalDate) {
        val todayStr = today.toString()
        if (state.lastActiveDate != todayStr) {
            val prior = state.lastActiveDate?.let {
                try { LocalDate.parse(it) } catch (_: Exception) { null }
            }
            val consecutive = prior != null && ChronoUnit.DAYS.between(prior, today) == 1L
            state.streak = if (consecutive) state.streak + 1 else 1
            if (!consecutive) state.milestones.clear()
            state.lastActiveDate = todayStr
        }
    }

    private fun recalculateTotalXp(state: ProgressState) {
        val tracked = state.xpLedger.entries.sumOf { (key, amount) ->
            if (key.startsWith("task:") && state.legacyAwardedTasks.contains(key.removePrefix("task:"))) 0 else amount
        }
        if (state.xp > state.legacyXp + tracked) {
            state.legacyXp = state.xp - tracked
        }
        state.xp = state.legacyXp + tracked
    }

    private fun ProgressProfile.deepCopy(): ProgressProfile =
        gson.fromJson(gson.toJson(this), ProgressProfile::class.java) ?: ProgressProfile()
}
