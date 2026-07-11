package com.gpxeditor.android.recording

import android.content.Context
import java.io.File

/**
 * Append-only journal file for the active recording. Written by
 * [TrackRecordingService] and read back on app start to recover a
 * recording that was interrupted by a process death.
 */
class FileRecordingJournal(context: Context) {
    private val file = File(context.applicationContext.filesDir, "recording/journal.log")

    fun exists(): Boolean = file.exists()

    fun read(): String = if (file.exists()) file.readText() else ""

    fun append(line: String) {
        file.parentFile?.mkdirs()
        file.appendText(line + "\n")
    }

    fun clear() {
        file.delete()
    }
}
