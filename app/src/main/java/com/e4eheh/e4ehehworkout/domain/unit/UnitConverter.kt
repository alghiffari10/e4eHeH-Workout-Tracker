package com.e4eheh.e4ehehworkout.domain.unit

import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure canonical unit conversion boundary between stored metric values (`kg`, `km`)
 * and presentation/input units (`kg`/`lb`, `km`/`mi`).
 */
object UnitConverter {
    const val KG_PER_LB = 0.45359237
    const val KM_PER_MI = 1.609344

    fun weightToDisplay(canonicalKg: Double, unit: WeightUnit): Double =
        convert(canonicalKg) { value ->
            when (unit) {
                WeightUnit.KG -> value
                WeightUnit.LB -> value / KG_PER_LB
            }
        }

    fun weightToCanonicalKg(displayValue: Double, unit: WeightUnit): Double =
        convert(displayValue) { value ->
            when (unit) {
                WeightUnit.KG -> value
                WeightUnit.LB -> value * KG_PER_LB
            }
        }

    fun distanceToDisplay(canonicalKm: Double, unit: DistanceUnit): Double =
        convert(canonicalKm) { value ->
            when (unit) {
                DistanceUnit.KM -> value
                DistanceUnit.MI -> value / KM_PER_MI
            }
        }

    fun distanceToCanonicalKm(displayValue: Double, unit: DistanceUnit): Double =
        convert(displayValue) { value ->
            when (unit) {
                DistanceUnit.KM -> value
                DistanceUnit.MI -> value * KM_PER_MI
            }
        }

    fun formatWeight(canonicalKg: Double, unit: WeightUnit): String =
        "${formatDecimal(weightToDisplay(canonicalKg, unit))} ${unit.symbol}"

    fun formatDistance(canonicalKm: Double, unit: DistanceUnit): String =
        "${formatDecimal(distanceToDisplay(canonicalKm, unit))} ${unit.symbol}"

    private inline fun convert(value: Double, transform: (Double) -> Double): Double {
        require(value.isFinite() && value >= 0.0) {
            "Measurement value must be finite and non-negative, got $value"
        }
        if (value == 0.0) return 0.0
        return transform(value)
    }

    private fun formatDecimal(value: Double, maxDecimals: Int = 2): String {
        val rounded = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP)
        return if (rounded.compareTo(BigDecimal.ZERO) == 0) {
            "0"
        } else {
            rounded.stripTrailingZeros().toPlainString()
        }
    }
}
