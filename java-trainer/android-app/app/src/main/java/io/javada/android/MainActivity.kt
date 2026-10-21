package io.javada.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.javada.android.data.CourseExam
import io.javada.android.data.CourseLesson
import io.javada.android.data.CourseModule
import io.javada.android.data.CourseTask
import io.javada.android.data.ItemStatus
import io.javada.android.data.ParsonsLine
import io.javada.android.ui.AppScreen
import io.javada.android.ui.JavadaUiState
import io.javada.android.ui.JavadaViewModel
import io.javada.android.ui.theme.JavadaEmerald
import io.javada.android.ui.theme.JavadaOrange
import io.javada.android.ui.theme.JavadaTheme
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private val viewModel: JavadaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsState()
            val isDark = state.profile.state.theme == "dark"

            // Лаунчер импорта файла progress.json с устройства
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                uri ?: return@rememberLauncherForActivityResult
                contentResolver.openInputStream(uri)?.use { stream ->
                    val raw = stream.readBytes().toString(StandardCharsets.UTF_8)
                    viewModel.importProgressJsonString(raw)
                }
            }

            // Лаунчер экспорта файла progress.json для переноса на ПК (Браузер / IntelliJ IDEA)
            val exportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri: Uri? ->
                uri ?: return@rememberLauncherForActivityResult
                contentResolver.openOutputStream(uri)?.use { stream ->
                    val json = viewModel.exportProgressJsonString()
                    stream.write(json.toByteArray(StandardCharsets.UTF_8))
                }
            }

            BackHandler(enabled = state.screen !is AppScreen.CourseMap) {
                viewModel.navigateBack()
            }

            JavadaTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    JavadaAppRoot(
                        state = state,
                        viewModel = viewModel,
                        onImportProgress = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        onExportProgress = { exportLauncher.launch("progress.json") }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JavadaAppRoot(
    state: JavadaUiState,
    viewModel: JavadaViewModel,
    onImportProgress: () -> Unit,
    onExportProgress: () -> Unit
) {
    val xp = state.profile.state.xp
    val level = (xp / 250) + 1
    val streak = state.profile.state.streak

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (state.screen !is AppScreen.CourseMap) {
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                title = {
                    Column {
                        Text("☕ JavaДа Mobile", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        val statLine = when (state.lang) {
                            "en" -> "Lvl $level · $xp XP · 🔥 ${streak}d"
                            "es" -> "Niv $level · $xp XP · 🔥 ${streak}d"
                            "de" -> "Lvl $level · $xp XP · 🔥 ${streak}T"
                            "zh" -> "等级 $level · $xp XP · 🔥 ${streak}天"
                            else -> "Уровень $level · $xp XP · 🔥 $streak дн."
                        }
                        Text(
                            statLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.cycleLanguage() }) {
                        Text("🌐 ${state.lang.uppercase()}", fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onImportProgress) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Импорт progress.json")
                    }
                    IconButton(onClick = onExportProgress) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Экспорт progress.json")
                    }
                    TextButton(onClick = { viewModel.toggleTheme() }) {
                        Text(if (state.profile.state.theme == "dark") "☀️" else "🌙")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            state.syncBannerMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable { viewModel.dismissSyncBanner() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(msg, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            when (val screen = state.screen) {
                is AppScreen.CourseMap -> CourseMapScreen(state, viewModel)
                is AppScreen.ModuleOverview -> {
                    val module = state.catalog.modules.find { it.id == screen.moduleId }
                    if (module != null) ModuleOverviewScreen(module, state, viewModel)
                }
                is AppScreen.LessonView -> {
                    val module = state.catalog.modules.find { it.id == screen.moduleId }
                    val lesson = module?.lessons?.find { it.id == screen.lessonId }
                    if (module != null && lesson != null) {
                        LessonActivityScreen(module, lesson, state, viewModel)
                    }
                }
                is AppScreen.PracticeView -> {
                    val module = state.catalog.modules.find { it.id == screen.moduleId }
                    if (module != null) {
                        PracticeActivityScreen(module, screen.isBoss, state, viewModel)
                    }
                }
                is AppScreen.MiniExamView -> {
                    val module = state.catalog.modules.find { it.id == screen.moduleId }
                    val exam = module?.exam
                    if (module != null && exam != null) {
                        MiniExamScreen(module, exam, state, viewModel)
                    }
                }
                is AppScreen.LabView -> {
                    val lab = state.catalog.labs.find { it.id == screen.labId }
                    if (lab != null) {
                        LabActivityScreen(lab, state, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun CourseMapScreen(state: JavadaUiState, viewModel: JavadaViewModel) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val lessonsDone = state.profile.state.completedLessons.size

    Column(modifier = Modifier.fillMaxSize()) {
        // Карточка общего прогресса курса
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "Автономный курс Java (Офлайн)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Пройдено уроков: $lessonsDone из 99 · Экзаменов сдано: ${state.profile.state.examScores.values.count { it.passed }} из 22",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (lessonsDone / 99f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        TabRow(selectedTabIndex = selectedSection) {
            Tab(
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                text = { Text("22 Модуля") }
            )
            Tab(
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 },
                text = { Text("5 Лабораторных") }
            )
        }

        if (selectedSection == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.catalog.modules, key = { it.id }) { module ->
                    val status = viewModel.getModuleStatus(module)
                    val doneCount = module.lessons.count { state.profile.state.completedLessons.contains(it.id) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateTo(AppScreen.ModuleOverview(module.id)) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusIcon(status)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Модуль ${module.id}. ${module.title}",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (module.subtitle.isNotBlank()) {
                                    Text(
                                        module.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "Уроков: $doneCount/${module.lessons.size} · Практика · Мини-экзамен",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JavadaOrange
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.catalog.labs, key = { it.id }) { lab ->
                    val solved = state.profile.state.completedLabs.contains(lab.id)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateTo(AppScreen.LabView(lab.id)) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusIcon(if (solved) ItemStatus.COMPLETED else ItemStatus.AVAILABLE)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lab.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    lab.prompt.lines().firstOrNull() ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleOverviewScreen(module: CourseModule, state: JavadaUiState, viewModel: JavadaViewModel) {
    val pState = state.profile.state
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Модуль ${module.id}. ${module.title}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                module.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        items(module.lessons, key = { it.id }) { lesson ->
            val done = pState.completedLessons.contains(lesson.id)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.navigateTo(AppScreen.LessonView(module.id, lesson.id)) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusIcon(if (done) ItemStatus.COMPLETED else ItemStatus.AVAILABLE)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${lesson.id.uppercase()} · ${lesson.title}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${lesson.durationMinutes} мин · Теория + ${lesson.tasks.size} заданий",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (module.bossTasks.isNotEmpty()) {
            item {
                val bossPassed = pState.bossScores[module.id.toString()]?.passed == true
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.navigateTo(AppScreen.PracticeView(module.id, isBoss = true)) }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusIcon(if (bossPassed) ItemStatus.COMPLETED else ItemStatus.AVAILABLE)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Стоп-урок перед практикой", fontWeight = FontWeight.Bold)
                            Text("5 проверочных вопросов (порог 80%)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            val practiceDone = pState.completedPractices.contains(module.id)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.navigateTo(AppScreen.PracticeView(module.id, isBoss = false)) }
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusIcon(if (practiceDone) ItemStatus.COMPLETED else ItemStatus.AVAILABLE)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Финальная практика модуля", fontWeight = FontWeight.Bold)
                        Text(
                            "${module.practiceTasks.size} разнотипных заданий",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        module.exam?.let { exam ->
            item {
                val examScore = pState.examScores[module.id.toString()]
                val passed = examScore?.passed == true
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.navigateTo(AppScreen.MiniExamView(module.id)) }
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusIcon(if (passed) ItemStatus.COMPLETED else ItemStatus.AVAILABLE)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "🏆 Мини-экзамен (+${exam.xpBonus} XP)",
                                fontWeight = FontWeight.Bold,
                                color = JavadaOrange
                            )
                            Text(
                                "${exam.tasks.size} заданий без подсказок · Порог ${exam.passingPercent}%" +
                                        (examScore?.best?.let { " · Лучший: $it%" } ?: ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LessonActivityScreen(
    module: CourseModule,
    lesson: CourseLesson,
    state: JavadaUiState,
    viewModel: JavadaViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (state.showTheoryTab) 0 else 1) {
            Tab(
                selected = state.showTheoryTab,
                onClick = { viewModel.setLessonTab(true) },
                text = { Text("📖 Теория урока") }
            )
            Tab(
                selected = !state.showTheoryTab,
                onClick = { viewModel.setLessonTab(false) },
                text = { Text("✍️ Задания (${lesson.tasks.size})") }
            )
        }

        if (state.showTheoryTab) {
            val t = lesson.theory
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(lesson.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (t.objective.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            "🎯 Цель: ${t.objective}",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                t.paragraphs.forEach { p ->
                    Text(p, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
                }
                if (t.code.isNotBlank()) {
                    CodeSnippetBox(code = t.code, label = t.caption.ifBlank { "Фрагмент кода" })
                }
                if (t.walkthrough.isNotBlank()) {
                    Text("🔍 Разбор: ${t.walkthrough}", style = MaterialTheme.typography.bodyMedium)
                }
                if (t.pitfall.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text("⚠️ Частая ошибка: ${t.pitfall}", modifier = Modifier.padding(12.dp))
                    }
                }
                if (t.deepDive.isNotBlank()) {
                    Text("💡 Глубже в тему: ${t.deepDive}", style = MaterialTheme.typography.bodyMedium)
                }
                if (t.useCase.isNotBlank()) {
                    Text("🛠 На практике: ${t.useCase}", style = MaterialTheme.typography.bodyMedium)
                }
                if (t.guideSteps.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Пошаговое руководство", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (t.guideWhy.isNotBlank()) Text(t.guideWhy, style = MaterialTheme.typography.bodyMedium)
                    t.guideSteps.forEachIndexed { idx, step ->
                        Text("${idx + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (t.guideExampleCode.isNotBlank()) {
                    CodeSnippetBox(code = t.guideExampleCode, label = t.guideExampleLabel)
                    if (t.guideExampleStdout.isNotBlank()) {
                        Text(
                            "Вывод программы:\n${t.guideExampleStdout}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.setLessonTab(false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Перейти к заданиям урока →")
                }
            }
        } else {
            TaskPagerWorkspace(
                tasks = lesson.tasks,
                module = module,
                state = state,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun PracticeActivityScreen(
    module: CourseModule,
    isBoss: Boolean,
    state: JavadaUiState,
    viewModel: JavadaViewModel
) {
    val tasks = if (isBoss) module.bossTasks else module.practiceTasks
    TaskPagerWorkspace(tasks = tasks, module = module, state = state, viewModel = viewModel)
}

@Composable
fun LabActivityScreen(
    lab: CourseTask,
    state: JavadaUiState,
    viewModel: JavadaViewModel
) {
    TaskPagerWorkspace(tasks = listOf(lab), module = null, state = state, viewModel = viewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskPagerWorkspace(
    tasks: List<CourseTask>,
    module: CourseModule?,
    state: JavadaUiState,
    viewModel: JavadaViewModel
) {
    val currentIndex = state.selectedTaskIndex.coerceIn(0, (tasks.size - 1).coerceAtLeast(0))
    val task = tasks.getOrNull(currentIndex) ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (tasks.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tasks.forEachIndexed { idx, item ->
                    val solved = state.profile.state.awardedTasks.contains(item.id) ||
                            state.profile.state.attempts[item.id]?.correct == true
                    FilterChip(
                        selected = idx == currentIndex,
                        onClick = { viewModel.selectTaskIndex(idx) },
                        label = {
                            Text(
                                (if (solved) "✓ " else "") +
                                        (if (item.optional) "★ Хард" else "#${idx + 1}")
                            )
                        }
                    )
                }
            }
        }

        TaskInteractiveCard(
            task = task,
            initialCode = viewModel.getInitialCodeForTask(task),
            isExamMode = false,
            showHint = state.showHint,
            onToggleHint = { viewModel.toggleHint() },
            onDraftChanged = { code -> viewModel.saveCodeDraft(task.id, code) },
            onSubmitAnswer = { answer -> viewModel.checkTaskAnswer(task, module, answer) }
        )

        state.lastValidation?.let { outcome ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (outcome.success) JavadaEmerald.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(outcome.title, fontWeight = FontWeight.Bold)
                    if (outcome.message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(outcome.message, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (outcome.output.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Вывод:\n${outcome.output}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniExamScreen(
    module: CourseModule,
    exam: CourseExam,
    state: JavadaUiState,
    viewModel: JavadaViewModel
) {
    val summary = state.examSummary
    if (summary != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (summary.passed) "🎉 Мини-экзамен сдан!" else "❌ Мини-экзамен не сдан",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (summary.passed) JavadaEmerald else MaterialTheme.colorScheme.error
            )
            Text(
                "Результат: ${summary.percent}% (${summary.correct} из ${summary.total}). " +
                        if (summary.passed) "Начислено +${summary.xp} XP, следующий модуль открыт!"
                        else "Для зачёта требуется минимум ${exam.passingPercent}%.",
                style = MaterialTheme.typography.bodyLarge
            )
            HorizontalDivider()
            summary.details.forEach { d ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${if (d.correct) "✅" else "❌"} Задание ${d.id}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (d.correct) d.explanation.ifBlank { "Ответ верный" } else d.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Button(
                onClick = { viewModel.navigateTo(AppScreen.ModuleOverview(module.id)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Вернуться к модулю")
            }
        }
        return
    }

    val stepIdx = state.selectedTaskIndex.coerceIn(0, exam.tasks.lastIndex)
    val task = exam.tasks[stepIdx]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Мини-экзамен · Вопрос ${stepIdx + 1} из ${exam.tasks.size}", fontWeight = FontWeight.Bold)
                Text("Подсказки отключены", style = MaterialTheme.typography.labelSmall)
            }
        }

        TaskInteractiveCard(
            task = task,
            initialCode = viewModel.getInitialCodeForTask(task),
            isExamMode = true,
            showHint = false,
            onToggleHint = {},
            onDraftChanged = { code -> viewModel.saveCodeDraft(task.id, code) },
            onSubmitAnswer = { answer ->
                viewModel.submitExamStepAndAdvance(module, exam, task, answer)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskInteractiveCard(
    task: CourseTask,
    initialCode: String,
    isExamMode: Boolean,
    showHint: Boolean,
    onToggleHint: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSubmitAnswer: (Any?) -> Unit
) {
    // Локальное состояние ответа для текущего задания
    var codeText by remember(task.id) { mutableStateOf(initialCode) }
    val selectedChoices = remember(task.id) { mutableStateListOf<String>() }
    val fillValues = remember(task.id) { mutableStateMapOf<String, String>() }
    val matchingValues = remember(task.id) { mutableStateMapOf<String, String>() }
    val parsonsLines = remember(task.id) {
        mutableStateListOf<ParsonsLine>().apply { addAll(task.parsonsLines) }
    }

    LaunchedEffect(task.id, initialCode) {
        codeText = initialCode
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("${task.title} (${task.id})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(task.prompt, style = MaterialTheme.typography.bodyLarge)

            if (task.type == "copy" && task.sourceCode.isNotBlank()) {
                CodeSnippetBox(code = task.sourceCode, label = "Образец для переписывания вручную")
            }

            if (task.sampleInput.isNotBlank() || task.sampleOutput.isNotBlank()) {
                CodeSnippetBox(
                    code = "Ввод:\n${task.sampleInput.trim()}\n\nОжидаемый вывод:\n${task.sampleOutput.trim()}",
                    label = "Пример данных"
                )
            }

            when (task.type) {
                "choice" -> {
                    task.options.forEach { opt ->
                        val isSelected = selectedChoices.contains(opt.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (task.multipleChoice) {
                                        if (isSelected) selectedChoices.remove(opt.id) else selectedChoices.add(opt.id)
                                    } else {
                                        selectedChoices.clear()
                                        selectedChoices.add(opt.id)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (task.multipleChoice) {
                                Checkbox(checked = isSelected, onCheckedChange = null)
                            } else {
                                RadioButton(selected = isSelected, onClick = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(opt.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                "fill" -> {
                    val preview = task.fillTemplate.joinToString(" [ ___ ] ")
                    CodeSnippetBox(code = preview, label = "Шаблон выражения")
                    task.fillBlanks.forEach { blank ->
                        Text("Пропуск «${blank.id}»:", fontWeight = FontWeight.SemiBold)
                        if (blank.kind == "select" && blank.options.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                blank.options.forEach { opt ->
                                    FilterChip(
                                        selected = fillValues[blank.id] == opt,
                                        onClick = { fillValues[blank.id] = opt },
                                        label = { Text(opt) }
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = fillValues[blank.id] ?: "",
                                onValueChange = { fillValues[blank.id] = it },
                                label = { Text("Введите значение (${blank.id})") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                "matching" -> {
                    task.matchingPairs.forEach { pair ->
                        Text("🔹 ${pair.left}", fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            task.matchingPairs.forEach { candidate ->
                                FilterChip(
                                    selected = matchingValues[pair.id] == candidate.id,
                                    onClick = { matchingValues[pair.id] = candidate.id },
                                    label = { Text(candidate.right) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                "parsons" -> {
                    Text(
                        "Расположите строки в нужном порядке (↑/↓) и выставьте уровень отступа (←/→):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    parsonsLines.forEachIndexed { idx, line ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "    ".repeat(line.indent) + line.text,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        if (idx > 0) {
                                            val item = parsonsLines.removeAt(idx)
                                            parsonsLines.add(idx - 1, item)
                                        }
                                    }
                                ) { Text("↑") }
                                TextButton(
                                    onClick = {
                                        if (idx < parsonsLines.lastIndex) {
                                            val item = parsonsLines.removeAt(idx)
                                            parsonsLines.add(idx + 1, item)
                                        }
                                    }
                                ) { Text("↓") }
                                TextButton(
                                    onClick = {
                                        parsonsLines[idx] = line.copy(indent = (line.indent - 1).coerceAtLeast(0))
                                    }
                                ) { Text("←") }
                                TextButton(
                                    onClick = {
                                        parsonsLines[idx] = line.copy(indent = (line.indent + 1).coerceAtMost(3))
                                    }
                                ) { Text("→") }
                            }
                        }
                    }
                }

                "code", "copy" -> {
                    // Мобильная панель быстрого ввода символов Java
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("{", "}", "(", ")", ";", "\"", "=", "System.out.println();").forEach { snippet ->
                            AssistChip(
                                onClick = {
                                    codeText += snippet
                                    onDraftChanged(codeText)
                                },
                                label = { Text(snippet, fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = codeText,
                        onValueChange = {
                            codeText = it
                            onDraftChanged(it)
                        },
                        label = { Text("Main.java") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        minLines = 8,
                        maxLines = 18,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!isExamMode && showHint && task.hint.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text("💡 Подсказка: ${task.hint}", modifier = Modifier.padding(10.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val answer: Any = when (task.type) {
                            "choice" -> selectedChoices.toList()
                            "fill" -> fillValues.toMap()
                            "matching" -> matchingValues.toMap()
                            "parsons" -> parsonsLines.toList()
                            else -> codeText
                        }
                        onSubmitAnswer(answer)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isExamMode) "Ответить и далее →" else "Проверить решение")
                }

                if (!isExamMode && task.hint.isNotBlank()) {
                    OutlinedButton(onClick = onToggleHint) {
                        Text("Подсказка")
                    }
                }
            }
        }
    }
}

@Composable
fun CodeSnippetBox(code: String, label: String = "") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        if (label.isNotBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = JavadaOrange
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun StatusIcon(status: ItemStatus) {
    when (status) {
        ItemStatus.COMPLETED -> Icon(Icons.Default.CheckCircle, contentDescription = "Выполнено", tint = JavadaEmerald)
        ItemStatus.AVAILABLE -> Icon(Icons.Default.PlayArrow, contentDescription = "Доступно", tint = JavadaOrange)
        ItemStatus.LOCKED -> Icon(Icons.Default.Lock, contentDescription = "Заблокировано", tint = MaterialTheme.colorScheme.outline)
    }
}
