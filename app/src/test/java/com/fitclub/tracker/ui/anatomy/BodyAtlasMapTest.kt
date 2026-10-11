package com.fitclub.tracker.ui.anatomy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = android.app.Application::class)
class BodyAtlasMapTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun map(sex: String) = context.assets.open("bodymap/${sex}_zones.png").use { ZoneMap.decode(it.readBytes()) }!!

    @Test fun everyZoneOpensAnExistingAtlasCard() {
        val atlas = MuscleAtlas.load(context)
        BodyZone.entries.forEach { assertNotNull("${it.title} → ${it.muscleId}", atlas.muscle(it.muscleId)) }
    }

    @Test fun tapsLandOnTheRightMuscle() {
        val male = map("male")
        val samples = mapOf(
            BodyZone.CHEST to (0.2386f to 0.2611f), BodyZone.BICEPS to (0.3024f to 0.3389f), BodyZone.TRICEPS to (0.883f to 0.3456f),
            BodyZone.ABS to (0.2386f to 0.3767f), BodyZone.LATS to (0.7401f to 0.3344f), BodyZone.GLUTES to (0.7462f to 0.4944f),
            BodyZone.QUADS to (0.2781f to 0.57f), BodyZone.HAMSTRINGS to (0.731f to 0.5989f), BodyZone.CALVES to (0.6185f to 0.7811f))
        samples.forEach { (zone, p) -> assertEquals(zone.title, zone, male.zoneAt(p.first, p.second, radius = 0)) }
        val female = map("female")
        assertEquals(BodyZone.CHEST, female.zoneAt(0.2462f, 0.2878f, radius = 0))
        assertEquals(BodyZone.GLUTES, female.zoneAt(0.7508f, 0.5011f, radius = 0))
    }

    @Test fun backgroundIsNotClickableButNearbyMuscleIs() {
        val male = map("male")
        assertNull(male.zoneAt(0.02f, 0.98f))
        // A tap a few pixels off a muscle still finds it.
        assertEquals(BodyZone.CHEST, male.zoneAt(0.2386f, 0.2611f))
    }
}
