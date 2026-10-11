package com.fitclub.tracker.domain

import com.fitclub.tracker.data.db.BodyMeasurementEntity
import org.junit.Assert.*
import org.junit.Test

class BodyModelTest {
    private fun row(day: Long, weight: Float? = null, waist: Float? = null, hips: Float? = null, chest: Float? = null) =
        BodyMeasurementEntity(epochDay = day, weightKg = weight, waistCm = waist, fatPercent = null, chestCm = chest, armCm = null, hipsCm = hips)

    @Test fun everyGirthHasABandOnAllFourFiguresInsideTheDrawing() {
        for (figure in BodyFigure.ALL) {
            assertEquals(BodyMetric.entries.filter { it.isGirth }.toSet(), figure.bands.map { it.metric }.toSet())
            for (b in figure.bands) {
                assertTrue(figure.asset, b.cx - b.half >= 0f && b.cx + b.half <= figure.width)
                assertTrue(figure.asset, b.y in 0f..figure.height)
            }
            val y = figure.bands.associate { it.metric to it.y }
            assertTrue(y.getValue(BodyMetric.SHOULDERS) < y.getValue(BodyMetric.CHEST))
            assertTrue(y.getValue(BodyMetric.CHEST) < y.getValue(BodyMetric.WAIST))
            assertTrue(y.getValue(BodyMetric.WAIST) < y.getValue(BodyMetric.HIPS))
            assertTrue(y.getValue(BodyMetric.HIPS) < y.getValue(BodyMetric.THIGH))
            assertEquals(3, figure.bands.count { it.rightSide })
        }
        assertEquals(BodyFigure.FEMALE_DARK, BodyFigure.of(female = true, dark = true))
        assertEquals(BodyFigure.MALE_LIGHT, BodyFigure.of(female = false, dark = false))
    }

    @Test fun valueOnADateIsTheLatestRecordedUpToThatDay() {
        val rows = listOf(row(1, weight = 80f, waist = 88f), row(15, weight = 79f), row(30, waist = 85f, hips = 100f))
        assertEquals(88f, valueAsOf(rows, 1, BodyMetric.WAIST))
        assertEquals(79f, valueAsOf(rows, 2, BodyMetric.WEIGHT))
        assertNull(valueAsOf(rows, 1, BodyMetric.HIPS))
        assertEquals(100f, valueAsOf(rows, 2, BodyMetric.HIPS))
        assertNull(valueAsOf(rows, 5, BodyMetric.WAIST))
        assertEquals(88f, valueOnDay(rows, 29, BodyMetric.WAIST))
        assertEquals(85f, valueOnDay(rows, 30, BodyMetric.WAIST))
        assertNull(valueOnDay(rows, 0, BodyMetric.WAIST))
    }

    @Test fun weeklySeriesCarriesTheLastKnownValueAndEndsToday() {
        val rows = listOf(row(10, waist = 88f), row(40, waist = 85f), row(60, waist = 82f))
        val series = weeklySeries(rows, BodyMetric.WAIST, today = 60, weeks = 8)
        // Week ends: 11, 18, 25, 32, 39, 46, 53, 60.
        assertEquals(listOf(88f, 88f, 88f, 88f, 88f, 85f, 85f, 82f), series)
        val change = periodChange(BodyMetric.WAIST, series)!!
        assertEquals(-6f, change.delta, 0.001f)
        assertEquals(-6.8f, change.percent, 0.001f)
        assertEquals(true, change.good)
        assertNull(periodChange(BodyMetric.WAIST, listOf(null, null, 82f)))
        assertEquals(PeriodChange(0f, 0f, null), periodChange(BodyMetric.HIPS, listOf(null, 100f, 100f)))
        assertEquals(false, periodChange(BodyMetric.HIPS, listOf(97f, 98f))!!.good)
    }

    @Test fun wholeFigureFitsTheCardOnEveryPhoneWidth() {
        for (figure in BodyFigure.ALL) for (card in listOf(288f, 328f, 358f, 380f)) {
            val h = figureHeight(figure, card)
            assertTrue(h <= 400f)
            // The figure plus both callout columns never exceed the card.
            assertTrue("${figure.asset} @ $card", h * figure.aspect + 2 * 86f <= card + 0.01f)
        }
        assertEquals(400f, figureHeight(BodyFigure.MALE_LIGHT, 358f), 0.01f)
        assertTrue(figureHeight(BodyFigure.MALE_DARK, 328f) < 400f)
    }

    @Test fun zonesHaveTheReferenceColours() {
        assertEquals(BodyHue.BLUE, BodyMetric.SHOULDERS.hue)
        assertEquals(BodyHue.BLUE, BodyMetric.CHEST.hue)
        assertEquals(BodyHue.PURPLE, BodyMetric.ARM.hue)
        assertEquals(BodyHue.PURPLE, BodyMetric.HIPS.hue)
        assertEquals(BodyHue.CYAN, BodyMetric.WAIST.hue)
        assertEquals(BodyHue.CYAN, BodyMetric.THIGH.hue)
        assertEquals("Бицепс", BodyMetric.ARM.title)
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
