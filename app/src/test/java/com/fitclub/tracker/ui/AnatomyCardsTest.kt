package com.fitclub.tracker.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.FitApp
import com.fitclub.tracker.ui.components.AnatomyCutouts
import com.fitclub.tracker.ui.components.AtlasBand
import com.fitclub.tracker.ui.components.trimAtlasBackdrop
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = FitApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnatomyCardsTest {
    @Test fun onlyConnectedBackdropIsRemovedAndDarkClothingIsPreserved() {
        val backdrop = Color.rgb(20, 25, 29)
        val skin = Color.rgb(170, 90, 60)
        val source = IntArray(49) { backdrop }
        for (y in 1..5) for (x in 1..5) source[y * 7 + x] = skin
        source[3 * 7 + 3] = backdrop // dark garment enclosed by the subject
        val result = trimAtlasBackdrop(source, 7, 7)
        assertEquals(5, result.width); assertEquals(5, result.height)
        assertEquals(backdrop, result.pixels[2 * 5 + 2])
        assertEquals(skin, result.pixels.first())
        assertEquals(backdrop, source.first()) // the source remains untouched
    }

    @Test fun garmentTouchingTheBackdropIsNotErased() {
        val backdrop = Color.rgb(20, 25, 29)
        val skin = Color.rgb(170, 90, 60)
        val source = IntArray(49) { backdrop }
        for (y in 1..5) for (x in 1..5) source[y * 7 + x] = skin
        for (x in 2..4) source[5 * 7 + x] = backdrop // black garment open to the lower frame
        val result = trimAtlasBackdrop(source, 7, 7, listOf(AtlasBand(0f, 1f, .6f, .9f)))
        assertEquals(5, result.width); assertEquals(5, result.height)
        assertEquals(backdrop, result.pixels[4 * 5 + 2])
    }

    @Test fun allAtlasesHaveTransparentTrimmedBackdropAndRenderInBothThemes() {
        val app = ApplicationProvider.getApplicationContext<FitApp>()
        val indexes = listOf(0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11)
        val labels = listOf("Грудь", "Плечи", "Бицепс", "Трицепс", "Предплечья", "Пресс и кор",
            "Спина", "Ягодицы", "Квадрицепсы", "Бицепс бедра", "Икры")
        for (sex in listOf("male", "female")) {
            val images = indexes.map { index ->
                val id = app.resources.getIdentifier("group_${sex}_$index", "drawable", app.packageName)
                assertTrue("Missing $sex/$index", id != 0)
                val source = BitmapFactory.decodeResource(app.resources, id)
                val result = AnatomyCutouts.get(app.resources, id)
                assertTrue(result.width <= source.width && result.height <= source.height)
                val pixels = IntArray(result.width * result.height)
                result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
                assertTrue("No cutout: $sex/$index", pixels.any { Color.alpha(it) == 0 })
                assertTrue("Subject erased: $sex/$index", pixels.count { Color.alpha(it) > 0 } > pixels.size / 5)
                assertTrue((0 until result.width).any { Color.alpha(pixels[it]) > 0 })
                assertTrue((0 until result.width).any { Color.alpha(pixels[(result.height - 1) * result.width + it]) > 0 })
                assertTrue((0 until result.height).any { Color.alpha(pixels[it * result.width]) > 0 })
                assertTrue((0 until result.height).any { Color.alpha(pixels[it * result.width + result.width - 1]) > 0 })
                source.recycle()
                result
            }
            for (light in listOf(true, false)) {
                val board = Bitmap.createBitmap(720, 1920, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(board)
                canvas.drawColor(if (light) Color.WHITE else Color.rgb(14, 19, 23))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                paint.color = if (light) Color.rgb(23, 33, 43) else Color.WHITE
                paint.textSize = 30f; paint.isFakeBoldText = true
                canvas.drawText("Группы мышц · 11", 20f, 42f, paint)
                images.forEachIndexed { index, image ->
                    val x = 20f + (index % 2) * 350f
                    val y = 66f + (index / 2) * 308f
                    val window = RectF(x, y, x + 330f, y + 297.5f)
                    paint.color = if (light) Color.WHITE else Color.rgb(24, 32, 40)
                    canvas.drawRoundRect(window, 20f, 20f, paint)
                    val imageWindow = RectF(x, y, x + 330f, y + 247.5f)
                    canvas.save()
                    canvas.clipRect(imageWindow)
                    paint.color = if (light) Color.rgb(241, 244, 246) else Color.rgb(34, 42, 49)
                    canvas.drawRect(imageWindow, paint)
                    val scale = maxOf(imageWindow.width() / image.width, imageWindow.height() / image.height)
                    val scaledWidth = image.width * scale; val scaledHeight = image.height * scale
                    val left = imageWindow.centerX() - scaledWidth / 2
                    val top = if (index == 10) imageWindow.top else imageWindow.centerY() - scaledHeight / 2
                    canvas.drawBitmap(image, Rect(0, 0, image.width, image.height),
                        RectF(left, top, left + scaledWidth, top + scaledHeight), paint)
                    canvas.restore()
                    paint.color = if (light) Color.rgb(23, 33, 43) else Color.WHITE
                    paint.textSize = 24f
                    canvas.drawText(labels[index], x + 12f, y + 281f, paint)
                }
                val out = File("build/ui-preview/anatomy-$sex-${if (light) "light" else "dark"}.png")
                out.parentFile.mkdirs()
                out.outputStream().use { board.compress(Bitmap.CompressFormat.PNG, 100, it) }
                board.recycle()
            }
        }
    }
}
