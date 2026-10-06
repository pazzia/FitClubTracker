package com.fitclub.tracker.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.ui.components.trimPhotoPadding
import com.fitclub.tracker.ui.components.renderExercisePhoto
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoPresentationTest {
    @Test fun removesEdgePaddingWithoutRemovingInteriorWhiteHighlights() {
        val source = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        for (y in 10 until 70) for (x in 10 until 90) source.setPixel(x, y, Color.RED)
        source.setPixel(50, 40, Color.WHITE)
        val result = trimPhotoPadding(source)
        assertEquals(80, result.width)
        assertEquals(60, result.height)
        assertEquals(Color.WHITE, result.getPixel(40, 30))
        assertEquals(Color.RED, result.getPixel(0, 0))
    }

    @Test fun contourPreservesEquipmentAndRetainsBackgroundWithMildContrast() {
        val source = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(80, 100, 130))
        val mask = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        mask.eraseColor(Color.BLACK)
        for (y in 10..69) for (x in 30..69) mask.setPixel(x, y, Color.WHITE)
        for (y in 35..45) for (x in 5..15) mask.setPixel(x, y, Color.WHITE)
        val result = renderExercisePhoto(source, mask)
        assertEquals(255, Color.alpha(result.getPixel(50, 40)))
        assertEquals(255, Color.alpha(result.getPixel(10, 40))) // equipment outside person bounds
        assertEquals(255, Color.alpha(result.getPixel(20, 40))) // full background between weight and athlete
        assertEquals(Color.rgb(102, 118, 142), result.getPixel(20, 40)) // gentle background brightening
        assertEquals(Color.rgb(102, 118, 142), result.getPixel(0, 0))
        assertEquals(255, Color.alpha(source.getPixel(0, 0)))
        assertEquals(source.getPixel(50, 40), result.getPixel(50, 40))
    }

    @Test fun recolorsOnlyGarmentWhenItMergesWithBackground() {
        val source = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(45,45,45)) }
        val foreground = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val clothing = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        for (y in 20..60) for (x in 30..70) foreground.setPixel(x,y,Color.WHITE)
        for (y in 30..50) for (x in 40..60) clothing.setPixel(x,y,Color.WHITE)
        source.setPixel(32,25,Color.rgb(190,130,95)) // exposed skin
        val result = renderExercisePhoto(source,foreground,clothing)
        assertNotEquals(source.getPixel(50,40),result.getPixel(50,40))
        assertEquals(source.getPixel(32,25),result.getPixel(32,25))
        assertEquals(source.getPixel(35,40),result.getPixel(35,40)) // gear outside clothes
        assertEquals(Color.rgb(45,45,45),source.getPixel(50,40))
        for (y in 20..60) for (x in 30..70) source.setPixel(x,y,Color.WHITE)
        val alreadyVisible = renderExercisePhoto(source,foreground,clothing)
        assertEquals(Color.WHITE,alreadyVisible.getPixel(50,40))
    }

    @Test fun everyPhotoHasNonEmptyAlignedMaskAndNativePreview() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val lines = context.assets.open("free_exercise_db/photos.sha256").bufferedReader().use { it.readLines().filter(String::isNotBlank) }
        assertEquals(448, lines.size)
        val examples = listOf("Barbell_Bench_Press_-_Medium_Grip", "Incline_Dumbbell_Press", "Dumbbell_Flyes",
            "Close-Grip_Barbell_Bench_Press", "Barbell_Deadlift", "Sumo_Deadlift", "Wide-Grip_Lat_Pulldown", "Leg_Press", "Machine_Bench_Press", "Dips_-_Chest_Version", "Band_Pull_Apart", "Smith_Machine_Overhead_Shoulder_Press", "Ball_Leg_Curl")
        val output = File("build/contour-qa").apply { mkdirs() }
        val changes = mutableListOf("photo,changed_clothing_pixels")
        val sheets = List(4) { Bitmap.createBitmap(1920, 2100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(27, 34, 40)) } }
        val sheetCanvases = sheets.map { Canvas(it) }
        val sheetPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for ((index, line) in lines.withIndex()) {
            val path = line.substringAfter(' ')
            val source = context.assets.open("free_exercise_db/$path").use { BitmapFactory.decodeStream(it) }!!
            val mask = context.assets.open("exercise_masks/${path.removeSuffix(".jpg")}.png").use { BitmapFactory.decodeStream(it) }!!
            assertEquals(path, source.width, mask.width)
            assertEquals(path, source.height, mask.height)
            val pixels = IntArray(mask.width * mask.height)
            mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
            assertTrue("No foreground: $path", pixels.count { Color.red(it) > 128 } > pixels.size * .003)
            assertTrue("No background: $path", pixels.count { Color.red(it) < 128 } > pixels.size * .02)
            val clothing = if (path.endsWith("/1.jpg")) context.assets.open("exercise_clothing/${path.removeSuffix(".jpg")}.png").use { BitmapFactory.decodeStream(it) }!! else null
            if (clothing != null) {
                assertEquals(path, source.width, clothing.width)
                assertEquals(path, source.height, clothing.height)
            }
            val rendered = renderExercisePhoto(source, mask, clothing)
            // Gear and skin outside the garment mask retain exact foreground pixels.
            for (y in 0 until source.height step 4) for (x in 0 until source.width step 4) {
                if (Color.red(mask.getPixel(x,y)) == 255 && (clothing == null || Color.red(clothing.getPixel(x,y)) == 0))
                    assertEquals("Changed unmasked foreground: $path ($x,$y)", source.getPixel(x,y), rendered.getPixel(x,y))
            }
            if (clothing != null) {
                var changed = 0
                for (y in 0 until source.height) for (x in 0 until source.width)
                    if (Color.red(clothing.getPixel(x,y)) == 255 && source.getPixel(x,y) != rendered.getPixel(x,y)) changed++
                changes.add("$path,$changed")
            }
            clothing?.recycle()
            val trimmed = trimPhotoPadding(rendered, source)
            val cell = index % 112
            val x = (cell % 8) * 240
            val y = (cell / 8) * 150
            val cellScale = minOf(230f / trimmed.width, 123f / trimmed.height)
            val cw = (trimmed.width * cellScale).toInt(); val ch = (trimmed.height * cellScale).toInt()
            sheetCanvases[index / 112].drawBitmap(trimmed, null, Rect(x+(240-cw)/2, y, x+(240+cw)/2, y+ch), sheetPaint)
            sheetPaint.color = Color.WHITE; sheetPaint.textSize = 11f
            sheetCanvases[index / 112].drawText("${index+1}: ${path.take(35)}", (x+3).toFloat(), (y+139).toFloat(), sheetPaint)
            if (path.substringBefore('/') in examples) {
                val preview = Bitmap.createBitmap(800, 520, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(preview)
                canvas.drawColor(Color.rgb(27, 34, 40))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val scale = minOf(760f / trimmed.width, 420f / trimmed.height)
                val w = (trimmed.width * scale).toInt(); val h = (trimmed.height * scale).toInt()
                canvas.drawBitmap(trimmed, null, Rect((800-w)/2, 0, (800+w)/2, h), paint)
                val smallScale = minOf(150f / trimmed.width, 80f / trimmed.height)
                val sw = (trimmed.width * smallScale).toInt(); val sh = (trimmed.height * smallScale).toInt()
                canvas.drawBitmap(trimmed, null, Rect(20, 435, 20+sw, 435+sh), paint)
                paint.color = Color.WHITE; paint.textSize = 15f
                canvas.drawText(path, 185f, 475f, paint)
                File(output, path.replace('/', '-').replace(".jpg", ".png")).outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
                preview.recycle()
            }
            if (trimmed !== rendered) trimmed.recycle()
            rendered.recycle(); source.recycle(); mask.recycle()
        }
        File(output, "clothing-changes.csv").writeText(changes.joinToString("\n"))
        sheets.forEachIndexed { index, sheet ->
            File(output, "all-photos-${index+1}.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            sheet.recycle()
        }
    }
}

