package com.gpxeditor.shared.domain.profile

enum class Sex {
    MALE,
    FEMALE,
}

/**
 * Five heart rate zones (Z1–Z5) described by the four boundaries between them.
 * `upperBoundsBpm[i]` is the inclusive top of zone `i + 1`; Z5 has no upper bound.
 */
data class HeartRateZones(val upperBoundsBpm: List<Int>) {
    init {
        require(upperBoundsBpm.size == BOUNDARY_COUNT) {
            "Heart rate zones need exactly $BOUNDARY_COUNT boundaries"
        }
    }

    companion object {
        const val ZONE_COUNT = 5
        const val BOUNDARY_COUNT = ZONE_COUNT - 1
    }
}

/**
 * Seven Coggan power zones (Z1–Z7) described by the six boundaries between them.
 * `upperBoundsWatts[i]` is the inclusive top of zone `i + 1`; Z7 has no upper bound.
 */
data class PowerZones(val upperBoundsWatts: List<Int>) {
    init {
        require(upperBoundsWatts.size == BOUNDARY_COUNT) {
            "Power zones need exactly $BOUNDARY_COUNT boundaries"
        }
    }

    companion object {
        const val ZONE_COUNT = 7
        const val BOUNDARY_COUNT = ZONE_COUNT - 1
    }
}

/** Global user profile; every field is optional so features consuming one can stay hidden until it is set. */
data class UserProfile(
    val weightKg: Double? = null,
    /** Weight of the bike; consumers fall back to [UserProfile.DEFAULT_BIKE_WEIGHT_KG] when unset. */
    val bikeWeightKg: Double? = null,
    val sex: Sex? = null,
    val birthYear: Int? = null,
    val heartRateZones: HeartRateZones? = null,
    val ftpWatts: Int? = null,
    val powerZones: PowerZones? = null,
) {
    companion object {
        val EMPTY = UserProfile()

        /** Assumed bike weight while [bikeWeightKg] is unset, so power maths always has a total mass. */
        const val DEFAULT_BIKE_WEIGHT_KG = 9.0
    }
}
