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
        assertEquals(listOf(4, 6, 11, 15, 20, 7, 11, 10, 8, 16, 8, 4), atlas.groups.map { it.muscles.size })
        assertEquals(120, atlas.muscles.map { it.id }.distinct().size)
        assertTrue(atlas.muscles.all { it.name.isNotBlank() && it.latin.isNotBlank() && it.where != null && it.does != null })
        // Wrapped two-line names must not leak into the Latin name.
        assertEquals("Длинный разгибатель большого пальца кисти", atlas.muscle("fit-2516")!!.name)
        assertFalse(atlas.muscles.any { Regex("[а-яё]").containsMatchIn(it.latin) })
        // Terminologia Anatomica word order: «flexor carpi radialis», not «flexor radialis carpi».
        assertEquals("Flexor carpi radialis", atlas.muscle("fit-2481")!!.latin)
        assertEquals("Flexor digitorum superficialis", atlas.muscle("fit-2486")!!.latin)
        assertEquals("Extensor hallucis longus", atlas.muscle("fit-2650")!!.latin)
        assertEquals("Diaphragma", atlas.muscle("fit-2327")!!.latin)
        val wrongOrder = Regex("\\b(radialis|ulnaris|longus|brevis|superficialis|profundus) (carpi|digitorum|pollicis|hallucis)\\b")
        assertEquals(emptyList<String>(), atlas.muscles.filter { wrongOrder.containsMatchIn(it.latin) }.map { it.latin })
    }

    @Test fun everyLinkedExerciseExistsInTheExerciseCatalog() {
        val ids = ExerciseSeed.all.map { it.id }.toSet()
        val missing = atlas.muscles.flatMap { m -> m.exercises.filter { it.exerciseId !in ids }.map { "${m.id}:${it.exerciseId}" } }
        assertEquals(emptyList<String>(), missing)
        // A muscle either has exercises or explains why it is not trained on its own.
        assertTrue(atlas.muscles.all { it.exercises.isNotEmpty() || it.trainingNote != null })
        val pecMajor = atlas.muscle("fit-2301")!!
        assertEquals(listOf("Верх", "Середина", "Низ"), partFilters(pecMajor.exercises))
        assertTrue(pecMajor.exercises.all { it.main })
        assertTrue(atlas.muscle("fit-2305")!!.exercises.none { it.main })
    }

    @Test fun searchWorksInRussianAndLatin() {
        assertEquals(listOf("fit-2301"), atlas.search("БОЛЬШАЯ грудная").map { it.id })
        assertEquals(listOf("fit-2301"), atlas.search("pectoralis major").map { it.id })
        assertTrue(atlas.search("трехглавая").map { it.id }.containsAll(listOf("fit-2471", "fit-2656")))
        assertTrue(atlas.search("несуществующаямышца").isEmpty())
        assertTrue(atlas.search("  ").isEmpty())
    }

    @Test fun aliasesPartsAndMembersFromTheReviewedCatalog() {
        assertEquals("fit-2301", atlas.search("грудные").first().id)
        assertEquals(3, atlas.muscle("fit-2301")!!.parts.size)
        assertTrue(atlas.muscles.any { it.members.isNotEmpty() })
        assertTrue(atlas.muscles.none { it.name == "Подкожная мышца шеи" })
    }

    @Test fun neighboursStayInsideTheGroup() {
        assertEquals(null to "fit-2305", atlas.neighbours("fit-2301").let { it.first?.id to it.second?.id })
        assertEquals("fit-2306" to null, atlas.neighbours("fit-2307").let { it.first?.id to it.second?.id })
    }

    @Test fun everyIllustrationAndThumbnailDecodesOffline() {
        val files = context.assets.list("atlas")!!.toSet()
        assertEquals(9, atlas.muscles.count { !it.hasIllustration })
        assertTrue(atlas.muscles.filter { !it.hasIllustration }.all { it.schematic.isNotEmpty() })
        atlas.muscles.filter { it.hasIllustration }.forEach { m ->
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
