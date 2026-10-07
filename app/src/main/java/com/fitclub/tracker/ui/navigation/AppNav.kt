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
import com.fitclub.tracker.ui.components.LineIcons

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
    const val WORKOUT = "workout/{schedule}"
    const val PICK_PROGRAM = "plan-programs/{epochDay}"
    const val PLANNER = "plan/{epochDay}?schedule={schedule}"
    const val RESULT = "result/{session}"
    fun program(id: Long, add: String = "") = "program/$id?add=$add"
    fun group(g: MuscleGroup) = "library/${g.name}"
    fun exercise(id: String) = "exercise/$id"
    fun workout(schedule: Long) = "workout/$schedule"
    fun plan(epochDay: Long, schedule: Long = 0L) = "plan/$epochDay?schedule=$schedule"
    fun calendar(epochDay: Long = -1L) = "calendar?day=$epochDay"
    fun result(session: Long) = "result/$session"
}

private data class Tab(val route: String, val title: String, val icon: String, val prefixes: List<String>)

private val tabs = listOf(
    Tab(Routes.TODAY, "Сегодня", LineIcons.BOLT, listOf("today", "calendar")),
    Tab(Routes.PROGRAMS, "Программы", LineIcons.LIST, listOf("programs", "program/")),
    Tab(Routes.LIBRARY, "Справочник", LineIcons.BOOK, listOf("library", "exercise/")),
    Tab(Routes.PROGRESS, "Прогресс", LineIcons.CHART, listOf("progress"))
)

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
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.fillMaxSize().padding(padding)) {
            composable(Routes.TODAY) {
                TodayScreen(onStart = { nav.navigate(Routes.workout(it)) }, onPlan = { nav.navigate(Routes.plan(it)) },
                    onEditPlan = { date, id -> nav.navigate(Routes.plan(date, id)) }, onCalendar = { nav.navigate(Routes.calendar(it)) },
                    onPrograms = { nav.navigate(Routes.PROGRAMS) { launchSingleTop = true } }, onProfile = { nav.navigate(Routes.PROFILE) })
            }
            composable(Routes.CALENDAR, arguments = listOf(navArgument("day") { type = NavType.LongType; defaultValue = -1L })) { entry ->
                CalendarHomeScreen(onPlan = { nav.navigate(Routes.plan(it)) }, onStart = { nav.navigate(Routes.workout(it)) }, onProfile = { nav.navigate(Routes.PROFILE) }, onEditPlan = { date, id -> nav.navigate(Routes.plan(date, id)) }, onResult = { nav.navigate(Routes.result(it)) },
                    initialEpoch = entry.arguments?.getLong("day")?.takeIf { it >= 0 })
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
            composable(Routes.RESULT, arguments = listOf(navArgument("session") { type = NavType.LongType })) { entry ->
                WorkoutResultScreen(entry.arguments!!.getLong("session"), onClose = { nav.popBackStack() })
            }
            composable(Routes.PROGRESS) { ProgressScreen(onProfile = { nav.navigate(Routes.PROFILE) }) }
            composable(Routes.PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.WORKOUT, arguments = listOf(navArgument("schedule") { type = NavType.LongType })) { entry ->
                WorkoutScreen(
                    scheduleId = entry.arguments?.getLong("schedule") ?: 0L,
                    onClose = { nav.popBackStack() },
                    onTechnique = { nav.navigate(Routes.exercise(it) + "?training=true") },
                    onFinished = { session -> nav.navigate(Routes.result(session)) { popUpTo(Routes.WORKOUT) { inclusive = true } } }
                )
            }
            composable(Routes.LIBRARY) {
                LibraryHomeScreen(
                    onGroup = { nav.navigate(Routes.group(it)) },
                    onExercise = { nav.navigate(Routes.exercise(it)) },
                    onProfile = { nav.navigate(Routes.PROFILE) }
                )
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
    Column(Modifier.background(Fc.Nav).navigationBarsPadding()) {
        HorizontalDivider(color = Fc.Line2, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEach { tab ->
                val on = tab.prefixes.any { route.startsWith(it) }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { selected = on }
                        .clickable(role = Role.Tab) {
                            // Screens opened on top of Today (profile, calendar) must not come back as its saved state.
                            if (tab.route == Routes.TODAY && nav.popBackStack(Routes.TODAY, inclusive = false)) return@clickable
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .width(52.dp).height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (on) Fc.LimeSoft else Fc.Nav),
                        contentAlignment = Alignment.Center
                    ) {
                        LineIcon(tab.icon, if (on) Fc.Lime else Fc.Muted, Modifier.size(22.dp))
                    }
                    Text(
                        tab.title, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (on) Fc.Lime else Fc.Muted, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
