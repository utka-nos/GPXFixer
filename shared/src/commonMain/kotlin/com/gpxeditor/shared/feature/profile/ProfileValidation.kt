package com.gpxeditor.shared.feature.profile

enum class ZoneBoundsError {
    OUT_OF_RANGE,
    NOT_ASCENDING,
}

/** Sanity limits and boundary checks shared by both platform profile screens. */
object ProfileValidation {
    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 400.0
    const val MIN_BIRTH_YEAR = 1900
    const val MIN_HEART_RATE_BPM = 40
    const val MAX_HEART_RATE_BPM = 250
    const val MIN_FTP_WATTS = 30
    const val MAX_FTP_WATTS = 2000
    const val MIN_POWER_BOUND_WATTS = 1
    const val MAX_POWER_BOUND_WATTS = 3000

    fun isValidWeight(weightKg: Double): Boolean = weightKg in MIN_WEIGHT_KG..MAX_WEIGHT_KG

    fun isValidBirthYear(birthYear: Int, currentYear: Int): Boolean = birthYear in MIN_BIRTH_YEAR..currentYear

    fun isValidMaxHeartRate(maxHeartRateBpm: Int): Boolean =
        maxHeartRateBpm in MIN_HEART_RATE_BPM..MAX_HEART_RATE_BPM

    fun isValidFtp(ftpWatts: Int): Boolean = ftpWatts in MIN_FTP_WATTS..MAX_FTP_WATTS

    fun validateHeartRateBounds(upperBoundsBpm: List<Int>): ZoneBoundsError? =
        validateBounds(upperBoundsBpm, MIN_HEART_RATE_BPM, MAX_HEART_RATE_BPM)

    fun validatePowerBounds(upperBoundsWatts: List<Int>): ZoneBoundsError? =
        validateBounds(upperBoundsWatts, MIN_POWER_BOUND_WATTS, MAX_POWER_BOUND_WATTS)

    private fun validateBounds(bounds: List<Int>, minimum: Int, maximum: Int): ZoneBoundsError? {
        if (bounds.any { it < minimum || it > maximum }) return ZoneBoundsError.OUT_OF_RANGE
        if (bounds.zipWithNext().any { (lower, upper) -> lower >= upper }) return ZoneBoundsError.NOT_ASCENDING
        return null
    }
}
