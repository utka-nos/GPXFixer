package com.gpxeditor.shared.core.time

/** Offset of the device time zone from UTC, in seconds, at the given moment. */
internal expect fun utcOffsetSecondsAt(unixSeconds: Long): Long
