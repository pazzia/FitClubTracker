package com.fitclub.tracker.data

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.data.db.AppDatabase
import com.fitclub.tracker.data.model.MuscleGroup
import com.fitclub.tracker.data.repo.ExerciseRepository
import com.fitclub.tracker.data.seed.ExerciseSeed
import com.fitclub.tracker.data.seed.FreeExerciseCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ExerciseCatalogTest {
    @Test fun curatedImportCoversAllGroupsWithOfflinePhotos() {
        assertEquals(220, FreeExerciseCatalog.all.size)
        assertEquals(220, FreeExerciseCatalog.all.map { it.id }.toSet().size)
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (group in MuscleGroup.entries) {
            assertEquals(group.title, 20, FreeExerciseCatalog.all.count { it.group == group })
        }
        for (e in FreeExerciseCatalog.all) {
            assertTrue(e.name.any { it in 'А'..'я' })
            val media = FreeExerciseCatalog.media.getValue(e.id)
            assertEquals(2, media.images.size)
            assertTrue(media.videoUrl!!.startsWith("https://www.youtube.com/watch?v="))
            for (path in media.images) {
                context.assets.open(path).use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    assertTrue(path, options.outWidth > 0 && options.outHeight > 0)
                }
            }
        }
        assertEquals(5, FreeExerciseCatalog.media.values.count { it.category == "Растяжка" })
    }

    @Test fun populatedInstallGetsCatalogAndRepeatedStartupPreservesExistingRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val existing = ExerciseSeed.all.first { it.id == "bench" }.copy(technique = "Сохранённая техника")
            db.exerciseDao().insertAll(listOf(existing))
            val repository = ExerciseRepository(db)
            repository.seedIfEmpty()
            repository.seedIfEmpty()
            val rows = repository.observeAll().first()
            assertEquals(ExerciseSeed.all.size, rows.size)
            assertEquals("Сохранённая техника", rows.first { it.id == "bench" }.technique)
            for (group in MuscleGroup.entries) assertTrue(group.title, rows.count { it.group == group } >= 20)
            assertTrue(rows.any { it.id == "wheel" })
        } finally { db.close() }
    }
}
