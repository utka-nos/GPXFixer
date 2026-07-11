package com.gpxeditor.shared.core.time

import java.util.TimeZone

internal actual fun utcOffsetSecondsAt(unixSeconds: Long): Long =
    TimeZone.getDefault().getOffset(unixSeconds * 1000L) / 1000L
