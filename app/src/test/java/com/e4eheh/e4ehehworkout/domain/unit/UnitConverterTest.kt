package com.e4eheh.e4ehehworkout.domain.unit

import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {
    @Test
    fun weightToDisplay_andToCanonicalKg_preserveMetricValuesIdentically() {
        assertEquals(0.0, UnitConverter.weightToDisplay(0.0, WeightUnit.KG), 0.0)
        assertEquals(72.5, UnitConverter.weightToDisplay(72.5, WeightUnit.KG), 0.0)
        assertEquals(100.0, UnitConverter.weightToCanonicalKg(100.0, WeightUnit.KG), 0.0)
        assertEquals(72.5, UnitConverter.weightToCanonicalKg(72.5, WeightUnit.KG), 0.0)
    }

    @Test
    fun weight_convertsAccuratelyBetweenKgAndLb_andRoundTrips() {
        assertEquals(0.45359237, UnitConverter.weightToCanonicalKg(1.0, WeightUnit.LB), 1e-12)
        assertEquals(102.05828325, UnitConverter.weightToCanonicalKg(225.0, WeightUnit.LB), 1e-9)
        assertEquals(220.46226218487757, UnitConverter.weightToDisplay(100.0, WeightUnit.LB), 1e-9)

        val canonicalFromLb = UnitConverter.weightToCanonicalKg(225.0, WeightUnit.LB)
        assertEquals(225.0, UnitConverter.weightToDisplay(canonicalFromLb, WeightUnit.LB), 1e-9)

        val displayFromKg = UnitConverter.weightToDisplay(100.0, WeightUnit.LB)
        assertEquals(100.0, UnitConverter.weightToCanonicalKg(displayFromKg, WeightUnit.LB), 1e-9)
    }

    @Test
    fun distanceToDisplay_andToCanonicalKm_preserveMetricValuesIdentically() {
        assertEquals(0.0, UnitConverter.distanceToDisplay(0.0, DistanceUnit.KM), 0.0)
        assertEquals(5.0, UnitConverter.distanceToDisplay(5.0, DistanceUnit.KM), 0.0)
        assertEquals(12.34, UnitConverter.distanceToCanonicalKm(12.34, DistanceUnit.KM), 0.0)
    }

    @Test
    fun distance_convertsAccuratelyBetweenKmAndMi_andRoundTrips() {
        assertEquals(1.609344, UnitConverter.distanceToCanonicalKm(1.0, DistanceUnit.MI), 1e-12)
        assertEquals(4.9889664, UnitConverter.distanceToCanonicalKm(3.1, DistanceUnit.MI), 1e-9)
        assertEquals(3.1068559611866697, UnitConverter.distanceToDisplay(5.0, DistanceUnit.MI), 1e-9)

        val canonicalFromMi = UnitConverter.distanceToCanonicalKm(3.1, DistanceUnit.MI)
        assertEquals(3.1, UnitConverter.distanceToDisplay(canonicalFromMi, DistanceUnit.MI), 1e-9)

        val displayFromKm = UnitConverter.distanceToDisplay(5.0, DistanceUnit.MI)
        assertEquals(5.0, UnitConverter.distanceToCanonicalKm(displayFromKm, DistanceUnit.MI), 1e-9)
    }

    @Test
    fun repeatedUnitSwitching_doesNotMutateOrDriftCanonicalValues() {
        val canonicalKg = 80.0
        val firstLb = UnitConverter.weightToDisplay(canonicalKg, WeightUnit.LB)
        val backToKg = UnitConverter.weightToDisplay(canonicalKg, WeightUnit.KG)
        val secondLb = UnitConverter.weightToDisplay(canonicalKg, WeightUnit.LB)

        assertEquals(80.0, backToKg, 0.0)
        assertEquals(firstLb, secondLb, 0.0)

        val canonicalKm = 10.0
        val firstMi = UnitConverter.distanceToDisplay(canonicalKm, DistanceUnit.MI)
        val backToKm = UnitConverter.distanceToDisplay(canonicalKm, DistanceUnit.KM)
        val secondMi = UnitConverter.distanceToDisplay(canonicalKm, DistanceUnit.MI)

        assertEquals(10.0, backToKm, 0.0)
        assertEquals(firstMi, secondMi, 0.0)
    }

    @Test
    fun zeroAndNegativeZero_normalizeToPositiveZero() {
        assertEquals(0L, java.lang.Double.doubleToRawLongBits(UnitConverter.weightToDisplay(-0.0, WeightUnit.KG)))
        assertEquals(0L, java.lang.Double.doubleToRawLongBits(UnitConverter.weightToDisplay(-0.0, WeightUnit.LB)))
        assertEquals(0L, java.lang.Double.doubleToRawLongBits(UnitConverter.weightToCanonicalKg(-0.0, WeightUnit.KG)))
        assertEquals(0L, java.lang.Double.doubleToRawLongBits(UnitConverter.weightToCanonicalKg(-0.0, WeightUnit.LB)))
        assertEquals(0L, java.lang.Double.doubleToRawLongBits(UnitConverter.distanceToDisplay(-0.0, DistanceUnit.KM)))
        assertEquals(0L, java.lang.Double.doubleToRawLongBits(UnitConverter.distanceToDisplay(-0.0, DistanceUnit.MI)))
        assertEquals(0L, java.lang.Double.doubleToRawLongBits(UnitConverter.distanceToCanonicalKm(-0.0, DistanceUnit.KM)))
        assertEquals(0L, java.lang.Double.doubleToRawLongBits(UnitConverter.distanceToCanonicalKm(-0.0, DistanceUnit.MI)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun weightToDisplay_rejectsNegativeValue() {
        UnitConverter.weightToDisplay(-1.0, WeightUnit.KG)
    }

    @Test(expected = IllegalArgumentException::class)
    fun weightToCanonicalKg_rejectsNaN() {
        UnitConverter.weightToCanonicalKg(Double.NaN, WeightUnit.LB)
    }

    @Test(expected = IllegalArgumentException::class)
    fun distanceToDisplay_rejectsPositiveInfinity() {
        UnitConverter.distanceToDisplay(Double.POSITIVE_INFINITY, DistanceUnit.MI)
    }

    @Test(expected = IllegalArgumentException::class)
    fun distanceToCanonicalKm_rejectsNegativeValue() {
        UnitConverter.distanceToCanonicalKm(-0.5, DistanceUnit.KM)
    }

    @Test
    fun formatWeight_andFormatDistance_formatCleanCompactDecimalsWithUnitSymbols() {
        assertEquals("100 kg", UnitConverter.formatWeight(100.0, WeightUnit.KG))
        assertEquals("220.46 lb", UnitConverter.formatWeight(100.0, WeightUnit.LB))
        assertEquals("72.5 kg", UnitConverter.formatWeight(72.5, WeightUnit.KG))
        assertEquals("21.25 kg", UnitConverter.formatWeight(21.25, WeightUnit.KG))
        assertEquals("0 kg", UnitConverter.formatWeight(0.0, WeightUnit.KG))
        assertEquals("0 lb", UnitConverter.formatWeight(0.0, WeightUnit.LB))

        assertEquals("5 km", UnitConverter.formatDistance(5.0, DistanceUnit.KM))
        assertEquals("3.11 mi", UnitConverter.formatDistance(5.0, DistanceUnit.MI))
        assertEquals("0 km", UnitConverter.formatDistance(0.0, DistanceUnit.KM))
        assertEquals("0 mi", UnitConverter.formatDistance(0.0, DistanceUnit.MI))

        assertEquals("220.46", UnitConverter.formatWeightValue(100.0, WeightUnit.LB))
        assertEquals("45", UnitConverter.formatWeightValue(UnitConverter.weightToCanonicalKg(45.0, WeightUnit.LB), WeightUnit.LB))
        assertEquals("3.11", UnitConverter.formatDistanceValue(5.0, DistanceUnit.MI))
        assertEquals("0", UnitConverter.formatDistanceValue(0.0, DistanceUnit.MI))
    }

    @Test
    fun paceToDisplaySecondsPerUnit_andFormatPace_convertAndFormatAccurately() {
        assertEquals(300.0, UnitConverter.paceToDisplaySecondsPerUnit(300.0, DistanceUnit.KM), 0.0)
        assertEquals(482.8032, UnitConverter.paceToDisplaySecondsPerUnit(300.0, DistanceUnit.MI), 1e-6)

        assertEquals("05:00", UnitConverter.formatPaceValue(300.0, DistanceUnit.KM))
        assertEquals("05:00 /km", UnitConverter.formatPace(300.0, DistanceUnit.KM))
        assertEquals("08:03", UnitConverter.formatPaceValue(300.0, DistanceUnit.MI))
        assertEquals("08:03 /mi", UnitConverter.formatPace(300.0, DistanceUnit.MI))
    }

    @Test(expected = IllegalArgumentException::class)
    fun paceToDisplaySecondsPerUnit_rejectsZero() {
        UnitConverter.paceToDisplaySecondsPerUnit(0.0, DistanceUnit.KM)
    }

    @Test
    fun formatDurationClock_andFormatDecimal_formatMonospaceValuesCleanly() {
        assertEquals("00:45", UnitConverter.formatDurationClock(45L))
        assertEquals("01:15", UnitConverter.formatDurationClock(75L))
        assertEquals("01:01:05", UnitConverter.formatDurationClock(3_665L))
        assertEquals("00:00", UnitConverter.formatDurationClock(-10L))

        assertEquals("120", UnitConverter.formatDecimal(120.0, maxDecimals = 1))
        assertEquals("12.5", UnitConverter.formatDecimal(12.54, maxDecimals = 1))
        assertEquals("3.11", UnitConverter.formatDecimal(3.1068, maxDecimals = 2))
    }
}
