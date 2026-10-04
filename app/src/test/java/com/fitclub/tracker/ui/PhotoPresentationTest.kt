package com.fitclub.tracker.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.fitclub.tracker.ui.components.trimPhotoPadding
import com.fitclub.tracker.ui.components.renderExerciseThumbnail
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
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

    @Test fun fadingPreservesAthleteAndNearbyEquipmentAndDoesNotChangeSource() {
        val source = Bitmap.createBitmap(100, 80, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(80, 100, 130))
        val mask = FloatArray(8000) { i -> if (i % 100 in 30..69 && i / 100 in 10..69) 1f else 0f }
        val result = renderExerciseThumbnail(source, mask)
        val foreground = Color.alpha(result.getPixel(50, 40))
        val equipment = Color.alpha(result.getPixel(20, 40))
        val background = Color.alpha(result.getPixel(0, 0))
        assertEquals(255, foreground)
        assertTrue(equipment > 220)
        assertTrue(background < 70)
        assertEquals(255, Color.alpha(source.getPixel(0, 0)))
    }

    @Test fun whiteBackgroundBecomesTransparentWhenSegmentationIsUnavailable() {
        val source = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        source.setPixel(30, 30, Color.RED)
        val result = renderExerciseThumbnail(source, null)
        assertTrue(Color.alpha(result.getPixel(0, 0)) < 20)
        assertEquals(255, Color.alpha(result.getPixel(30, 30)))
    }
}
