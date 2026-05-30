package com.gpxeditor.android.data.imported

import com.gpxeditor.shared.domain.imported.ports.ImportClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AndroidImportClock : ImportClock {
    override fun nowIsoString(): String {
        return SimpleDateFormat(ISO_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }

    private companion object {
        const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    }
}
