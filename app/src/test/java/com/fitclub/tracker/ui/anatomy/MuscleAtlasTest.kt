package com.fitclub.tracker.ui.anatomy

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.data.seed.ExerciseSeed
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MuscleAtlasTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val atlas by lazy { MuscleAtlas.load(context) }

    @Test fun twelveFitnessGroupsFromTheCatalogAreBundled() {
        assertEquals(
            listOf("Грудь", "Плечи", "Спина", "Плечо и предплечье", "Кисть и хват", "Кор", "Таз и ягодицы",
                "Бедро", "Голень", "Стопа", "Шея", "Дыхание"),
            atlas.groups.map { it.title })
        assertEquals(listOf(4, 6, 11, 15, 20, 7, 11, 10, 8, 16, 9, 4), atlas.groups.map { it.muscles.size })
        assertEquals(121, atlas.muscles.map { it.id }.distinct().size)
        assertTrue(atlas.muscles.all { it.name.isNotBlank() && it.latin.isNotBlank() && it.where != null && it.does != null })
        // Wrapped two-line names must not leak into the Latin name.
        assertEquals("Длинный разгибатель большого пальца кисти", atlas.muscle("m043")!!.name)
        assertFalse(atlas.muscles.any { Regex("[а-яё]").containsMatchIn(it.latin) })
    }

    @Test fun everyLinkedExerciseExistsInTheExerciseCatalog() {
        val ids = ExerciseSeed.all.map { it.id }.toSet()
        val missing = atlas.muscles.flatMap { m -> m.exercises.filter { it.exerciseId !in ids }.map { "${m.id}:${it.exerciseId}" } }
        assertEquals(emptyList<String>(), missing)
        // A muscle either has exercises or explains why it is not trained on its own.
        assertTrue(atlas.muscles.all { it.exercises.isNotEmpty() || it.trainingNote != null })
        val pecMajor = atlas.muscle("m001")!!
        assertEquals(listOf("Верх", "Середина", "Низ"), partFilters(pecMajor.exercises))
        assertTrue(pecMajor.exercises.all { it.main })
        assertTrue(atlas.muscle("m002")!!.exercises.none { it.main })
    }

    @Test fun searchWorksInRussianAndLatin() {
        assertEquals(listOf("m001"), atlas.search("БОЛЬШАЯ грудная").map { it.id })
        assertEquals(listOf("m001"), atlas.search("pectoralis major").map { it.id })
        assertTrue(atlas.search("трехглавая").map { it.id }.containsAll(listOf("m025", "m085")))
        assertTrue(atlas.search("несуществующаямышца").isEmpty())
        assertTrue(atlas.search("  ").isEmpty())
    }

    @Test fun neighboursStayInsideTheGroup() {
        assertEquals(null to "m002", atlas.neighbours("m001").let { it.first?.id to it.second?.id })
        assertEquals("m003" to null, atlas.neighbours("m004").let { it.first?.id to it.second?.id })
    }

    @Test fun everyIllustrationAndThumbnailDecodesOffline() {
        val files = context.assets.list("atlas")!!.toSet()
        atlas.muscles.forEach { m ->
            for (path in listOf(m.image, m.thumbnail)) {
                assertTrue("Missing $path", path.removePrefix("atlas/") in files)
                context.assets.open(path).use { input ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, options)
                    assertTrue("Unreadable $path", options.outWidth > 0 && options.outHeight > 0)
                }
            }
        }
    }

    @Test fun russianPluralsAreCorrect() {
        assertEquals("1 мышца", musclesWord(1))
        assertEquals("4 мышцы", musclesWord(4))
        assertEquals("11 мышц", musclesWord(11))
        assertEquals("121 мышца", musclesWord(121))
        assertEquals("5 упражнений", exercisesWord(5))
        assertEquals("22 упражнения", exercisesWord(22))
    }
}
