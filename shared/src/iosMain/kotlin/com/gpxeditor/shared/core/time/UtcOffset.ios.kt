package com.gpxeditor.shared.core.time

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

internal actual fun utcOffsetSecondsAt(unixSeconds: Long): Long =
    NSTimeZone.localTimeZone
        .secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(unixSeconds.toDouble()))
        .toLong()
