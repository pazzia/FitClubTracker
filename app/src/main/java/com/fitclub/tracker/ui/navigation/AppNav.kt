package com.fitclub.tracker.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import com.fitclub.tracker.domain.LocalWeightUnits
import com.fitclub.tracker.domain.WeightUnits
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitclub.tracker.AppContainer
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.ui.library.ExerciseDetailScreen
import com.fitclub.tracker.ui.library.LibraryGroupScreen
import com.fitclub.tracker.ui.anatomy.AtlasGroupScreen
import com.fitclub.tracker.ui.anatomy.AtlasHomeScreen
import com.fitclub.tracker.ui.anatomy.AtlasMuscleScreen
import com.fitclub.tracker.ui.library.LibraryHomeScreen
import com.fitclub.tracker.ui.placeholder.ComingSoonScreen
import com.fitclub.tracker.ui.dashboard.CalendarHomeScreen
import com.fitclub.tracker.ui.dashboard.TrainingPlannerScreen
import com.fitclub.tracker.ui.dashboard.ProgressScreen
import com.fitclub.tracker.ui.dashboard.ProfileScreen
import com.fitclub.tracker.ui.workout.WorkoutScreen
import com.fitclub.tracker.ui.workout.WorkoutResultScreen
import com.fitclub.tracker.ui.programs.ProgramEditorScreen
import com.fitclub.tracker.ui.programs.ProgramsScreen
import com.fitclub.tracker.ui.theme.Fc
import com.fitclub.tracker.ui.today.TodayScreen
import com.fitclub.tracker.ui.components.LineIcon
import com.fitclub.tracker.ui.components.PackIcons
import com.fitclub.tracker.ui.components.ambientGlow
import com.fitclub.tracker.ui.components.glow
import androidx.compose.ui.graphics.Color

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as FitApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app.container) } })
}

object Routes {
    const val TODAY = "today"
    const val CALENDAR = "calendar?day={day}"
    const val PROGRAMS = "programs"
    const val PROGRAM = "program/{id}?add={add}"
    const val LIBRARY = "library"
    const val GROUP = "library/{group}"
    const val EXERCISE = "exercise/{id}?training={training}"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"
    const val MEASURE = "measure?metric={metric}"
    const val BODY = "body"
    fun measure(metric: String? = null) = "measure?metric=${metric.orEmpty()}"
    const val WORKOUT = "workout/{schedule}"
    const val PICK_PROGRAM = "plan-programs/{epochDay}"
    const val PLANNER = "plan/{epochDay}?schedule={schedule}"
    const val RESULT = "result/{session}?celebrate={celebrate}"
    fun program(id: Long, add: String = "") = "program/$id?add=$add"
    fun group(g: MuscleGroup) = "library/${g.name}"
    fun exercise(id: String) = "exercise/$id"
    fun workout(schedule: Long) = "workout/$schedule"
    fun plan(epochDay: Long, schedule: Long = 0L) = "plan/$epochDay?schedule=$schedule"
    fun calendar(epochDay: Long = -1L) = "calendar?day=$epochDay"
    const val ATLAS = "anatomy"
    const val ATLAS_GROUP = "anatomy/group/{groupId}"
    const val ATLAS_MUSCLE = "anatomy/muscle/{muscleId}"
    fun atlasGroup(id: String) = "anatomy/group/$id"
    fun atlasMuscle(id: String) = "anatomy/muscle/$id"
    fun result(session: Long, celebrate: Boolean = false) = "result/$session?celebrate=$celebrate"
}

private data class Tab(val route: String, val title: String, val icon: String, val prefixes: List<String>)

private val tabs = listOf(
    Tab(Routes.TODAY, "Сегодня", PackIcons.HOME, listOf("today", "calendar")),
    Tab(Routes.PROGRAMS, "Программы", PackIcons.DUMBBELL, listOf("programs", "program/")),
    Tab(Routes.LIBRARY, "Справочник", PackIcons.BOOK_OPEN, listOf("library", "exercise/", "anatomy")),
    Tab(Routes.PROGRESS, "Прогресс", PackIcons.BARS, listOf("progress")),
    Tab(Routes.PROFILE, "Профиль", PackIcons.USER, listOf("profile", "body"))
)

/** Profile is a tab now: every «profile» shortcut switches to it instead of stacking a copy. */
private fun NavHostController.openTab(route: String) {
    if (route == Routes.TODAY && popBackStack(Routes.TODAY, inclusive = false)) return
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as FitApp
    val profile by app.container.profile.settings.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalWeightUnits provides WeightUnits(profile.unitsKg)) {
    Scaffold(
        containerColor = Fc.Bg,
        bottomBar = { BottomBar(nav) }
    ) { padding ->
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.fillMaxSize()
            .ambientGlow(!Fc.isLight, Fc.Lime).padding(padding)) {
            composable(Routes.TODAY) {
                TodayScreen(onStart = { nav.navigate(Routes.workout(it)) }, onPlan = { nav.navigate(Routes.plan(it)) },
                    onEditPlan = { date, id -> nav.navigate(Routes.plan(date, id)) }, onCalendar = { nav.navigate(Routes.calendar(it)) },
                    onPrograms = { nav.navigate(Routes.PROGRAMS) { launchSingleTop = true } }, onProfile = { nav.openTab(Routes.PROFILE) },
                    onResult = { nav.navigate(Routes.result(it)) })
            }
            composable(Routes.CALENDAR, arguments = listOf(navArgument("day") { type = NavType.LongType; defaultValue = -1L })) { entry ->
                CalendarHomeScreen(onPlan = { nav.navigate(Routes.plan(it)) }, onStart = { nav.navigate(Routes.workout(it)) }, onProfile = { nav.openTab(Routes.PROFILE) }, onEditPlan = { date, id -> nav.navigate(Routes.plan(date, id)) }, onResult = { nav.navigate(Routes.result(it)) },
                    initialEpoch = entry.arguments?.getLong("day")?.takeIf { it >= 0 }, onBack = { nav.popBackStack() })
            }
            composable(Routes.PLANNER, arguments = listOf(navArgument("epochDay") { type = NavType.LongType }, navArgument("schedule") { type = NavType.LongType; defaultValue = 0L })) { entry ->
                val selectedProgram by entry.savedStateHandle.getStateFlow("selectedProgramId", 0L).collectAsStateWithLifecycle()
                TrainingPlannerScreen(onChooseProgram = { nav.navigate("plan-programs/${entry.arguments!!.getLong("epochDay")}") },
                    scheduleId = entry.arguments!!.getLong("schedule"), selectedProgramId = selectedProgram, onSelectionConsumed = { entry.savedStateHandle["selectedProgramId"] = 0L }, epochDay = entry.arguments!!.getLong("epochDay"), onClose = { nav.popBackStack() }, onStart = {
                    nav.navigate(Routes.workout(it)) { popUpTo(Routes.PLANNER) { inclusive = true } }
                })
            }
            composable(Routes.PICK_PROGRAM, arguments = listOf(navArgument("epochDay") { type = NavType.LongType })) {
                ProgramsScreen(choosingProgram = true, onBack = { nav.popBackStack() },
                    onCreate = { nav.navigate(Routes.program(0L)) }, onOpen = { id ->
                        nav.previousBackStackEntry?.savedStateHandle?.set("selectedProgramId", id)
                        nav.popBackStack()
                    })
            }
            composable(Routes.RESULT, arguments = listOf(navArgument("session") { type = NavType.LongType },
                navArgument("celebrate") { type = NavType.BoolType; defaultValue = false })) { entry ->
                WorkoutResultScreen(entry.arguments!!.getLong("session"), onClose = { nav.popBackStack() },
                    celebrate = entry.arguments?.getBoolean("celebrate") == true,
                    onProgress = { nav.popBackStack(); nav.openTab(Routes.PROGRESS) })
            }
            composable(Routes.PROGRESS) { ProgressScreen(onProfile = { nav.openTab(Routes.PROFILE) }, onMeasure = { nav.navigate(Routes.measure(it)) { launchSingleTop = true } }) }
            composable(Routes.PROFILE) { ProfileScreen(onBack = null, onMeasure = { nav.navigate(Routes.measure(it)) { launchSingleTop = true } },
                onBody = { nav.navigate(Routes.BODY) { launchSingleTop = true } }) }
            composable(Routes.BODY) { com.fitclub.tracker.ui.profile.BodyScreen(onBack = { nav.popBackStack() }, onMeasure = { nav.navigate(Routes.measure(it)) { launchSingleTop = true } }) }
            composable(Routes.MEASURE, arguments = listOf(navArgument("metric") { type = NavType.StringType; defaultValue = "" })) { entry ->
                com.fitclub.tracker.ui.profile.MeasureScreen(entry.arguments?.getString("metric")?.ifBlank { null }, onClose = { nav.popBackStack() })
            }
            composable(Routes.WORKOUT, arguments = listOf(navArgument("schedule") { type = NavType.LongType })) { entry ->
                WorkoutScreen(
                    scheduleId = entry.arguments?.getLong("schedule") ?: 0L,
                    onClose = { nav.popBackStack() },
                    onTechnique = { nav.navigate(Routes.exercise(it) + "?training=true") },
                    onFinished = { session -> nav.navigate(Routes.result(session, celebrate = true)) { popUpTo(Routes.WORKOUT) { inclusive = true } } },
                    countdown = true
                )
            }
            composable(Routes.LIBRARY) {
                LibraryHomeScreen(
                    onGroup = { nav.navigate(Routes.group(it)) },
                    onExercise = { nav.navigate(Routes.exercise(it)) },
                    onProfile = { nav.openTab(Routes.PROFILE) },
                    onAnatomy = { nav.navigate(Routes.ATLAS) },
                    onAtlasMuscle = { nav.navigate(Routes.atlasMuscle(it)) }
                )
            }
            composable(Routes.ATLAS) {
                AtlasHomeScreen(onBack = { nav.popBackStack() }, onGroup = { nav.navigate(Routes.atlasGroup(it)) },
                    onMuscle = { nav.navigate(Routes.atlasMuscle(it)) })
            }
            composable(Routes.ATLAS_GROUP, arguments = listOf(navArgument("groupId") { type = NavType.StringType })) { entry ->
                AtlasGroupScreen(entry.arguments?.getString("groupId").orEmpty(), onBack = { nav.popBackStack() },
                    onMuscle = { nav.navigate(Routes.atlasMuscle(it)) })
            }
            composable(Routes.ATLAS_MUSCLE, arguments = listOf(navArgument("muscleId") { type = NavType.StringType })) { entry ->
                AtlasMuscleScreen(entry.arguments?.getString("muscleId").orEmpty(), onBack = { nav.popBackStack() },
                    // Paging to a neighbour replaces the card, so «back» still returns to the group.
                    onMuscle = { nav.navigate(Routes.atlasMuscle(it)) { popUpTo(Routes.ATLAS_MUSCLE) { inclusive = true } } },
                    onExercise = { nav.navigate(Routes.exercise(it)) })
            }
            composable(Routes.GROUP, arguments = listOf(navArgument("group") { type = NavType.StringType })) { entry ->
                val group = MuscleGroup.valueOf(entry.arguments?.getString("group") ?: MuscleGroup.CHEST.name)
                LibraryGroupScreen(group, onBack = { nav.popBackStack() }, onExercise = { nav.navigate(Routes.exercise(it)) },
                    onAdd = { nav.navigate("programs?add=$it") })
            }
            composable(Routes.EXERCISE, arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("training") { type = NavType.BoolType; defaultValue = false })) { entry ->
                ExerciseDetailScreen(
                    exerciseId = entry.arguments?.getString("id") ?: "",
                    trainingContext = entry.arguments?.getBoolean("training") == true,
                    onBack = { nav.popBackStack() },
                    onOpenExercise = { nav.navigate(Routes.exercise(it)) },
                    onAddToProgram = { nav.navigate("programs?add=${entry.arguments?.getString("id").orEmpty()}") }
                )
            }
            composable("programs?add={add}", arguments = listOf(navArgument("add") { type = NavType.StringType; defaultValue = "" })) { entry ->
                val add = entry.arguments?.getString("add").orEmpty()
                ProgramsScreen(onOpen = { nav.navigate(Routes.program(it, add)) }, choosingExercise = add.isNotBlank())
            }
            composable(Routes.PROGRAM, arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("add") { type = NavType.StringType; defaultValue = "" })) { entry ->
                ProgramEditorScreen(programId = entry.arguments?.getLong("id") ?: 0L, onClose = { nav.popBackStack() }, addExercise = entry.arguments?.getString("add").orEmpty())
            }
        }
    }
}

}

@Composable
private fun BottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: ""
    if (route.startsWith("program/") || route.startsWith("result/") || route.startsWith("workout") || route.startsWith("plan/") || route.startsWith("plan-programs/") || runCatching { nav.getBackStackEntry(Routes.WORKOUT) }.getOrNull() != null) return
    // Iron & Lime tab bar: a lime bar over the active tab, lime icon and label; the rest stay muted.
    Column(Modifier.background(Fc.Nav).navigationBarsPadding()) {
        HorizontalDivider(color = Fc.Line, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEach { tab ->
                val on = tab.prefixes.any { route.startsWith(it) }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { selected = on }
                        .clickable(role = Role.Tab) { nav.openTab(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.width(36.dp).height(3.dp)
                            .glow(Fc.Lime, on && !Fc.isLight, radius = 6.dp, alpha = .7f)
                            .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(if (on) Fc.Lime else Color.Transparent)
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LineIcon(tab.icon, if (on) Fc.Lime else Fc.Muted, Modifier.size(24.dp), strokeWidth = 1.8f)
                            Text(
                                tab.title, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
                                color = if (on) Fc.Lime else Fc.Muted, modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
