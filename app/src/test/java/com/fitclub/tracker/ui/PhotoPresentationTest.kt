package com.fitclub.tracker.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.fitclub.tracker.ui.components.frameExerciseThumbnail
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
        assertEquals(Color.rgb(102, 118, 142), result.getPixel(20, 40)) // mildly brightened background
        assertEquals(Color.rgb(102, 118, 142), result.getPixel(0, 0))
        assertEquals(255, Color.alpha(source.getPixel(0, 0)))
        assertEquals(source.getPixel(50, 40), result.getPixel(50, 40))
    }

    @Test fun thumbnailFrameKeepsEquipmentOutsideAthleteAndBackgroundMargin() {
        val source = Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val mask = Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        for (y in 40..100) for (x in 80..120) { mask.setPixel(x, y, Color.WHITE); source.setPixel(x, y, Color.RED) }
        mask.setPixel(45, 75, Color.WHITE); source.setPixel(45, 75, Color.BLUE)
        val result = frameExerciseThumbnail(source, mask)
        val pixels = IntArray(result.width * result.height); result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        assertTrue(pixels.contains(Color.BLUE)); assertTrue(pixels.contains(Color.RED)); assertTrue(pixels.contains(Color.GRAY))
        assertTrue(result.width < source.width)
    }

    @Test fun allEditedThumbnailsAreBundledAndTechniquesStayOriginal() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manifest = org.json.JSONObject(context.assets.open("exercise_thumbnails/manifest.json").bufferedReader().use { it.readText() })
        val rows = manifest.getJSONArray("photos")
        assertEquals(65, rows.length())
        val sourceHashes = context.assets.open("free_exercise_db/photos.sha256").bufferedReader().use { reader ->
            reader.readLines().filter(String::isNotBlank).associate { it.substringAfter(' ') to it.substringBefore(' ') }
        }
        val seen = mutableSetOf<String>()
        fun sha(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i); val path = row.getString("path")
            assertTrue(seen.add(path))
            assertTrue(path.endsWith("/1.jpg"))
            assertEquals(sourceHashes[path], row.getString("source_sha256"))
            val original = context.assets.open("free_exercise_db/$path").use { it.readBytes() }
            assertEquals(sourceHashes[path], sha(original))
            val edited = context.assets.open("exercise_thumbnails/$path").use { it.readBytes() }
            assertEquals(row.getString("thumbnail_sha256"), sha(edited))
            val bitmap = BitmapFactory.decodeByteArray(edited,0,edited.size)!!
            assertTrue(bitmap.width >= 320 && bitmap.height >= 240)
            bitmap.recycle()
        }
    }

    @Test fun expandedThumbnailsHaveOriginalPhotoProvenanceAndDecodeOffline() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val rows = org.json.JSONArray(context.assets.open("expanded_exercise_db/thumbnail-manifest.json")
            .bufferedReader().use { it.readText() })
        assertEquals(11, rows.length())
        val seen = mutableSetOf<String>()
        fun sha(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            assertTrue(seen.add(row.getString("sourcePath")))
            val original = context.assets.open(row.getString("sourcePath")).use { it.readBytes() }
            val prepared = context.assets.open(row.getString("thumbnailPath")).use { it.readBytes() }
            assertEquals(row.getString("source_sha256"), sha(original))
            assertEquals(row.getString("thumbnail_sha256"), sha(prepared))
            val bitmap = BitmapFactory.decodeByteArray(prepared, 0, prepared.size)!!
            assertTrue(bitmap.width >= 320 && bitmap.height >= 240)
            bitmap.recycle()
        }
    }

    @Test fun everyPhotoHasNonEmptyAlignedMaskAndNativePreview() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val lines = context.assets.open("free_exercise_db/photos.sha256").bufferedReader().use { it.readLines().filter(String::isNotBlank) }
        assertEquals(448, lines.size)
        val examples = listOf("Barbell_Bench_Press_-_Medium_Grip", "Incline_Dumbbell_Press", "Dumbbell_Flyes",
            "Close-Grip_Barbell_Bench_Press", "Barbell_Deadlift", "Sumo_Deadlift", "Wide-Grip_Lat_Pulldown", "Leg_Press", "Machine_Bench_Press", "Dips_-_Chest_Version", "Band_Pull_Apart", "Smith_Machine_Overhead_Shoulder_Press", "Ball_Leg_Curl")
        val output = File("build/contour-qa").apply { mkdirs() }
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
            val prepared = try { context.assets.open("exercise_thumbnails/$path").use { BitmapFactory.decodeStream(it) } } catch (_: java.io.IOException) { null }
            val rendered = prepared ?: renderExercisePhoto(source, mask)
            val trimmed = if (prepared != null) trimPhotoPadding(rendered) else trimPhotoPadding(rendered, source)
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
        sheets.forEachIndexed { index, sheet ->
            File(output, "all-photos-${index+1}.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            sheet.recycle()
        }
    }
}

