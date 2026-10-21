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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.javada.android.ui.AppScreen
import io.javada.android.ui.CareerScreen
import io.javada.android.ui.DashboardScreen
import io.javada.android.ui.JavadaViewModel
import io.javada.android.ui.LabScreen
import io.javada.android.ui.LessonScreen
import io.javada.android.ui.MiniExamScreen
import io.javada.android.ui.ModuleOverviewScreen
import io.javada.android.ui.PracticeScreen
import io.javada.android.ui.ReviewHubScreen
import io.javada.android.ui.ReviewSessionScreen
import io.javada.android.ui.RootTab
import io.javada.android.ui.SessionResultScreen
import io.javada.android.ui.SettingsScreen
import io.javada.android.ui.UiText
import io.javada.android.ui.theme.JavadaTheme
import io.javada.android.ui.theme.LocalJavadaColors
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    private val viewModel: JavadaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsState()
            JavadaTheme(darkTheme = state.profile.state.theme == "dark") {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    JavadaAppRoot(state = state, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun JavadaAppRoot(state: io.javada.android.ui.JavadaUiState, viewModel: JavadaViewModel) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    viewModel.importProgressJsonString(stream.readBytes().toString(StandardCharsets.UTF_8))
                } ?: error("Cannot open progress.json")
            }.onFailure { viewModel.importProgressJsonString("not-json") }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(viewModel.exportProgressJsonString().toByteArray(StandardCharsets.UTF_8))
                } ?: error("Cannot write progress.json")
            }
        }
    }
    val nested = state.screen !is AppScreen.Root || state.tabStacks[state.activeTab].orEmpty().size > 1
    BackHandler(enabled = nested || state.activeTab != RootTab.DASHBOARD) {
        if (!viewModel.navigateBack()) (context as? ComponentActivity)?.finish()
    }

    Scaffold(
        containerColor = LocalJavadaColors.current.bg,
        topBar = {
            JavadaTopBar(
                state = state,
                viewModel = viewModel,
                onBack = { viewModel.navigateBack() }
            )
        },
        bottomBar = {
            JavadaBottomNavigation(state = state, viewModel = viewModel)
        }
    ) { insets ->
        Column(modifier = Modifier.fillMaxSize().padding(insets)) {
            state.syncBannerMessage?.let { message ->
                SyncBanner(message = message, lang = state.lang, onDismiss = viewModel::dismissSyncBanner)
            }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                when (val screen = state.screen) {
                    is AppScreen.Root -> when (screen.tab) {
                        RootTab.DASHBOARD -> DashboardScreen(
                            state = state,
                            viewModel = viewModel,
                            onOpenModule = { viewModel.navigateTo(AppScreen.ModuleOverview(it)) },
                            onReview = { viewModel.selectRootTab(RootTab.REVIEW) },
                            onCareer = { viewModel.selectRootTab(RootTab.CAREER) }
                        )
                        RootTab.REVIEW -> ReviewHubScreen(state, viewModel)
                        RootTab.CAREER -> CareerScreen(state, viewModel)
                        RootTab.SETTINGS -> SettingsScreen(
                            state = state,
                            viewModel = viewModel,
                            onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            onExport = { exportLauncher.launch("progress.json") }
                        )
                    }
                    is AppScreen.ModuleOverview -> state.catalog.modules.firstOrNull { it.id == screen.moduleId }?.let {
                        ModuleOverviewScreen(it, state, viewModel)
                    }
                    is AppScreen.LessonView -> {
                        val module = state.catalog.modules.firstOrNull { it.id == screen.moduleId }
                        val lesson = module?.lessons?.firstOrNull { it.id == screen.lessonId }
                        if (module != null && lesson != null) LessonScreen(lesson, module, state, viewModel)
                    }
                    is AppScreen.PracticeView -> state.catalog.modules.firstOrNull { it.id == screen.moduleId }?.let {
                        PracticeScreen(it, screen.isBoss, state, viewModel)
                    }
                    is AppScreen.MiniExamView -> {
                        val module = state.catalog.modules.firstOrNull { it.id == screen.moduleId }
                        val exam = module?.exam
                        if (module != null && exam != null) MiniExamScreen(module, exam, state, viewModel)
                    }
                    is AppScreen.ReviewSession -> ReviewSessionScreen(state, screen.tasks, viewModel)
                    is AppScreen.LabView -> state.catalog.labs.firstOrNull { it.id == screen.labId }?.let {
                        LabScreen(it, state, viewModel)
                    }
                    is AppScreen.SessionResultView -> SessionResultScreen(screen.summary, state, viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JavadaTopBar(
    state: io.javada.android.ui.JavadaUiState,
    viewModel: JavadaViewModel,
    onBack: () -> Unit
) {
    val palette = LocalJavadaColors.current
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val root = state.screen is AppScreen.Root
    val pageTitle = when (val screen = state.screen) {
        is AppScreen.Root -> UiText.get(state.lang, when (screen.tab) {
            RootTab.DASHBOARD -> "dashboard"
            RootTab.REVIEW -> "review"
            RootTab.CAREER -> "career"
            RootTab.SETTINGS -> "settings"
        })
        is AppScreen.ModuleOverview -> state.catalog.modules.firstOrNull { it.id == screen.moduleId }?.title ?: UiText.get(state.lang, "course_map")
        is AppScreen.LessonView -> state.catalog.modules.firstOrNull { it.id == screen.moduleId }?.lessons?.firstOrNull { it.id == screen.lessonId }?.title ?: UiText.get(state.lang, "lesson")
        is AppScreen.PracticeView -> UiText.get(state.lang, if (screen.isBoss) "stop_lesson" else "final_practice")
        is AppScreen.MiniExamView -> UiText.get(state.lang, "mini_exam")
        is AppScreen.ReviewSession -> UiText.get(state.lang, "review_title")
        is AppScreen.LabView -> state.catalog.labs.firstOrNull { it.id == screen.labId }?.title ?: UiText.get(state.lang, "labs")
        is AppScreen.SessionResultView -> UiText.get(state.lang, "result")
    }
    val level = state.profile.state.xp / 250 + 1
    val xpRemainder = state.profile.state.xp % 250

    TopAppBar(
        navigationIcon = {
            if (!root) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiText.get(state.lang, "back"), tint = palette.mutedStrong)
                }
            }
        },
        title = {
            if (root) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    LogoMark()
                    Column {
                        Text("javada.", color = palette.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, lineHeight = 21.sp)
                        Text(UiText.get(state.lang, "tagline"), color = palette.muted, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, maxLines = 1)
                    }
                }
            } else {
                Column {
                    Text(UiText.get(state.lang, "my_path"), color = palette.muted, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                    Text(pageTitle, color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LanguageMenu(lang = state.lang, onSelect = viewModel::setLanguage)
                StatPill(glyph = "⚡", value = state.profile.state.xp.toString(), detail = if (wide) UiText.get(state.lang, "xp_level", level) else null, color = palette.xpGold)
                if (wide) {
                    StatPill(glyph = "🔥", value = state.profile.state.streak.toString(), detail = UiText.get(state.lang, "streak_days"), color = palette.streakOrange)
                    IconButton(onClick = { viewModel.selectRootTab(RootTab.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = UiText.get(state.lang, "settings"), tint = palette.mutedStrong)
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = palette.surface,
            titleContentColor = palette.text,
            navigationIconContentColor = palette.mutedStrong,
            actionIconContentColor = palette.mutedStrong
        )
    )
}

@Composable
private fun LogoMark() {
    Box(
        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF6FE02C), Color(0xFF45AC07)))),
        contentAlignment = Alignment.Center
    ) {
        Text("{·}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
private fun StatPill(glyph: String, value: String, detail: String?, color: Color) {
    val palette = LocalJavadaColors.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.line),
        modifier = Modifier.padding(horizontal = 1.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(glyph, fontSize = 13.sp)
            Text(value, color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            if (detail != null) Text(detail, color = palette.muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun LanguageMenu(lang: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val palette = LocalJavadaColors.current
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 4.dp)) {
            Text("🌐 ${lang.uppercase()}", color = palette.mutedStrong, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("ru" to "Русский", "en" to "English", "es" to "Español", "de" to "Deutsch", "zh" to "中文").forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text("${code.uppercase()}  ·  $label", fontWeight = if (lang == code) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { expanded = false; onSelect(code) }
                )
            }
        }
    }
}

@Composable
private fun JavadaBottomNavigation(state: io.javada.android.ui.JavadaUiState, viewModel: JavadaViewModel) {
    val palette = LocalJavadaColors.current
    val dueCount = remember(state.profile.revision, state.lang) { viewModel.dueReviewTasks().size }
    val selectedTab = when (val screen = state.screen) {
        is AppScreen.Root -> screen.tab
        is AppScreen.ReviewSession -> RootTab.REVIEW
        is AppScreen.LabView -> RootTab.CAREER
        else -> state.activeTab
    }
    NavigationBar(
        containerColor = palette.surface,
        contentColor = palette.mutedStrong,
        tonalElevation = 0.dp
    ) {
        val tabs = listOf(
            Triple(RootTab.DASHBOARD, Icons.Filled.Map, "dashboard"),
            Triple(RootTab.REVIEW, Icons.Filled.Refresh, "review"),
            Triple(RootTab.CAREER, Icons.Filled.Flag, "career"),
            Triple(RootTab.SETTINGS, Icons.Filled.Settings, "settings")
        )
        tabs.forEach { (tab, image, labelKey) ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { viewModel.selectRootTab(tab) },
                icon = {
                    if (tab == RootTab.REVIEW && dueCount > 0) {
                        BadgedBox(badge = { Badge { Text(if (dueCount > 9) "9+" else dueCount.toString()) } }) {
                            Icon(image, contentDescription = null)
                        }
                    } else {
                        Icon(image, contentDescription = null)
                    }
                },
                label = { Text(UiText.get(state.lang, labelKey), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = palette.greenDeep,
                    selectedTextColor = palette.greenDeep,
                    indicatorColor = palette.greenSoft,
                    unselectedIconColor = palette.muted,
                    unselectedTextColor = palette.mutedStrong
                )
            )
        }
    }
}

@Composable
private fun SyncBanner(message: String, lang: String, onDismiss: () -> Unit) {
    val palette = LocalJavadaColors.current
    Surface(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        shape = RoundedCornerShape(11.dp),
        color = palette.greenSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.green.copy(alpha = .25f))
    ) {
        Row(modifier = Modifier.padding(start = 12.dp, end = 7.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), color = palette.greenDeep, fontSize = 10.sp, lineHeight = 15.sp)
            TextButton(onClick = onDismiss) { Text(UiText.get(lang, "close"), color = palette.greenDeep, fontSize = 10.sp) }
        }
    }
}
