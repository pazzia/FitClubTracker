package com.fitclub.tracker

import android.app.Application
import com.fitclub.tracker.data.db.AppDatabase
import com.fitclub.tracker.data.repo.ExerciseRepository
import com.fitclub.tracker.data.repo.ProgramRepository
import com.fitclub.tracker.data.repo.FitnessRepository
import com.fitclub.tracker.data.repo.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Простой контейнер зависимостей (без Hilt — для MVP достаточно). */
class AppContainer(app: Application) {
    val db: AppDatabase = AppDatabase.build(app)
    val exercises = ExerciseRepository(db)
    val programs = ProgramRepository(db)
    val fitness = FitnessRepository(db)
    val profile = ProfileRepository(app)
}

class FitApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.exercises.seedIfEmpty()
            container.programs.seedIfEmpty()
        }
    }
}
