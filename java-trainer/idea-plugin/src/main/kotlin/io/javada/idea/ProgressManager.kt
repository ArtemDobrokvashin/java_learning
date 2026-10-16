package io.javada.idea

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.StandardWatchEventKinds
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Структура файла `.javada/progress.json` (полная совместимость с `shared_progress.py` и веб-приложением).
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
    var theme: String = "light",
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
    var type: String = "code",
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
    var error: String = "",
    var output: String = ""
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
 * Менеджер прогресса курса JavaДа.
 *
 * Отвечает за:
 * 1. Чтение и атомарную запись файла `.javada/progress.json` через временный файл и `ATOMIC_MOVE`.
 * 2. Отслеживание изменений файла внешним процессом (браузером / `server.py`) в фоновом потоке
 *    через `WatchService` и монотонное слияние (`mergeProfiles`) без потери XP и черновиков.
 * 3. Начисление XP (с учётом серии дней, вех 7/30 дней и идемпотентного `xpLedger`),
 *    отметку задач, уроков, практик и лабораторных работ.
 * 4. Проведение и оценку мини-экзаменов (порог >= 80%, начисление бонусных XP и разблокировка следующего модуля).
 */
class ProgressManager(private val trainerRoot: Path) : Disposable {
    private val log = Logger.getInstance(ProgressManager::class.java)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create()
    private val lock = ReentrantReadWriteLock()
    private val listeners = CopyOnWriteArrayList<(ProgressProfile) -> Unit>()

    private val profileDir: Path = resolveProfileDir(trainerRoot)
    val progressFile: Path = profileDir.resolve("progress.json")

    @Volatile
    private var lastKnownMtimeMs: Long = -1L

    @Volatile
    private var cachedProfile: ProgressProfile = ProgressProfile()

    private val watcherExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "javada-progress-watcher").apply { isDaemon = true }
    }

    init {
        reloadFromDisk()
        startExternalChangeWatcher()
    }

    private fun resolveProfileDir(root: Path): Path {
        val envDir = System.getenv("JAVADA_PROFILE_DIR")
        return if (!envDir.isNullOrBlank()) Path.of(envDir) else root.resolve(".javada")
    }

    /**
     * Подписка UI-компонентов на обновления прогресса (как из IDE, так и из браузера).
     */
    fun addListener(listener: (ProgressProfile) -> Unit) {
        listeners.add(listener)
    }

    fun getProfile(): ProgressProfile = lock.read {
        checkExternalModification()
        cachedProfile.deepCopy()
    }

    /**
     * Перечитывает `.javada/progress.json` с диска и объединяет с текущим состоянием в памяти.
     */
    fun reloadFromDisk(): ProgressProfile = lock.write {
        val diskProfile = readDiskFileSafe()
        cachedProfile = if (cachedProfile.revision == 0 && cachedProfile.state.xp == 0) {
            normalizeProfile(diskProfile)
        } else {
            mergeProfiles(cachedProfile, diskProfile)
        }
        lastKnownMtimeMs = currentFileMtimeMs()
        val snapshot = cachedProfile.deepCopy()
        notifyListeners(snapshot)
        snapshot
    }

    /**
     * Проверяет, не изменился ли файл `.javada/progress.json` внешним процессом (например, браузером).
     * Если изменился — выполняет неразрушающее слияние состояний.
     */
    private fun checkExternalModification() {
        val diskMtime = currentFileMtimeMs()
        if (diskMtime > 0 && diskMtime != lastKnownMtimeMs) {
            lock.write {
                val recheck = currentFileMtimeMs()
                if (recheck > 0 && recheck != lastKnownMtimeMs) {
                    val diskProfile = readDiskFileSafe()
                    cachedProfile = mergeProfiles(cachedProfile, diskProfile)
                    lastKnownMtimeMs = recheck
                }
            }
        }
    }

    /**
     * Сохраняет черновик кода задачи при редактировании в редакторе IDEA.
     * Пустая строка очищает черновик (например, после успешного решения задачи).
     */
    fun saveDraft(taskId: String, code: String): ProgressProfile = mutateAndPersist { profile ->
        if (code.toByteArray(StandardCharsets.UTF_8).size > 16_384) return@mutateAndPersist
        val nowMs = System.currentTimeMillis()
        val prevUpdated = profile.drafts[taskId]?.updatedAt ?: 0L
        val nextUpdated = max(nowMs, prevUpdated + 1L)
        profile.drafts[taskId] = CodeDraft(code = code, updatedAt = nextUpdated)
    }

    /**
     * Возвращает сохранённый черновик для задачи или `null`, если черновик отсутствует или пуст.
     */
    fun getDraft(taskId: String): String? = lock.read {
        checkExternalModification()
        cachedProfile.drafts[taskId]?.code?.takeIf { it.isNotBlank() }
    }

    /**
     * Начисляет XP и отмечает выполнение задачи, а также автоматически завершает урок/практику/босс-раунд,
     * если все обязательные задания соответствующего раздела решены.
     */
    fun recordTaskResult(
        task: CourseTask,
        module: CourseModule?,
        correct: Boolean
    ): ProgressProfile = mutateAndPersist { profile ->
        val state = profile.state
        val now = OffsetDateTime.now()
        val today = now.toLocalDate().toString()
        val isLab = task.id.startsWith("lab-")
        val isExamTask = task.id.startsWith("m-exam-")

        // Обновляем статистику попыток
        val prevAttempt = state.attempts[task.id]
        state.attempts[task.id] = AttemptEntry(
            correct = correct || (prevAttempt?.correct == true),
            type = task.type,
            lastAttempt = now.toString(),
            mistakes = (prevAttempt?.mistakes ?: 0) + (if (correct) 0 else 1),
            needsReview = !correct
        )

        if (!correct) return@mutateAndPersist

        // Экзаменационные задачи оцениваются пакетно в submitMiniExam
        if (isExamTask) return@mutateAndPersist

        // Обновляем серию дней (streak), кроме лабораторных работ
        if (!isLab) {
            updateStreak(state, now.toLocalDate())
        }

        // Начисляем XP за задачу ровно один раз через идемпотентный xpLedger
        if (!state.awardedTasks.contains(task.id)) {
            state.awardedTasks.add(task.id)
            val baseXp = when {
                isLab -> 0
                task.difficulty == "hardcore" -> 90
                task.difficulty == "hard" -> 35
                task.difficulty == "medium" -> 20
                task.difficulty == "easy" -> 10
                task.type == "code" || task.type == "junit" -> 50
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

        // Проверяем вехи серии (7 и 30 дней)
        val milestoneBonus = when (state.streak) {
            7 -> 50
            30 -> 200
            else -> null
        }
        if (!isLab && milestoneBonus != null && !state.milestones.contains(state.streak)) {
            state.milestones.add(state.streak)
            state.xpLedger["milestone:${state.streak}:$today"] = milestoneBonus
        }

        // Выдаём достижение за хард-задачу урока
        if (task.difficulty == "hardcore" && !task.lessonId.isNullOrBlank()) {
            val badge = "mastery:${task.lessonId}"
            if (!state.achievements.contains(badge)) {
                state.achievements.add(badge)
            }
        }

        // Отмечаем лабораторную работу
        if (isLab && !state.completedLabs.contains(task.id)) {
            state.completedLabs.add(task.id)
        }

        // Проверяем автоматическое завершение урока, босс-раунда или финальной практики модуля
        if (module != null) {
            val lesson = module.lessons.find { it.id == task.lessonId }
            if (lesson != null) {
                val requiredTasks = lesson.tasks.filter { !it.optional && it.difficulty != "hardcore" }
                val allRequiredSolved = requiredTasks.all { req ->
                    state.awardedTasks.contains(req.id) || state.attempts[req.id]?.correct == true
                }
                if (allRequiredSolved && !state.completedLessons.contains(lesson.id)) {
                    state.completedLessons.add(lesson.id)
                }
            }

            if (module.bossTasks.any { it.id == task.id }) {
                val solvedBoss = module.bossTasks.count { b ->
                    state.awardedTasks.contains(b.id) || state.attempts[b.id]?.correct == true
                }
                val percent = if (module.bossTasks.isNotEmpty()) (solvedBoss * 100) / module.bossTasks.size else 100
                val prevBoss = state.bossScores[module.id.toString()] ?: ScoreEntry()
                state.bossScores[module.id.toString()] = ScoreEntry(
                    best = max(prevBoss.best, percent),
                    passed = prevBoss.passed || (solvedBoss * 100 >= 80 * module.bossTasks.size)
                )
            }

            if (module.practiceTasks.any { it.id == task.id }) {
                val allPracticeSolved = module.practiceTasks.all { p ->
                    state.awardedTasks.contains(p.id) || state.attempts[p.id]?.correct == true
                }
                if (allPracticeSolved && !state.completedPractices.contains(module.id)) {
                    state.completedPractices.add(module.id)
                }
            }
        }

        // Очищаем черновик успешно решённой задачи
        if (profile.drafts.containsKey(task.id)) {
            val nowMs = System.currentTimeMillis()
            val prevUpdated = profile.drafts[task.id]?.updatedAt ?: 0L
            profile.drafts[task.id] = CodeDraft(code = "", updatedAt = max(nowMs, prevUpdated + 1L))
        }

        recalculateTotalXp(state)
    }

    /**
     * Запускает или возвращает активную попытку мини-экзамена модуля.
     */
    fun startMiniExam(module: CourseModule): ExamRecord {
        var recordResult = ExamRecord()
        mutateAndPersist { profile ->
            val key = module.id.toString()
            val nowSec = Instant.now().epochSecond
            val existing = profile.exams[key]
            if (existing != null && (existing.active || existing.result?.passed == true || existing.retryAt > nowSec)) {
                recordResult = existing
                return@mutateAndPersist
            }
            val created = ExamRecord(
                attemptId = java.util.UUID.randomUUID().toString().replace("-", ""),
                active = true,
                startedAt = nowSec,
                retryAt = 0L,
                result = null
            )
            profile.exams[key] = created
            recordResult = created
        }
        return recordResult
    }

    /**
     * Завершает мини-экзамен (5–8 задач без подсказок), подсчитывает процент правильных ответов.
     * Если >= 80% (без округления вверх: `correct * 100 >= 80 * total`) — начисляет бонусные XP
     * и разблокирует следующий модуль в `progress.json`.
     */
    fun submitMiniExam(
        module: CourseModule,
        exam: CourseExam,
        details: List<ExamTaskDetail>
    ): ExamResultSummary {
        val total = exam.tasks.size.coerceAtLeast(1)
        val correct = details.count { it.correct }
        // Точная целочисленная проверка >= 80% без округления вверх (как в exams.py)
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
            val currentRecord = profile.exams[key] ?: ExamRecord(
                attemptId = java.util.UUID.randomUUID().toString().replace("-", ""),
                startedAt = nowSec
            )
            currentRecord.active = false
            currentRecord.result = summary
            currentRecord.retryAt = if (passed) 0L else nowSec + exam.cooldownSeconds
            profile.exams[key] = currentRecord

            val state = profile.state
            val prevScore = state.examScores[key] ?: ScoreEntry()
            state.examScores[key] = ScoreEntry(
                best = max(prevScore.best, percent.toInt()),
                passed = prevScore.passed || passed
            )

            if (passed) {
                // Гарантируем, что практика модуля отмечена выполненной для разблокировки следующего модуля
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

    /**
     * Вычисляет статус модуля (`LOCKED`, `AVAILABLE`, `COMPLETED`) согласно правилам курса.
     */
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

    fun getLessonStatus(module: CourseModule, lesson: CourseLesson, moduleStatus: ItemStatus, state: ProgressState): ItemStatus {
        if (moduleStatus == ItemStatus.LOCKED) return ItemStatus.LOCKED
        if (state.completedLessons.contains(lesson.id)) return ItemStatus.COMPLETED
        val idx = module.lessons.indexOfFirst { it.id == lesson.id }
        if (idx <= 0) return ItemStatus.AVAILABLE
        val prevLesson = module.lessons[idx - 1]
        return if (state.completedLessons.contains(prevLesson.id)) ItemStatus.AVAILABLE else ItemStatus.LOCKED
    }

    fun getTaskStatus(task: CourseTask, parentStatus: ItemStatus, state: ProgressState): ItemStatus {
        if (state.awardedTasks.contains(task.id) ||
            state.completedLabs.contains(task.id) ||
            state.attempts[task.id]?.correct == true
        ) {
            return ItemStatus.COMPLETED
        }
        return if (parentStatus == ItemStatus.LOCKED) ItemStatus.LOCKED else ItemStatus.AVAILABLE
    }

    /**
     * Выполняет потокобезопасную мутацию профиля с предварительным слиянием изменений с диска
     * и атомарной записью в `.javada/progress.json`.
     */
    private fun mutateAndPersist(mutation: (ProgressProfile) -> Unit): ProgressProfile {
        val snapshot = lock.write {
            val diskProfile = readDiskFileSafe()
            val merged = mergeProfiles(cachedProfile, diskProfile)
            mutation(merged)
            merged.revision += 1
            recalculateTotalXp(merged.state)
            writeDiskFileAtomically(merged)
            cachedProfile = merged
            lastKnownMtimeMs = currentFileMtimeMs()
            merged.deepCopy()
        }
        notifyListeners(snapshot)
        return snapshot
    }

    /**
     * Атомарная запись в `.javada/progress.json`:
     * Записывает JSON во временный файл в той же директории, сбрасывает буферы ОС через `FileChannel.force(true)`
     * и выполняет атомарное переименование `ATOMIC_MOVE`.
     */
    private fun writeDiskFileAtomically(profile: ProgressProfile) {
        try {
            Files.createDirectories(profileDir)
            val tempFile = Files.createTempFile(profileDir, ".progress-", ".tmp")
            try {
                val jsonBytes = (gson.toJson(profile) + "\n").toByteArray(StandardCharsets.UTF_8)
                FileChannel.open(
                    tempFile,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
                ).use { channel ->
                    channel.write(java.nio.ByteBuffer.wrap(jsonBytes))
                    channel.force(true)
                }
                try {
                    Files.move(
                        tempFile,
                        progressFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(tempFile, progressFile, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(tempFile)
            }
        } catch (e: IOException) {
            log.warn("Не удалось атомарно сохранить .javada/progress.json", e)
        }
    }

    private fun readDiskFileSafe(): ProgressProfile {
        if (!Files.isRegularFile(progressFile)) return ProgressProfile()
        return try {
            val content = Files.readString(progressFile, StandardCharsets.UTF_8)
            val parsed = gson.fromJson(content, ProgressProfile::class.java) ?: ProgressProfile()
            normalizeProfile(parsed)
        } catch (e: Exception) {
            log.warn("Ошибка чтения файла прогресса $progressFile, используется профиль в памяти", e)
            cachedProfile.deepCopy()
        }
    }

    /**
     * Монотонное объединение состояния из памяти IDE и состояния на диске (записанного браузером или Flask-сервером).
     * Полностью повторяет инварианты `shared_progress.py:merge_states`.
     */
    private fun mergeProfiles(leftRaw: ProgressProfile, rightRaw: ProgressProfile): ProgressProfile {
        val left = normalizeProfile(leftRaw)
        val right = normalizeProfile(rightRaw)
        val lState = left.state
        val rState = right.state

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
            theme = rState.theme.ifBlank { lState.theme },
            remindersEnabled = rState.remindersEnabled || lState.remindersEnabled,
            reminderTime = rState.reminderTime.ifBlank { lState.reminderTime },
            lastReminderDay = rState.lastReminderDay ?: lState.lastReminderDay
        )

        // Объединение xpLedger (максимальная награда по каждому ключу)
        val ledger = mutableMapOf<String, Int>()
        ledger.putAll(lState.xpLedger)
        for ((k, v) in rState.xpLedger) {
            ledger[k] = max(ledger[k] ?: 0, v)
        }
        mergedState.xpLedger = ledger

        // Объединение серии дней (streak)
        val lDay = lState.lastActiveDate
        val rDay = rState.lastActiveDate
        val newestDay = listOfNotNull(lDay, rDay).maxOrNull()
        mergedState.lastActiveDate = newestDay
        if (lDay == rDay) {
            mergedState.streak = max(lState.streak, rState.streak)
        } else {
            val newestState = if (newestDay == lDay) lState else rState
            mergedState.streak = newestState.streak
            mergedState.milestones = newestState.milestones.toMutableList()
        }

        // Объединение результатов босс-раундов и мини-экзаменов
        val bossKeys = lState.bossScores.keys + rState.bossScores.keys
        for (k in bossKeys) {
            val a = lState.bossScores[k]
            val b = rState.bossScores[k]
            mergedState.bossScores[k] = ScoreEntry(
                best = max(a?.best ?: 0, b?.best ?: 0),
                passed = (a?.passed == true) || (b?.passed == true)
            )
        }

        val examKeys = lState.examScores.keys + rState.examScores.keys
        for (k in examKeys) {
            val a = lState.examScores[k]
            val b = rState.examScores[k]
            mergedState.examScores[k] = ScoreEntry(
                best = max(a?.best ?: 0, b?.best ?: 0),
                passed = (a?.passed == true) || (b?.passed == true)
            )
        }

        // Объединение попыток
        val attemptKeys = lState.attempts.keys + rState.attempts.keys
        for (k in attemptKeys) {
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

        // Объединение черновиков по наибольшему updatedAt
        val mergedDrafts = mutableMapOf<String, CodeDraft>()
        val draftKeys = left.drafts.keys + right.drafts.keys
        for (k in draftKeys) {
            val dLeft = left.drafts[k]
            val dRight = right.drafts[k]
            mergedDrafts[k] = when {
                dLeft == null -> dRight!!.copy()
                dRight == null -> dLeft.copy()
                dRight.updatedAt >= dLeft.updatedAt -> dRight.copy()
                else -> dLeft.copy()
            }
        }

        // Объединение записей экзаменов
        val mergedExams = mutableMapOf<String, ExamRecord>()
        mergedExams.putAll(left.exams)
        for ((k, rExam) in right.exams) {
            val lExam = mergedExams[k]
            if (lExam == null || rExam.startedAt >= lExam.startedAt || rExam.result?.passed == true) {
                mergedExams[k] = rExam
            }
        }

        recalculateTotalXp(mergedState)

        return ProgressProfile(
            version = 1,
            revision = max(left.revision, right.revision),
            state = mergedState,
            drafts = mergedDrafts,
            exams = mergedExams
        )
    }

    private fun updateStreak(state: ProgressState, todayDate: LocalDate) {
        val todayStr = todayDate.toString()
        if (state.lastActiveDate != todayStr) {
            val priorDate = state.lastActiveDate?.let {
                try { LocalDate.parse(it) } catch (_: Exception) { null }
            }
            val consecutive = priorDate != null && ChronoUnit.DAYS.between(priorDate, todayDate) == 1L
            state.streak = if (consecutive) state.streak + 1 else 1
            if (!consecutive) {
                state.milestones.clear()
            }
            state.lastActiveDate = todayStr
        }
    }

    private fun recalculateTotalXp(state: ProgressState) {
        val tracked = state.xpLedger.entries.sumOf { (key, amount) ->
            if (key.startsWith("task:") && state.legacyAwardedTasks.contains(key.removePrefix("task:"))) {
                0
            } else {
                amount
            }
        }
        if (state.xp > state.legacyXp + tracked) {
            state.legacyXp = state.xp - tracked
        }
        state.xp = state.legacyXp + tracked
    }

    private fun normalizeProfile(raw: ProgressProfile): ProgressProfile {
        val copy = raw.deepCopy()
        recalculateTotalXp(copy.state)
        return copy
    }

    private fun ProgressProfile.deepCopy(): ProgressProfile =
        gson.fromJson(gson.toJson(this), ProgressProfile::class.java) ?: ProgressProfile()

    private fun currentFileMtimeMs(): Long {
        return try {
            if (Files.isRegularFile(progressFile)) Files.getLastModifiedTime(progressFile).toMillis() else -1L
        } catch (_: IOException) {
            -1L
        }
    }

    private fun notifyListeners(profile: ProgressProfile) {
        for (listener in listeners) {
            try {
                listener(profile)
            } catch (e: Exception) {
                log.warn("Ошибка уведомления слушателя прогресса", e)
            }
        }
    }

    /**
     * Фоновый мониторинг изменений `.javada/progress.json` через NIO WatchService + периодический опрос mtime.
     */
    private fun startExternalChangeWatcher() {
        watcherExecutor.scheduleWithFixedDelay({
            try {
                val diskMtime = currentFileMtimeMs()
                if (diskMtime > 0 && diskMtime != lastKnownMtimeMs) {
                    reloadFromDisk()
                }
            } catch (_: Exception) {
                // Игнорируем временные ошибки ФС во время атомарной подмены файла браузером
            }
        }, 2, 2, TimeUnit.SECONDS)

        watcherExecutor.execute {
            try {
                Files.createDirectories(profileDir)
                FileSystems.getDefault().newWatchService().use { watchService ->
                    profileDir.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY
                    )
                    while (!watcherExecutor.isShutdown) {
                        val key = watchService.poll(3, TimeUnit.SECONDS) ?: continue
                        var changed = false
                        for (event in key.pollEvents()) {
                            val changedPath = event.context() as? Path
                            if (changedPath != null && changedPath.fileName.toString() == "progress.json") {
                                changed = true
                            }
                        }
                        key.reset()
                        if (changed) {
                            reloadFromDisk()
                        }
                    }
                }
            } catch (_: Exception) {
                // Фолбэк на периодическую проверку mtime уже активен выше
            }
        }
    }

    override fun dispose() {
        watcherExecutor.shutdownNow()
        listeners.clear()
    }
}
