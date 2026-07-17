package com.gpxeditor.shared.feature.profile

import com.gpxeditor.shared.domain.profile.HeartRateZones
import com.gpxeditor.shared.domain.profile.PowerZones
import kotlin.math.roundToInt

/** One-tap zone defaults; every boundary stays editable afterwards. */
object ZoneDefaults {
    /** Widely used 60/70/80/90 % of max split for five heart rate zones. */
    private val HEART_RATE_BOUNDARY_FRACTIONS = listOf(0.60, 0.70, 0.80, 0.90)

    /** Coggan zone tops: 55/75/90/105/120/150 % of FTP; Z7 is open-ended. */
    private val POWER_BOUNDARY_FRACTIONS = listOf(0.55, 0.75, 0.90, 1.05, 1.20, 1.50)

    fun estimatedMaxHeartRate(birthYear: Int, currentYear: Int): Int = 220 - (currentYear - birthYear)

    fun heartRateZonesFromMax(maxHeartRateBpm: Int): HeartRateZones =
        HeartRateZones(HEART_RATE_BOUNDARY_FRACTIONS.map { (it * maxHeartRateBpm).roundToInt() })

    fun powerZonesFromFtp(ftpWatts: Int): PowerZones =
        PowerZones(POWER_BOUNDARY_FRACTIONS.map { (it * ftpWatts).roundToInt() })
}
