package io.javada.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.javada.android.data.CourseModule
import io.javada.android.data.CourseTask
import io.javada.android.data.ItemStatus
import io.javada.android.ui.theme.LocalJavadaColors
import java.util.Locale

@Composable
fun DashboardScreen(
    state: JavadaUiState,
    viewModel: JavadaViewModel,
    onOpenModule: (Int) -> Unit,
    onReview: () -> Unit,
    onCareer: () -> Unit
) {
    val lang = state.lang
    val palette = LocalJavadaColors.current
    val modules = remember(state.catalog.modules, state.profile.revision) {
        state.catalog.modules.map { it to viewModel.moduleStatus(it) }
    }
    val shownModules = if (state.moduleFilter == ModuleFilter.READY) {
        modules.filter { it.second != ItemStatus.LOCKED }
    } else modules
    val completedCount = modules.count { it.second == ItemStatus.COMPLETED }
    val currentModule = modules.firstOrNull { it.second == ItemStatus.AVAILABLE }?.first
    val due = remember(state.profile.revision, lang) { viewModel.dueReviewTasks().size }
    val studiedToday = state.profile.state.lastActiveDate == java.time.LocalDate.now().toString()
    val level = (state.profile.state.xp / 250) + 1
    val xpIntoLevel = state.profile.state.xp % 250

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 17.dp, top = 23.dp, end = 17.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { DashboardHeader(lang) }
        item {
            DashboardHero(
                lang = lang,
                module = currentModule,
                completedCount = completedCount,
                moduleCount = modules.size,
                onContinue = viewModel::continueLearning
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardWidget(
                    icon = "↻",
                    iconColor = palette.reviewPurple,
                    iconBg = palette.reviewPurpleSoft,
                    label = UiText.get(lang, "review_widget_label"),
                    title = if (due > 0) UiText.get(lang, "review_ready", due) else UiText.get(lang, "review_none"),
                    description = if (due > 0) UiText.get(lang, "review_widget_description") else UiText.get(lang, "review_widget_empty"),
                    actionLabel = UiText.get(lang, "start_review"),
                    onAction = onReview,
                    actionEnabled = due > 0
                )
                DashboardWidget(
                    icon = "◎",
                    iconColor = palette.xpGold,
                    iconBg = palette.yellowSoft,
                    label = UiText.get(lang, "daily_goal"),
                    title = UiText.get(lang, if (studiedToday) "today_done" else "today_not_done"),
                    description = UiText.get(lang, if (studiedToday) "streak_keeps" else "solve_any_task"),
                    actionLabel = null,
                    onAction = {},
                    actionEnabled = false,
                    trailing = if (studiedToday) "✓" else "◷"
                )
            }
        }
        item { CareerTeaser(lang, onClick = onCareer) }
        item {
            SectionHeading(
                overline = UiText.get(lang, "program"),
                title = UiText.get(lang, "course_map"),
                subtitle = UiText.get(lang, "dashboard_subtitle"),
                trailing = {
                    Text(
                        UiText.get(lang, "all_modules"),
                        color = palette.greenDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(palette.greenSoft)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(
                    selected = state.moduleFilter == ModuleFilter.ALL,
                    onClick = { viewModel.setModuleFilter(ModuleFilter.ALL) },
                    label = { Text(UiText.get(lang, "filter_all"), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = state.moduleFilter == ModuleFilter.READY,
                    onClick = { viewModel.setModuleFilter(ModuleFilter.READY) },
                    label = { Text(UiText.get(lang, "available_modules"), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }
        if (shownModules.isEmpty()) {
            item {
                JavadaCard(modifier = Modifier.fillMaxWidth()) {
                    Text(UiText.get(lang, "empty"), modifier = Modifier.padding(20.dp), color = palette.mutedStrong)
                }
            }
        } else {
            items(shownModules, key = { it.first.id }) { (module, status) ->
                ModuleMapCard(
                    module = module,
                    status = status,
                    lang = lang,
                    progress = viewModel.moduleProgress(module),
                    onClick = { onOpenModule(module.id) }
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("●", color = palette.green, fontSize = 10.sp)
                Spacer(Modifier.width(7.dp))
                Text(UiText.get(lang, "offline_status"), color = palette.muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun DashboardHeader(lang: String) {
    val parts = when (lang) {
        "en" -> Triple("Learning to code is ", "a journey.", "")
        "es" -> Triple("Aprender a programar es ", "un camino.", "")
        "de" -> Triple("Programmieren lernen ist ", "ein Weg.", "")
        "zh" -> Triple("学会编程，是", "一段旅程。", "")
        else -> Triple("Учиться писать код — это ", "путь.", "")
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Eyebrow(UiText.get(lang, "dashboard_kicker"))
        RichTitle(parts.first, parts.second, parts.third, fontSize = 27)
        Text(UiText.get(lang, "dashboard_subtitle"), color = LocalJavadaColors.current.mutedStrong, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun DashboardHero(
    lang: String,
    module: CourseModule?,
    completedCount: Int,
    moduleCount: Int,
    onContinue: () -> Unit
) {
    val palette = LocalJavadaColors.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(23.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF258E33), Color(0xFF43AF2C), Color(0xFF78CE35))))
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = .13f), radius = size.maxDimension * .43f, center = Offset(size.width * 1.02f, size.height * .82f), style = Stroke(width = 1.dp.toPx()))
            drawCircle(Color.White.copy(alpha = .11f), radius = size.maxDimension * .30f, center = Offset(size.width * .93f, size.height * .93f), style = Stroke(width = 1.dp.toPx()))
            for (x in 0..12) for (y in 0..9) {
                drawCircle(Color.White.copy(alpha = .12f), radius = 1.dp.toPx(), center = Offset(x * 22.dp.toPx(), y * 22.dp.toPx()))
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 23.dp, vertical = 25.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = .14f),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .33f))
            ) {
                Text(
                    UiText.get(lang, "continue_kicker"),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.15.sp
                )
            }
            Text(
                UiText.get(lang, if (module == null) "course_finished" else "continue_title"),
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.45).sp
            )
            if (module != null) {
                Text(
                    UiText.get(lang, "next_up", module.title),
                    color = Color.White.copy(alpha = .86f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
            JavadaButton(
                text = UiText.get(lang, "continue_learning"),
                onClick = onContinue,
                enabled = module != null,
                white = true,
                leading = "▶",
                trailing = "›",
                modifier = Modifier.fillMaxWidth(.88f)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("✓", color = Color(0xFFE5FFAD), fontWeight = FontWeight.Black)
                Text(
                    UiText.get(lang, "modules_completed", "$completedCount"),
                    color = Color.White.copy(alpha = .83f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DashboardWidget(
    icon: String,
    iconColor: Color,
    iconBg: Color,
    label: String,
    title: String,
    description: String,
    actionLabel: String?,
    onAction: () -> Unit,
    actionEnabled: Boolean,
    trailing: String? = null
) {
    val palette = LocalJavadaColors.current
    JavadaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(49.dp).clip(RoundedCornerShape(14.dp)).background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = iconColor, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, color = palette.muted, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Text(title, color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(description, color = palette.mutedStrong, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (actionLabel != null) {
                androidx.compose.material3.IconButton(onClick = onAction, enabled = actionEnabled, modifier = Modifier.size(35.dp)) {
                    Text("›", color = if (actionEnabled) palette.greenDeep else palette.muted, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
            } else if (trailing != null) {
                Box(
                    Modifier.size(33.dp).clip(RoundedCornerShape(9.dp)).background(if (trailing == "✓") palette.greenSoft else palette.surfaceSoft).border(1.dp, palette.line, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(trailing, color = if (trailing == "✓") palette.greenDeep else palette.muted, fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun CareerTeaser(lang: String, onClick: () -> Unit) {
    val palette = LocalJavadaColors.current
    JavadaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Eyebrow(UiText.get(lang, "junior_teaser_label"))
            Text(UiText.get(lang, "junior_teaser_title"), color = palette.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 23.sp)
            Text(UiText.get(lang, "junior_teaser_description"), color = palette.mutedStrong, fontSize = 11.sp, lineHeight = 17.sp)
            OutlineJavadaButton(UiText.get(lang, "open_path"), onClick, trailing = "›")
        }
    }
}

@Composable
private fun ModuleMapCard(
    module: CourseModule,
    status: ItemStatus,
    lang: String,
    progress: Triple<Int, Int, Int>,
    onClick: () -> Unit
) {
    val palette = LocalJavadaColors.current
    val completed = status == ItemStatus.COMPLETED
    val fraction = progress.first.toFloat() / progress.second.coerceAtLeast(1)
    JavadaCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = status != ItemStatus.LOCKED,
        containerColor = if (completed) palette.greenSoft.copy(alpha = if (palette.isDark) .32f else .6f) else null
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Eyebrow(UiText.get(lang, "module", String.format(Locale.ROOT, "%02d", module.id)))
                StatusBadge(status, lang)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModuleSymbol(module.id)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(module.title, color = if (status == ItemStatus.LOCKED) palette.mutedStrong else palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(module.subtitle, color = palette.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (status == ItemStatus.LOCKED) androidx.compose.material3.Icon(Icons.Filled.Lock, contentDescription = null, tint = palette.muted, modifier = Modifier.size(17.dp))
                else androidx.compose.material3.Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = palette.greenDeep, modifier = Modifier.size(18.dp))
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(UiText.get(lang, "progress_steps", progress.first, progress.second), color = palette.mutedStrong, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${(fraction * 100).toInt()}%", color = if (status == ItemStatus.LOCKED) palette.muted else palette.greenDeep, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
            ProgressMeter(fraction)
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    buildString {
                        append(UiText.get(lang, "lessons_count", module.lessons.size))
                        append(" · ")
                        append(if (lang == "ru") "практика" else when (lang) { "es" -> "práctica"; "de" -> "Praxis"; "zh" -> "练习"; else -> "practice" })
                        if (module.bossTasks.isNotEmpty()) append(" ${UiText.get(lang, "boss_exam")}")
                    },
                    color = palette.muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(if (status == ItemStatus.LOCKED) "🔒" else "›", color = palette.greenDeep, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ModuleOverviewScreen(module: CourseModule, state: JavadaUiState, viewModel: JavadaViewModel) {
    val lang = state.lang
    val palette = LocalJavadaColors.current
    val (done, total, lessonsDone) = viewModel.moduleProgress(module)
    val fraction = done.toFloat() / total
    val next = viewModel.nextStepScreenForModule(module)
    val nextText = when (next) {
        is AppScreen.LessonView -> UiText.get(lang, "lesson") + ": " + (module.lessons.find { it.id == next.lessonId }?.title ?: "")
        is AppScreen.PracticeView -> UiText.get(lang, if (next.isBoss) "stop_lesson" else "final_practice")
        is AppScreen.MiniExamView -> UiText.get(lang, "mini_exam")
        else -> UiText.get(lang, "completed")
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 17.dp, top = 18.dp, end = 17.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = { viewModel.selectRootTab(RootTab.DASHBOARD) }, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 5.dp)) {
                Text("‹  ${UiText.get(lang, "back_to_map")}", color = palette.mutedStrong, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            ModuleHero(module, lang, done, total, fraction, nextText) {
                if (next !is AppScreen.ModuleOverview) viewModel.navigateTo(next)
            }
        }
        item {
            SectionHeading(
                overline = UiText.get(lang, "learning_path"),
                title = module.title,
                subtitle = UiText.get(lang, "completed_lessons", lessonsDone, module.lessons.size)
            )
        }
        items(module.lessons, key = { it.id }) { lesson ->
            val index = module.lessons.indexOf(lesson)
            val doneLesson = lesson.id in state.profile.state.completedLessons
            val open = viewModel.canOpenLesson(module, index)
            PathStepCard(
                number = "${index + 1}".padStart(2, '0'),
                title = lesson.title,
                description = UiText.get(lang, "lesson_meta", lesson.durationMinutes, lesson.tasks.size),
                status = when { doneLesson -> ItemStatus.COMPLETED; open -> ItemStatus.AVAILABLE; else -> ItemStatus.LOCKED },
                lang = lang,
                onClick = { viewModel.navigateTo(AppScreen.LessonView(module.id, lesson.id)) },
                enabled = open,
                lockedDescription = if (!open) UiText.get(lang, "locked_explanation") else null
            )
        }
        if (module.bossTasks.isNotEmpty()) {
            item {
                val passed = state.profile.state.bossScores[module.id.toString()]?.passed == true
                PathStepCard(
                    number = "★",
                    title = UiText.get(lang, "stop_lesson"),
                    description = UiText.get(lang, "stop_description"),
                    status = when { passed -> ItemStatus.COMPLETED; viewModel.canOpenBoss(module) -> ItemStatus.AVAILABLE; else -> ItemStatus.LOCKED },
                    lang = lang,
                    onClick = { viewModel.navigateTo(AppScreen.PracticeView(module.id, isBoss = true)) },
                    enabled = viewModel.canOpenBoss(module),
                    lockedDescription = if (viewModel.canOpenBoss(module)) null else UiText.get(lang, "complete_lesson_first")
                )
            }
        }
        item {
            val passed = module.id in state.profile.state.completedPractices
            PathStepCard(
                number = "↗",
                title = UiText.get(lang, "final_practice"),
                description = UiText.get(lang, "practice_description") + " · ${module.practiceTasks.size}",
                status = when { passed -> ItemStatus.COMPLETED; viewModel.canOpenPractice(module) -> ItemStatus.AVAILABLE; else -> ItemStatus.LOCKED },
                lang = lang,
                onClick = { viewModel.navigateTo(AppScreen.PracticeView(module.id)) },
                enabled = viewModel.canOpenPractice(module),
                lockedDescription = if (viewModel.canOpenPractice(module)) null else UiText.get(lang, "complete_lesson_first")
            )
        }
        module.exam?.let { exam ->
            item {
                val score = state.profile.state.examScores[module.id.toString()]
                PathStepCard(
                    number = "🏆",
                    title = UiText.get(lang, "mini_exam"),
                    description = UiText.get(lang, "exam_description", exam.passingPercent) + " · +${exam.xpBonus} XP",
                    status = when { score?.passed == true -> ItemStatus.COMPLETED; viewModel.canOpenExam(module) -> ItemStatus.AVAILABLE; else -> ItemStatus.LOCKED },
                    lang = lang,
                    onClick = { viewModel.navigateTo(AppScreen.MiniExamView(module.id)) },
                    enabled = viewModel.canOpenExam(module),
                    lockedDescription = if (viewModel.canOpenExam(module)) null else UiText.get(lang, "locked_explanation")
                )
            }
        }
    }
}

@Composable
private fun ModuleHero(
    module: CourseModule,
    lang: String,
    done: Int,
    total: Int,
    fraction: Float,
    nextText: String,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF298C35), Color(0xFF57B942), Color(0xFF7CCE54))))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(UiText.get(lang, "module_banner_tag", String.format(Locale.ROOT, "%02d", module.id)), color = Color.White.copy(alpha = .85f), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
            Text(module.title, color = Color.White, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(module.subtitle, color = Color.White.copy(alpha = .86f), fontSize = 11.sp, lineHeight = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.get(lang, "progress_steps", done, total), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${(fraction * 100).toInt()}%", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f))) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(7.dp).clip(CircleShape).background(Color.White))
            }
            if (done < total) {
                JavadaButton(UiText.get(lang, "continue_learning"), onContinue, white = true, trailing = "›", modifier = Modifier.fillMaxWidth(.8f))
            }
        }
    }
}

@Composable
private fun PathStepCard(
    number: String,
    title: String,
    description: String,
    status: ItemStatus,
    lang: String,
    onClick: () -> Unit,
    enabled: Boolean,
    lockedDescription: String?
) {
    val palette = LocalJavadaColors.current
    JavadaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, enabled = enabled, containerColor = if (status == ItemStatus.AVAILABLE) palette.surface else null) {
        Row(modifier = Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                modifier = Modifier.size(47.dp).clip(RoundedCornerShape(14.dp)).background(
                    when (status) { ItemStatus.COMPLETED -> palette.greenSoft; ItemStatus.AVAILABLE -> palette.green; ItemStatus.LOCKED -> palette.surfaceSoft }
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(number, color = if (status == ItemStatus.AVAILABLE) Color.White else palette.greenDeep, fontSize = if (number.length > 2) 10.sp else 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow(UiText.get(lang, when (status) { ItemStatus.COMPLETED -> "completed"; ItemStatus.AVAILABLE -> "available"; ItemStatus.LOCKED -> "locked" }))
                Text(title, color = if (status == ItemStatus.LOCKED) palette.mutedStrong else palette.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(description, color = palette.muted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (lockedDescription != null) Text(lockedDescription, color = palette.muted, fontSize = 9.sp, lineHeight = 13.sp, maxLines = 2)
            }
            when (status) {
                ItemStatus.COMPLETED -> androidx.compose.material3.Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = palette.greenDeep, modifier = Modifier.size(20.dp))
                ItemStatus.AVAILABLE -> androidx.compose.material3.Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = palette.greenDeep, modifier = Modifier.size(21.dp))
                ItemStatus.LOCKED -> androidx.compose.material3.Icon(Icons.Filled.Lock, contentDescription = null, tint = palette.muted, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
fun ReviewHubScreen(state: JavadaUiState, viewModel: JavadaViewModel) {
    val lang = state.lang
    val due = remember(state.profile.revision, state.lang) { viewModel.dueReviewTasks() }
    val palette = LocalJavadaColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 17.dp, top = 24.dp, end = 17.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { PageHeading(overline = UiText.get(lang, "review_widget_label"), title = UiText.get(lang, "review_title"), description = UiText.get(lang, "review_description")) }
        item {
            JavadaCard(modifier = Modifier.fillMaxWidth(), containerColor = palette.reviewPurpleSoft.copy(alpha = if (palette.isDark) .55f else .65f)) {
                Column(modifier = Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("↻", color = palette.reviewPurple, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("${due.size}", color = palette.text, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                        Text(UiText.get(lang, "review_question_count", due.size), color = palette.mutedStrong, fontSize = 11.sp)
                    }
                    ProgressMeter(if (due.isEmpty()) 0f else 1f, height = 8.dp)
                    JavadaButton(UiText.get(lang, "start_review"), viewModel::startSpacedReviewSession, enabled = due.isNotEmpty(), leading = "↻", trailing = "›", modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (due.isEmpty()) {
            item {
                JavadaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("✦", color = palette.greenDeep, fontSize = 32.sp)
                        Text(UiText.get(lang, "no_review"), color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text(UiText.get(lang, "no_review_description"), color = palette.mutedStrong, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
            }
        } else {
            item { SectionHeading(UiText.get(lang, "review_widget_label"), UiText.get(lang, "tasks"), UiText.get(lang, "review_description")) }
            items(due.take(12), key = { it.id }) { task -> ReviewTaskCard(task, lang) }
        }
    }
}

@Composable
private fun ReviewTaskCard(task: CourseTask, lang: String) {
    val palette = LocalJavadaColors.current
    JavadaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.title, color = palette.text, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            Text(task.prompt, color = palette.mutedStrong, fontSize = 10.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(taskTypeLabel(task.type, lang), color = palette.greenDeep, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .6.sp)
        }
    }
}

@Composable
fun CareerScreen(state: JavadaUiState, viewModel: JavadaViewModel) {
    val lang = state.lang
    val palette = LocalJavadaColors.current
    val labsDone = state.catalog.labs.count { it.id in state.profile.state.completedLabs }
    val checklist = listOf(
        "cli-verify" to when (lang) { "en" -> "CLI project: verification script passes"; "es" -> "Proyecto CLI: pasan las verificaciones"; "de" -> "CLI-Projekt: Prüfscripte laufen"; "zh" -> "CLI 项目：检查脚本通过"; else -> "CLI: python3 verify.py проходит" },
        "cli-own-tests" to when (lang) { "en" -> "CLI project: add edge-case tests"; "es" -> "CLI: añade pruebas de casos límite"; "de" -> "CLI: Grenzfalltests ergänzen"; "zh" -> "CLI：添加边界测试"; else -> "CLI: добавил(а) свои крайние случаи" },
        "cli-docs" to when (lang) { "en" -> "CLI project: clear README and examples"; "es" -> "CLI: README y ejemplos claros"; "de" -> "CLI: verständliche README und Beispiele"; "zh" -> "CLI：README 与示例清晰"; else -> "CLI: README с запуском и примерами" },
        "api-tests" to when (lang) { "en" -> "API project: PATCH/DELETE and tests pass"; "es" -> "API: PATCH/DELETE y pruebas correctas"; "de" -> "API: PATCH/DELETE und Tests bestehen"; "zh" -> "API：PATCH/DELETE 与测试通过"; else -> "API: PATCH/DELETE реализованы, тесты зелёные" },
        "api-docs" to when (lang) { "en" -> "API project: document HTTP and layers"; "es" -> "API: documenta HTTP y las capas"; "de" -> "API: HTTP und Schichten dokumentiert"; "zh" -> "API：记录 HTTP 契约与分层"; else -> "API: задокументированы HTTP и слои" }
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 17.dp, top = 24.dp, end = 17.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item { PageHeading(UiText.get(lang, "career"), UiText.get(lang, "career_title"), UiText.get(lang, "career_description")) }
        item {
            JavadaCard(modifier = Modifier.fillMaxWidth(), containerColor = palette.yellowSoft.copy(alpha = if (palette.isDark) .65f else 1f)) {
                Text(UiText.get(lang, "career_notice"), modifier = Modifier.padding(15.dp), color = palette.yellowText, fontSize = 10.sp, lineHeight = 16.sp)
            }
        }
        item { SectionHeading(UiText.get(lang, "career_stages"), UiText.get(lang, "career_title")) }
        item { CareerStageCard("01", UiText.get(lang, "stage_core"), UiText.get(lang, "stage_core_desc")) }
        item { CareerStageCard("02", UiText.get(lang, "stage_data"), UiText.get(lang, "stage_data_desc")) }
        item { CareerStageCard("03", UiText.get(lang, "stage_backend"), UiText.get(lang, "stage_backend_desc")) }
        item {
            SectionHeading(
                overline = UiText.get(lang, "career"),
                title = UiText.get(lang, "labs"),
                subtitle = UiText.get(lang, "labs_count", labsDone, state.catalog.labs.size)
            )
        }
        items(state.catalog.labs, key = { it.id }) { lab ->
            val complete = lab.id in state.profile.state.completedLabs
            CareerLabCard(lab, lang, complete) { viewModel.navigateTo(AppScreen.LabView(lab.id)) }
        }
        item { SectionHeading(UiText.get(lang, "career"), UiText.get(lang, "projects")) }
        item { PortfolioProjectCard("Task Tracker CLI", "JAVA 17 · FILES · TESTS", if (lang == "ru") "Трекер задач в консоли, сохранение данных и обработка ошибок." else "A command-line task tracker with persistence and robust error handling.") }
        item { PortfolioProjectCard(UiText.get(lang, "task_api"), "SPRING BOOT · REST · SQL", if (lang == "ru") "REST API с валидацией, базой данных и интеграционными тестами." else "A REST API with validation, a database and integration tests.") }
        item {
            SectionHeading(UiText.get(lang, "checklist"), UiText.get(lang, "checklist"), UiText.get(lang, "self_assessment"))
        }
        items(checklist, key = { it.first }) { (id, label) ->
            val checked = id in state.profile.state.careerChecklist
            JavadaCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleCareerChecklist(id) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { viewModel.toggleCareerChecklist(id) })
                    Text(label, color = palette.text, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun CareerStageCard(number: String, title: String, description: String) {
    val palette = LocalJavadaColors.current
    JavadaCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(palette.greenSoft), contentAlignment = Alignment.Center) {
                Text(number, color = palette.greenDeep, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Eyebrow(title)
                Text(description, color = palette.mutedStrong, fontSize = 10.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun CareerLabCard(lab: CourseTask, lang: String, complete: Boolean, onClick: () -> Unit) {
    val palette = LocalJavadaColors.current
    JavadaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, containerColor = if (complete) palette.greenSoft.copy(alpha = if (palette.isDark) .35f else .65f) else null) {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Eyebrow(UiText.get(lang, "lab_goal"))
                Text(UiText.get(lang, if (complete) "lab_solved" else "lab_not_solved"), color = if (complete) palette.greenDeep else palette.muted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(lab.title, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(lab.prompt.substringBefore("\n\n"), color = palette.mutedStrong, fontSize = 10.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            OutlineJavadaButton(UiText.get(lang, "solve_lab"), onClick, trailing = "›")
        }
    }
}

@Composable
private fun PortfolioProjectCard(title: String, tag: String, description: String) {
    val palette = LocalJavadaColors.current
    JavadaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Eyebrow(tag)
            Text(title, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(description, color = palette.mutedStrong, fontSize = 10.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
fun SettingsScreen(
    state: JavadaUiState,
    viewModel: JavadaViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    val lang = state.lang
    val palette = LocalJavadaColors.current
    var askReset by remember { mutableStateOf(false) }
    var reminderTime by remember(state.profile.state.reminderTime) { mutableStateOf(state.profile.state.reminderTime) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 17.dp, top = 24.dp, end = 17.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { PageHeading(UiText.get(lang, "settings"), UiText.get(lang, "settings_title"), UiText.get(lang, "theme_description")) }
        item {
            SettingsGroup(title = UiText.get(lang, "appearance")) {
                SettingsRow(
                    glyph = if (state.profile.state.theme == "dark") "☾" else "☀",
                    title = UiText.get(lang, if (state.profile.state.theme == "dark") "dark_theme" else "light_theme"),
                    description = UiText.get(lang, "theme_description"),
                    trailing = {
                        Switch(checked = state.profile.state.theme == "dark", onCheckedChange = { viewModel.toggleTheme() })
                    }
                )
                HorizontalDivider(color = palette.line)
                SettingsLanguageRow(lang = lang, onSelect = viewModel::setLanguage)
            }
        }
        item {
            SettingsGroup(title = UiText.get(lang, "progress_sync")) {
                Text(UiText.get(lang, "sync_description"), color = palette.mutedStrong, fontSize = 11.sp, lineHeight = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineJavadaButton(UiText.get(lang, "import_progress"), onImport, modifier = Modifier.weight(1f), leading = "⇧")
                    OutlineJavadaButton(UiText.get(lang, "export_progress"), onExport, modifier = Modifier.weight(1f), leading = "⇩")
                }
            }
        }
        item {
            JavadaCard(modifier = Modifier.fillMaxWidth(), containerColor = palette.greenSoft.copy(alpha = if (palette.isDark) .45f else .85f)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Eyebrow(UiText.get(lang, "offline"))
                    Text(UiText.get(lang, "offline_description"), color = palette.mutedStrong, fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
        }
        item {
            JavadaCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(UiText.get(lang, "reset_confirm"), color = palette.mutedStrong, fontSize = 11.sp, lineHeight = 16.sp)
                    OutlineJavadaButton(UiText.get(lang, "reset_progress"), onClick = { askReset = true }, trailing = "↺")
                }
            }
        }
    }
    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text(UiText.get(lang, "reset_progress"), fontWeight = FontWeight.ExtraBold) },
            text = { Text(UiText.get(lang, "reset_confirm")) },
            confirmButton = {
                TextButton(onClick = { askReset = false; viewModel.resetAllProgress() }) {
                    Text(UiText.get(lang, "reset"), color = palette.redText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { askReset = false }) { Text(UiText.get(lang, "cancel")) } }
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalJavadaColors.current
    JavadaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Eyebrow(title)
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    glyph: String,
    title: String,
    description: String,
    trailing: @Composable () -> Unit
) {
    val palette = LocalJavadaColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(palette.greenSoft), contentAlignment = Alignment.Center) {
            Text(glyph, color = palette.greenDeep, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = palette.text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text(description, color = palette.muted, fontSize = 9.sp, lineHeight = 13.sp)
        }
        trailing()
    }
}

@Composable
private fun SettingsLanguageRow(lang: String, onSelect: (String) -> Unit) {
    val palette = LocalJavadaColors.current
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(UiText.get(lang, "language"), color = palette.text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ru" to "RU", "en" to "EN", "es" to "ES", "de" to "DE", "zh" to "ZH").forEach { (code, label) ->
                FilterChip(
                    selected = lang == code,
                    onClick = { onSelect(code) },
                    label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                )
            }
        }
    }
}
