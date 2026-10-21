package io.javada.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.javada.android.data.CourseExam
import io.javada.android.data.CourseLesson
import io.javada.android.data.CourseModule
import io.javada.android.data.CourseTask
import io.javada.android.data.ParsonsLine
import io.javada.android.domain.ValidationOutcome
import io.javada.android.ui.theme.LocalJavadaColors
import java.util.Locale

@Composable
fun LessonScreen(lesson: CourseLesson, module: CourseModule, state: JavadaUiState, viewModel: JavadaViewModel) {
    val lang = state.lang
    Column(modifier = Modifier.fillMaxSize()) {
        SessionProgressHeader(
            title = module.title,
            position = state.selectedTaskIndex + 1,
            total = lesson.tasks.size,
            label = UiText.get(lang, "lesson") + " · " + lesson.title,
            progress = if (state.showTheoryTab) 0f else (state.selectedTaskIndex + 1f) / lesson.tasks.size.coerceAtLeast(1)
        )
        LessonSegmentedTabs(
            showTheory = state.showTheoryTab,
            lesson = lesson,
            lang = lang,
            onTheory = { viewModel.setLessonTab(true) },
            onTasks = { viewModel.setLessonTab(false) }
        )
        if (state.showTheoryTab) {
            TheoryScreenContent(lesson, lang, onStart = { viewModel.setLessonTab(false) })
        } else {
            TaskSessionContent(
                tasks = lesson.tasks,
                module = module,
                title = lesson.title,
                mode = "lesson",
                state = state,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun LessonSegmentedTabs(
    showTheory: Boolean,
    lesson: CourseLesson,
    lang: String,
    onTheory: () -> Unit,
    onTasks: () -> Unit
) {
    val palette = LocalJavadaColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(11.dp)).background(palette.surfaceSoft).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentButton(
            label = "📖  ${UiText.get(lang, "theory")}",
            selected = showTheory,
            modifier = Modifier.weight(1f),
            onClick = onTheory
        )
        SegmentButton(
            label = "✍  ${UiText.get(lang, "tasks")} · ${lesson.tasks.size}",
            selected = !showTheory,
            modifier = Modifier.weight(1f),
            onClick = onTasks
        )
    }
}

@Composable
private fun SegmentButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalJavadaColors.current
    Box(
        modifier = modifier.height(41.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) palette.surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) palette.greenDeep else palette.mutedStrong, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SessionProgressHeader(title: String, position: Int, total: Int, label: String, progress: Float) {
    val palette = LocalJavadaColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow(title)
                Text(label, color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("$position / $total", color = palette.greenDeep, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        ProgressMeter(progress, height = 8.dp)
    }
}

@Composable
private fun TheoryScreenContent(lesson: CourseLesson, lang: String, onStart: () -> Unit) {
    val t = lesson.theory
    val palette = LocalJavadaColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 17.dp, top = 8.dp, end = 17.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            JavadaCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Eyebrow("📖  ${UiText.get(lang, "theory")}")
                    Text(lesson.title, color = palette.text, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.ExtraBold)
                    if (t.objective.isNotBlank()) {
                        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(palette.greenSoft).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Eyebrow(UiText.get(lang, "objective"))
                            Text(t.objective, color = palette.text, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                    t.paragraphs.forEach { paragraph ->
                        Text(paragraph, color = palette.mutedStrong, fontSize = 13.sp, lineHeight = 21.sp)
                    }
                    if (t.code.isNotBlank()) CodeSnippetBox(t.code, t.caption.ifBlank { UiText.get(lang, "worked_example") })
                    if (t.walkthrough.isNotBlank()) InfoCallout("✦", t.walkthrough, palette.greenSoft, palette.greenDeep)
                    if (t.deepDive.isNotBlank()) {
                        Eyebrow(if (lang == "ru") "РАЗОБРАТЬ ГЛУБЖЕ" else "GO DEEPER")
                        Text(t.deepDive, color = palette.mutedStrong, fontSize = 12.sp, lineHeight = 19.sp)
                    }
                    if (t.useCase.isNotBlank()) {
                        Eyebrow(if (lang == "ru") "ГДЕ ПРИМЕНИТЬ" else "WHEN TO USE IT")
                        Text(t.useCase, color = palette.mutedStrong, fontSize = 12.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        if (t.guideWhy.isNotBlank() || t.guideSteps.isNotEmpty()) {
            item {
                JavadaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow(UiText.get(lang, "worked_example"))
                        if (t.guideWhy.isNotBlank()) Text(t.guideWhy, color = palette.mutedStrong, fontSize = 12.sp, lineHeight = 18.sp)
                        if (t.guideSteps.isNotEmpty()) {
                            Text(UiText.get(lang, "guide"), color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            t.guideSteps.forEachIndexed { index, step ->
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(palette.greenSoft), contentAlignment = Alignment.Center) {
                                        Text("${index + 1}", color = palette.greenDeep, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Text(step, color = palette.mutedStrong, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        if (t.guideExampleCode.isNotBlank()) CodeSnippetBox(t.guideExampleCode, t.guideExampleLabel.ifBlank { UiText.get(lang, "worked_example") })
                        if (t.guideExampleStdout.isNotBlank()) CodeSnippetBox(t.guideExampleStdout, UiText.get(lang, "example_output"))
                        if (t.guideResult.isNotBlank()) InfoCallout("✓", t.guideResult, palette.greenSoft, palette.greenDeep)
                        if (t.guideBridge.isNotBlank()) Text(t.guideBridge, color = palette.mutedStrong, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
        if (t.pitfall.isNotBlank()) {
            item {
                InfoCallout("⚠", "${UiText.get(lang, "common_mistake")}\n${t.pitfall}", palette.yellowSoft, palette.yellowText)
            }
        }
        item {
            JavadaButton(
                text = UiText.get(lang, "go_to_tasks"),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                leading = "✍",
                trailing = "↓"
            )
        }
    }
}

@Composable
private fun InfoCallout(glyph: String, text: String, background: Color, foreground: Color) {
    JavadaCard(modifier = Modifier.fillMaxWidth(), containerColor = background) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
            Text(glyph, color = foreground, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(text, color = foreground, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun PracticeScreen(module: CourseModule, isBoss: Boolean, state: JavadaUiState, viewModel: JavadaViewModel) {
    val mode = if (isBoss) "boss" else "practice"
    val tasks = if (isBoss) module.bossTasks else module.practiceTasks
    val title = UiText.get(state.lang, if (isBoss) "stop_lesson" else "final_practice")
    Column(modifier = Modifier.fillMaxSize()) {
        SessionProgressHeader(
            title = module.title,
            position = state.selectedTaskIndex + 1,
            total = tasks.size,
            label = title,
            progress = (state.selectedTaskIndex + 1f) / tasks.size.coerceAtLeast(1)
        )
        TaskSessionContent(tasks, module, title, mode, state, viewModel)
    }
}

@Composable
fun ReviewSessionScreen(state: JavadaUiState, tasks: List<CourseTask>, viewModel: JavadaViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        SessionProgressHeader(
            title = UiText.get(state.lang, "review"),
            position = state.selectedTaskIndex + 1,
            total = tasks.size,
            label = UiText.get(state.lang, "review_title"),
            progress = (state.selectedTaskIndex + 1f) / tasks.size.coerceAtLeast(1)
        )
        TaskSessionContent(tasks, null, UiText.get(state.lang, "review_title"), "review", state, viewModel)
    }
}

@Composable
private fun TaskSessionContent(
    tasks: List<CourseTask>,
    module: CourseModule?,
    title: String,
    mode: String,
    state: JavadaUiState,
    viewModel: JavadaViewModel
) {
    val lang = state.lang
    val palette = LocalJavadaColors.current
    if (tasks.isEmpty()) {
        EmptyState(UiText.get(lang, "empty"), UiText.get(lang, "complete_lesson_first"))
        return
    }
    val index = state.selectedTaskIndex.coerceIn(0, tasks.lastIndex)
    val task = tasks[index]
    val currentSolved = viewModel.isTaskSolved(task)
    val solvedCount = tasks.count(viewModel::isTaskSolved)
    val canMoveNext = index < tasks.lastIndex && when (mode) {
        "boss", "review" -> tasks.take(index + 1).all { it.id in state.sessionAttemptedIds || viewModel.isTaskSolved(it) }
        else -> viewModel.canOpenTask(tasks, index + 1)
    }
    val canFinish = viewModel.canAdvanceSession(mode, tasks)
    val needsAnswerForBoss = mode == "boss" && task.id in state.sessionAttemptedIds
    val success = state.lastValidation?.success == true

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 17.dp, top = 7.dp, end = 17.dp, bottom = 25.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        if (mode == "lab") {
            item {
                val lab = tasks.first()
                JavadaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow(UiText.get(lang, "lab_goal"))
                        Text(lab.title, color = palette.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                        Text(lab.prompt, color = palette.mutedStrong, fontSize = 11.sp, lineHeight = 17.sp)
                        if (lab.sampleInput.isNotBlank()) CodeSnippetBox(lab.sampleInput, UiText.get(lang, "sample_input"))
                        if (lab.expectedOutput.isNotBlank()) CodeSnippetBox(lab.expectedOutput, UiText.get(lang, "expected_output"))
                    }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tasks.forEachIndexed { taskIndex, entry ->
                    val solved = viewModel.isTaskSolved(entry)
                    val allowed = taskIndex <= index || when (mode) {
                        "boss", "review" -> tasks.take(taskIndex).all { it.id in state.sessionAttemptedIds || viewModel.isTaskSolved(it) }
                        else -> viewModel.canOpenTask(tasks, taskIndex)
                    }
                    FilterChip(
                        selected = taskIndex == index,
                        onClick = { if (allowed) viewModel.selectTaskIndex(taskIndex) },
                        enabled = allowed,
                        label = { Text(if (solved) "✓ ${taskIndex + 1}" else "${taskIndex + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }
        item {
            JavadaCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(UiText.get(lang, "question_of", index + 1, tasks.size).uppercase(), color = palette.muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .9.sp)
                        Text("⚡ ${taskXp(task)} XP", color = palette.yellowText, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(palette.yellowSoft).padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(taskTypeGlyph(task.type), color = palette.greenDeep, fontSize = 16.sp)
                        Text(taskTypeLabel(task.type, lang), color = palette.greenDeep, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .5.sp)
                        if (task.optional) Text(UiText.get(lang, "optional"), color = palette.yellowText, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(task.prompt, color = palette.text, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
                    if (task.type == "code" && (task.sampleInput.isNotBlank() || task.expectedOutput.isNotBlank())) {
                        CodeSnippetBox(
                            "${UiText.get(lang, "sample_input")}:\n${task.sampleInput.trim()}\n\n${UiText.get(lang, "expected_output")}:\n${task.expectedOutput.trim()}",
                            UiText.get(lang, "example_output")
                        )
                    }
                    if (task.type == "copy" && task.sourceCode.isNotBlank()) CodeSnippetBox(task.sourceCode, if (lang == "ru") "ОБРАЗЕЦ КОДА" else "CODE TO RETYPE")
                    TaskInput(
                        task = task,
                        lang = lang,
                        initialCode = viewModel.getDraftOrStarter(task),
                        initialAnswer = state.sessionAnswers[task.id],
                        isExamMode = false,
                        onAnswerChanged = { answer -> viewModel.recordSessionAnswer(task.id, answer) },
                        onDraftChanged = { code -> viewModel.saveDraft(task.id, code) },
                        onSubmit = {
                            if (task.type == "code" || task.type == "copy") viewModel.saveDraft(task.id, it as? String ?: "")
                            viewModel.checkTaskAnswer(task, module, it, isReview = mode == "review")
                        }
                    )
                    if (!state.showHint && task.hint.isNotBlank()) {
                        TextButton(onClick = viewModel::toggleHint, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) {
                            androidx.compose.material3.Icon(Icons.Filled.Lightbulb, null, tint = palette.yellowText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(UiText.get(lang, "hint"), color = palette.mutedStrong, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (state.showHint && task.hint.isNotBlank()) {
                        InfoCallout("✦", "${UiText.get(lang, "hint_prefix")}: ${task.hint}", palette.yellowSoft, palette.yellowText)
                    }
                }
            }
        }
        state.lastValidation?.let { outcome ->
            item { ValidationFeedback(outcome, lang) }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$solvedCount / ${tasks.count { !it.optional }}", color = palette.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (task.optional && index < tasks.lastIndex) {
                    OutlineJavadaButton(UiText.get(lang, "continue"), { viewModel.selectTaskIndex(index + 1) }, leading = "↷")
                }
                if (canMoveNext && (success || currentSolved || needsAnswerForBoss || mode == "review" && task.id in state.sessionAttemptedIds)) {
                    JavadaButton(UiText.get(lang, "continue"), { viewModel.selectTaskIndex(index + 1) }, trailing = "›")
                } else if (index == tasks.lastIndex && canFinish) {
                    JavadaButton(UiText.get(lang, "finish_session"), { viewModel.finishCurrentSession(mode, module, title, tasks) }, trailing = "✓")
                } else if (index < tasks.lastIndex && canFinish && (success || currentSolved || mode == "review" || mode == "boss")) {
                    JavadaButton(UiText.get(lang, "finish_session"), { viewModel.finishCurrentSession(mode, module, title, tasks) })
                }
            }
        }
    }
}

@Composable
private fun ValidationFeedback(outcome: ValidationOutcome, lang: String) {
    val palette = LocalJavadaColors.current
    val bg = if (outcome.success) palette.greenSoft else palette.redSoft
    val fg = if (outcome.success) palette.greenDeep else palette.redText
    JavadaCard(modifier = Modifier.fillMaxWidth(), containerColor = bg) {
        Row(modifier = Modifier.padding(13.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
            Text(if (outcome.success) "✓" else "!", color = fg, fontSize = 19.sp, fontWeight = FontWeight.Black)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(outcome.title, color = fg, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                if (outcome.message.isNotBlank()) Text(outcome.message, color = fg, fontSize = 10.sp, lineHeight = 16.sp)
                if (outcome.output.isNotBlank()) CodeSnippetBox(outcome.output, UiText.get(lang, "example_output"))
            }
        }
    }
}

@Composable
fun TaskInput(
    task: CourseTask,
    lang: String,
    initialCode: String,
    initialAnswer: Any?,
    isExamMode: Boolean,
    onAnswerChanged: (Any) -> Unit,
    onDraftChanged: (String) -> Unit = {},
    onSubmit: (Any) -> Unit
) {
    val palette = LocalJavadaColors.current
    var codeText by remember(task.id) { mutableStateOf((initialAnswer as? String) ?: initialCode) }
    val selectedChoices = remember(task.id) {
        mutableStateListOf<String>().apply { addAll((initialAnswer as? Collection<*>)?.mapNotNull { it?.toString() } ?: emptyList()) }
    }
    val fillValues = remember(task.id) {
        mutableStateMapOf<String, String>().apply { putAll((initialAnswer as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()) }
    }
    val matchingValues = remember(task.id) {
        mutableStateMapOf<String, String>().apply { putAll((initialAnswer as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()) }
    }
    val initialLines = (initialAnswer as? List<*>)?.filterIsInstance<ParsonsLine>() ?: task.parsonsLines
    val parsonsLines = remember(task.id) { mutableStateListOf<ParsonsLine>().apply { addAll(initialLines) } }

    fun answerValue(): Any = when (task.type) {
        "choice" -> selectedChoices.toList()
        "fill" -> fillValues.toMap()
        "matching" -> matchingValues.toMap()
        "parsons" -> parsonsLines.toList()
        else -> codeText
    }
    LaunchedEffect(task.id, codeText) {
        if (task.type == "code" || task.type == "copy") {
            kotlinx.coroutines.delay(350)
            onDraftChanged(codeText)
        }
    }

    fun changed() { onAnswerChanged(answerValue()) }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        when (task.type) {
            "choice" -> task.options.forEachIndexed { index, option ->
                val checked = option.id in selectedChoices
                AnswerOptionCard(
                    label = option.label,
                    index = ('A'.code + index).toChar().toString(),
                    checked = checked,
                    multiple = task.multipleChoice,
                    onClick = {
                        if (task.multipleChoice) {
                            if (checked) selectedChoices.remove(option.id) else selectedChoices.add(option.id)
                        } else {
                            selectedChoices.clear()
                            selectedChoices.add(option.id)
                        }
                        changed()
                    }
                )
            }
            "fill" -> {
                if (task.fillTemplate.isNotEmpty()) CodeSnippetBox(task.fillTemplate.joinToString("[ ___ ]"), if (lang == "ru") "ШАБЛОН" else "TEMPLATE")
                task.fillBlanks.forEach { blank ->
                    Text(blank.id, color = palette.mutedStrong, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (blank.kind == "select" && blank.options.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            blank.options.forEach { option ->
                                FilterChip(
                                    selected = fillValues[blank.id] == option,
                                    onClick = { fillValues[blank.id] = option; changed() },
                                    label = { Text(option, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = fillValues[blank.id].orEmpty(),
                            onValueChange = { fillValues[blank.id] = it; changed() },
                            label = { Text(UiText.get(lang, "answer_blank")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(9.dp)
                        )
                    }
                }
            }
            "matching" -> task.matchingPairs.forEach { pair ->
                JavadaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(pair.left, color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            task.matchingPairs.forEach { candidate ->
                                FilterChip(
                                    selected = matchingValues[pair.id] == candidate.id,
                                    onClick = { matchingValues[pair.id] = candidate.id; changed() },
                                    label = { Text(candidate.right, fontSize = 10.sp, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            }
            "parsons" -> {
                Text(UiText.get(lang, "reorder_help"), color = palette.mutedStrong, fontSize = 10.sp, lineHeight = 15.sp)
                parsonsLines.forEachIndexed { index, line ->
                    JavadaCard(modifier = Modifier.fillMaxWidth(), containerColor = palette.surfaceSoft) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("⋮⋮", color = palette.muted, fontSize = 13.sp)
                            Text("    ".repeat(line.indent) + line.text, color = palette.text, fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = {
                                if (index > 0) { val row = parsonsLines.removeAt(index); parsonsLines.add(index - 1, row); changed() }
                            }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("↑", color = palette.greenDeep, fontSize = 13.sp) }
                            TextButton(onClick = {
                                if (index < parsonsLines.lastIndex) { val row = parsonsLines.removeAt(index); parsonsLines.add(index + 1, row); changed() }
                            }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("↓", color = palette.greenDeep, fontSize = 13.sp) }
                            TextButton(onClick = { parsonsLines[index] = line.copy(indent = (line.indent - 1).coerceAtLeast(0)); changed() }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) { Text("−", color = palette.mutedStrong) }
                            TextButton(onClick = { parsonsLines[index] = line.copy(indent = (line.indent + 1).coerceAtMost(3)); changed() }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)) { Text("+", color = palette.mutedStrong) }
                        }
                    }
                }
            }
            "code", "copy" -> {
                if (task.type == "code") {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("{", "}", "(", ")", ";", "\"", "=", "System.out.println();").forEach { snippet ->
                            FilterChip(
                                selected = false,
                                onClick = { codeText += snippet; changed() },
                                label = { Text(snippet, fontFamily = FontFamily.Monospace, fontSize = 9.sp) }
                            )
                        }
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(palette.codeBg)) {
                    Row(modifier = Modifier.fillMaxWidth().background(palette.codeHeaderBg).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = palette.green, fontSize = 10.sp)
                        Spacer(Modifier.width(7.dp))
                        Text("Main.java", color = palette.codeMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        Spacer(Modifier.weight(1f))
                        Text("JAVA", color = palette.codeMuted, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
                    }
                    TextField(
                        value = codeText,
                        onValueChange = { codeText = it; changed() },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 9,
                        maxLines = 20,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = palette.codeText, lineHeight = 18.sp, fontSize = 11.sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = palette.codeBg,
                            unfocusedContainerColor = palette.codeBg,
                            disabledContainerColor = palette.codeBg,
                            focusedTextColor = palette.codeText,
                            unfocusedTextColor = palette.codeText,
                            cursorColor = palette.greenBright,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        placeholder = { Text("// ${UiText.get(lang, "java_editor")}", color = palette.codeMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                    )
                }
            }
            else -> Text(task.prompt, color = palette.mutedStrong, fontSize = 12.sp)
        }
        if (!isExamMode) {
            JavadaButton(
                text = UiText.get(lang, if (task.type == "code") "check_code" else "check_answer"),
                onClick = { onSubmit(answerValue()) },
                modifier = Modifier.fillMaxWidth(),
                trailing = "›"
            )
        }
    }
}

@Composable
private fun AnswerOptionCard(label: String, index: String, checked: Boolean, multiple: Boolean, onClick: () -> Unit) {
    val palette = LocalJavadaColors.current
    val stroke = if (checked) palette.green else palette.line
    val bg = if (checked) palette.greenSoft else palette.surface
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).border(if (checked) 1.5.dp else 1.dp, stroke, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.size(27.dp).clip(RoundedCornerShape(7.dp)).background(if (checked) palette.green else palette.surfaceSoft), contentAlignment = Alignment.Center) {
            Text(index, color = if (checked) Color.White else palette.mutedStrong, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(label, color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), lineHeight = 16.sp)
        if (checked) Text("✓", color = palette.greenDeep, fontWeight = FontWeight.Black)
        if (!multiple && !checked) RadioButton(selected = false, onClick = null, modifier = Modifier.size(20.dp))
        if (multiple && !checked) Checkbox(checked = false, onCheckedChange = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MiniExamScreen(module: CourseModule, exam: CourseExam, state: JavadaUiState, viewModel: JavadaViewModel) {
    val lang = state.lang
    val palette = LocalJavadaColors.current
    val result = state.examSummary
    if (result != null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 17.dp, top = 24.dp, end = 17.dp, bottom = 25.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                JavadaCard(modifier = Modifier.fillMaxWidth(), containerColor = if (result.passed) palette.greenSoft else palette.yellowSoft) {
                    Column(modifier = Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (result.passed) "🏆" else "✦", fontSize = 37.sp)
                        Text(UiText.get(lang, if (result.passed) "exam_passed" else "exam_failed"), color = if (result.passed) palette.greenDeep else palette.yellowText, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${result.percent}% · ${result.correct} / ${result.total}", color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (result.passed) Text("+${result.xp} XP", color = palette.greenDeep, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        else Text(UiText.get(lang, "exam_description", exam.passingPercent), color = palette.mutedStrong, fontSize = 11.sp)
                    }
                }
            }
            item { SectionHeading(UiText.get(lang, "exam_review"), UiText.get(lang, "tasks")) }
            items(result.details, key = { it.id }) { detail ->
                JavadaCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(13.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Text(if (detail.correct) "✓" else "×", color = if (detail.correct) palette.greenDeep else palette.redText, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(detail.id, color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            Text(if (detail.correct) detail.explanation.ifBlank { UiText.get(lang, "correct") } else detail.error, color = palette.mutedStrong, fontSize = 10.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
            item { JavadaButton(UiText.get(lang, "back_module"), { viewModel.navigateTo(AppScreen.ModuleOverview(module.id), pushCurrent = false) }, modifier = Modifier.fillMaxWidth(), trailing = "›") }
        }
        return
    }

    val index = state.selectedTaskIndex.coerceIn(0, exam.tasks.lastIndex)
    val task = exam.tasks[index]
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 17.dp, top = 17.dp, end = 17.dp, bottom = 25.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            JavadaCard(modifier = Modifier.fillMaxWidth(), containerColor = palette.greenSoft.copy(alpha = if (palette.isDark) .55f else .8f)) {
                Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Eyebrow(UiText.get(lang, "module", String.format(Locale.ROOT, "%02d", module.id)) + " · " + UiText.get(lang, "mini_exam"))
                    Text(exam.title, color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(UiText.get(lang, "exam_description", exam.passingPercent) + " · ${exam.tasks.size} · +${exam.xpBonus} XP", color = palette.mutedStrong, fontSize = 10.sp, lineHeight = 15.sp)
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                exam.tasks.forEachIndexed { taskIndex, entry ->
                    val answered = state.examAnswers.containsKey(entry.id)
                    FilterChip(
                        selected = taskIndex == index,
                        onClick = { viewModel.selectTaskIndex(taskIndex) },
                        label = { Text(if (answered) "✓ ${taskIndex + 1}" else "${taskIndex + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }
        item {
            JavadaCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(UiText.get(lang, "question_of", index + 1, exam.tasks.size), color = palette.muted, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${task.difficulty ?: ""}".uppercase(), color = palette.greenDeep, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    ProgressMeter((index + 1f) / exam.tasks.size.coerceAtLeast(1))
                    Text(task.prompt, color = palette.text, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
                    TaskInput(
                        task = task,
                        lang = lang,
                        initialCode = viewModel.getDraftOrStarter(task),
                        initialAnswer = state.examAnswers[task.id],
                        isExamMode = true,
            onAnswerChanged = { viewModel.recordExamAnswer(task, it) },
            onDraftChanged = { code -> viewModel.saveDraft(task.id, code) },
            onSubmit = { viewModel.recordExamAnswer(task, it) }
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                OutlineJavadaButton(UiText.get(lang, "back"), { viewModel.selectTaskIndex((index - 1).coerceAtLeast(0)) }, modifier = Modifier.weight(1f), enabled = index > 0)
                JavadaButton(
                    text = if (index < exam.tasks.lastIndex) UiText.get(lang, "continue") else UiText.get(lang, "submit_exam"),
                    onClick = {
                        if (index < exam.tasks.lastIndex) viewModel.selectTaskIndex(index + 1)
                        else viewModel.submitMiniExam(module, exam)
                    },
                    modifier = Modifier.weight(1f),
                    trailing = if (index < exam.tasks.lastIndex) "›" else "✓"
                )
            }
            Text(UiText.get(lang, "exam_review"), color = palette.muted, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
fun LabScreen(lab: CourseTask, state: JavadaUiState, viewModel: JavadaViewModel) {
    val lang = state.lang
    val solved = lab.id in state.profile.state.completedLabs
    Column(modifier = Modifier.fillMaxSize()) {
        SessionProgressHeader(
            title = UiText.get(lang, "career"),
            position = 1,
            total = 1,
            label = lab.title,
            progress = if (solved) 1f else 0f
        )
        TaskSessionContent(listOf(lab), null, lab.title, "lab", state, viewModel)
    }
}

@Composable
fun SessionResultScreen(summary: SessionCompletionSummary, state: JavadaUiState, viewModel: JavadaViewModel) {
    val lang = state.lang
    val palette = LocalJavadaColors.current
    val module = state.catalog.modules.firstOrNull { it.id == summary.moduleId }
    val time = String.format(Locale.ROOT, "%02d:%02d", summary.seconds / 60, summary.seconds % 60)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 25.dp, end = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(if (summary.passed) "✦" else "↻", color = if (summary.passed) palette.greenDeep else palette.yellowText, fontSize = 54.sp, fontWeight = FontWeight.Black)
            Eyebrow(if (summary.passed) UiText.get(lang, "result_passed") else UiText.get(lang, "result_retry"))
            Text(summary.title, color = palette.text, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(UiText.get(lang, "result"), color = palette.mutedStrong, fontSize = 11.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ResultStat("${summary.earnedXp}", UiText.get(lang, "earned_xp"), "⚡", Modifier.weight(1f))
                ResultStat("${summary.accuracy}%", UiText.get(lang, "accuracy"), "◎", Modifier.weight(1f))
                ResultStat(time, UiText.get(lang, "time_spent"), "◷", Modifier.weight(1f))
            }
        }
        item {
            JavadaCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(UiText.get(lang, "correct_first"), color = palette.mutedStrong, fontSize = 10.sp)
                        Text("${summary.correctFirstTry} / ${summary.total}", color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    ProgressMeter(summary.accuracy / 100f)
                    if (summary.mode == "review") Text(UiText.get(lang, "review_description"), color = palette.muted, fontSize = 10.sp, lineHeight = 15.sp)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                JavadaButton(
                    text = UiText.get(lang, "go_next"),
                    onClick = { viewModel.navigateTo(summary.nextScreen) },
                    modifier = Modifier.fillMaxWidth(),
                    trailing = "›"
                )
                if (summary.retryScreen != null) {
                    OutlineJavadaButton(UiText.get(lang, "retry_boss"), { viewModel.navigateTo(summary.retryScreen) }, modifier = Modifier.fillMaxWidth())
                }
                if (module != null) {
                    OutlineJavadaButton(UiText.get(lang, "to_module"), { viewModel.navigateTo(AppScreen.ModuleOverview(module.id)) }, modifier = Modifier.fillMaxWidth())
                } else {
                    val destination = if (summary.mode == "review") RootTab.REVIEW else RootTab.CAREER
                    val label = UiText.get(lang, if (summary.mode == "review") "review" else "career")
                    OutlineJavadaButton(label, { viewModel.selectRootTab(destination) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ResultStat(value: String, label: String, glyph: String, modifier: Modifier = Modifier) {
    val palette = LocalJavadaColors.current
    JavadaCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(glyph, color = palette.greenDeep, fontSize = 18.sp)
            Text(value, color = palette.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(label, color = palette.muted, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    val palette = LocalJavadaColors.current
    Column(modifier = Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("✦", color = palette.greenDeep, fontSize = 38.sp)
        Text(title, color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(description, color = palette.mutedStrong, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

private fun taskTypeLabel(type: String, lang: String): String = UiText.get(lang, when (type) {
    "choice" -> "task_choice"
    "fill" -> "task_fill"
    "matching" -> "task_matching"
    "parsons" -> "task_parsons"
    "copy" -> "task_copy"
    else -> "task_code"
})

private fun taskTypeGlyph(type: String): String = when (type) {
    "choice" -> "◉"
    "fill" -> "[ ]"
    "matching" -> "↔"
    "parsons" -> "≡"
    "copy" -> "{ }"
    else -> "</>"
}

private fun taskXp(task: CourseTask): Int = when (task.difficulty) {
    "easy" -> 10
    "medium" -> 20
    "hard" -> 35
    "hardcore" -> 90
    else -> when (task.type) { "theory" -> 5; "choice" -> 10; "fill" -> 15; "matching" -> 20; "parsons" -> 25; "copy" -> 8; else -> 50 }
}

@Composable
fun CodeSnippetBox(code: String, label: String = "") {
    val palette = LocalJavadaColors.current
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.codeBg)) {
        if (label.isNotBlank()) {
            Text(label.uppercase(), modifier = Modifier.fillMaxWidth().background(palette.codeHeaderBg).padding(horizontal = 11.dp, vertical = 8.dp), color = palette.codeMuted, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .65.sp)
        }
        Text(code, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), color = palette.codeText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 16.sp)
    }
}
