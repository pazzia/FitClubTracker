package com.fitclub.tracker

import android.app.Application
import com.fitclub.tracker.data.db.AppDatabase
import com.fitclub.tracker.data.repo.ExerciseRepository
import com.fitclub.tracker.data.repo.ProgramRepository
import com.fitclub.tracker.data.repo.FitnessRepository
import com.fitclub.tracker.data.repo.ProfileRepository
import com.fitclub.tracker.data.repo.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Простой контейнер зависимостей (без Hilt — для MVP достаточно). */
class AppContainer(app: Application) {
    val db: AppDatabase = AppDatabase.build(app)
    val exercises = ExerciseRepository(db)
    val programs = ProgramRepository(db)
    val fitness = FitnessRepository(db)
    val profile = ProfileRepository(app)
    val schedule = ScheduleRepository(db)
}

sealed interface StartupState {
    data object Loading : StartupState
    data object Ready : StartupState
    data class Failed(val message: String) : StartupState
}

class FitApp : Application() {
    lateinit var container: AppContainer
        private set

    private val _startup = MutableStateFlow<StartupState>(StartupState.Loading)
    val startup = _startup.asStateFlow()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        initialize()
    }

    fun initialize() {
        _startup.value = StartupState.Loading
        appScope.launch {
            runCatching {
                container.exercises.seedIfEmpty()
                container.programs.seedIfEmpty()
            }.onSuccess { _startup.value = StartupState.Ready }
                .onFailure { _startup.value = StartupState.Failed(it.message ?: "Не удалось открыть данные") }
        }
    }
}

