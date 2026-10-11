package com.fitclub.tracker.domain

import com.fitclub.tracker.data.db.BodyMeasurementEntity
import org.junit.Assert.*
import org.junit.Test

class BodyModelTest {
    private fun row(day: Long, weight: Float? = null, waist: Float? = null, hips: Float? = null, chest: Float? = null) =
        BodyMeasurementEntity(epochDay = day, weightKg = weight, waistCm = waist, fatPercent = null, chestCm = chest, armCm = null, hipsCm = hips)

    @Test fun everyGirthHasABandOnBothFiguresAndStaysInsideTheDrawing() {
        for (figure in listOf(BodyFigure.MALE, BodyFigure.FEMALE)) {
            assertEquals(BodyMetric.entries.filter { it.isGirth }.toSet(), figure.bands.map { it.metric }.toSet())
            for (b in figure.bands) {
                assertTrue(b.cx - b.half >= 0f && b.cx + b.half <= figure.width)
                assertTrue(b.y in 0f..figure.height)
            }
            // Top to bottom: chest above waist above hips above thigh.
            val y = figure.bands.associate { it.metric to it.y }
            assertTrue(y.getValue(BodyMetric.CHEST) < y.getValue(BodyMetric.WAIST))
            assertTrue(y.getValue(BodyMetric.WAIST) < y.getValue(BodyMetric.HIPS))
            assertTrue(y.getValue(BodyMetric.HIPS) < y.getValue(BodyMetric.THIGH))
        }
    }

    @Test fun valueOnADateIsTheLatestRecordedUpToThatDay() {
        val rows = listOf(row(1, weight = 80f, waist = 88f), row(15, weight = 79f), row(30, waist = 85f, hips = 100f))
        assertEquals(88f, valueAsOf(rows, 1, BodyMetric.WAIST))
        assertEquals(79f, valueAsOf(rows, 2, BodyMetric.WEIGHT))
        assertNull(valueAsOf(rows, 1, BodyMetric.HIPS))
        assertEquals(100f, valueAsOf(rows, 2, BodyMetric.HIPS))
        assertNull(valueAsOf(rows, 5, BodyMetric.WAIST))
        assertEquals(88f, referenceValue(rows, BodyMetric.WAIST))
    }

    @Test fun tapeFollowsTheGirthAndIgnoresTypos() {
        val band = BodyFigure.MALE.bands.first { it.metric == BodyMetric.WAIST }
        assertEquals(band.half * 2, tapeLength(band, 88f, 88f), 0.001f)
        assertEquals(band.half * 2 * 82f / 88f, tapeLength(band, 82f, 88f), 0.001f)
        assertEquals(band.half * 2 * 1.5f, tapeLength(band, 880f, 88f), 0.001f)
        assertEquals(band.half * 2, tapeLength(band, null, 88f), 0.001f)
    }

    @Test fun changeKnowsWhichDirectionIsProgress() {
        assertEquals(MetricChange(-6f, true), metricChange(BodyMetric.WAIST, 82f, 88f))
        assertEquals(MetricChange(3f, true), metricChange(BodyMetric.CHEST, 104f, 101f))
        assertEquals(MetricChange(-0.5f, false), metricChange(BodyMetric.ARM, 37f, 37.5f))
        assertTrue(metricChange(BodyMetric.WEIGHT, 80f, 80f)!!.isZero)
        assertNull(metricChange(BodyMetric.WEIGHT, 80f, null))
    }

    @Test fun numbersUseCommaAndRealMinus() {
        assertEquals("82", formatMeasure(82f))
        assertEquals("81,5", formatMeasure(81.5f))
        assertEquals("0,3", formatMeasure(0.1f + 0.2f))
        assertEquals("−6", formatSignedMeasure(-6f))
        assertEquals("+0,5", formatSignedMeasure(0.5f))
        assertEquals(81.6f, snapMeasure(BodyMetric.WEIGHT, 81.5f + 0.1f), 0.0001f)
        assertEquals(0f, snapMeasure(BodyMetric.FAT, -3f), 0f)
        assertEquals(BodyMetric.HIPS, BodyMetric.byKey("hips"))
    }

    @Test fun railLabelsNeverOverlapAndStayOnTheStage() {
        val tops = railTops(listOf(120f, 168f, 290f), 60f, 360f)
        assertEquals(120f, tops[0], 0f)
        assertEquals(180f, tops[1], 0f)
        assertEquals(290f, tops[2], 0f)
        val squeezed = railTops(listOf(250f, 260f, 270f), 60f, 300f)
        val sorted = squeezed.sorted()
        assertTrue(sorted.zipWithNext().all { (a, b) -> b - a >= 60f - 0.001f })
        assertTrue(sorted.last() + 60f <= 300f + 0.001f)
    }
}
