package com.fitclub.tracker.ui.anatomy

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AnatomyCatalogTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Test fun completeDatabaseIsBundledWithoutLosingUnfilledRecords() {
        val records = AnatomyCatalog.load(context)
        assertEquals(274, records.size)
        assertEquals(274, records.map { it.id }.distinct().size)
        assertEquals(165, records.count { it.fitness })
        assertTrue(records.any { it.action == null })
        assertTrue(records.any { !it.fitness })
        assertTrue(records.all { it.name.isNotBlank() && it.latin.isNotBlank() })
    }
    @Test fun russianLatinAliasAndCombinedFiltersWork() {
        val all = AnatomyCatalog.load(context)
        assertEquals(listOf("TA2:2464"), AnatomyCatalog.filter(all, "БИЦЕПС", false, null, null).map { it.id })
        assertEquals(listOf("TA2:2464"), AnatomyCatalog.filter(all, "biceps brachii", true, "movement", "Плечо").map { it.id })
        assertTrue(AnatomyCatalog.filter(all, "мышца глаза", true, null, null).isEmpty())
        assertEquals(165, AnatomyCatalog.filter(all, "", true, null, null).size)
        assertTrue(AnatomyCatalog.filter(all, "несуществующаямышца", false, null, null).isEmpty())
        assertTrue(AnatomyCatalog.filter(all, "", true, "grip", null).all { it.fitness && it.role == "grip" })
    }
    @Test fun allIllustrationsAndRelationshipsResolveOffline() {
        val records = AnatomyCatalog.load(context)
        val ids = records.map { it.id }.toSet()
        assertTrue(records.all { m -> m.relatedIds.all { it in ids } })
        val assets = context.assets.list("anatomy")!!.filter { it.endsWith(".webp") }
        assertEquals(17, assets.size)
        records.flatMap { it.assets }.distinct().forEach { id ->
            assertTrue("Missing $id", "$id.webp" in assets)
            context.assets.open("anatomy/$id.webp").use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                assertTrue(options.outWidth > 0 && options.outHeight > 0)
            }
        }
    }
}
