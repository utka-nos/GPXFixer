package com.gpxeditor.android.recording

import android.content.Context
import com.gpxeditor.shared.data.ble.HeartRateSensorSettingsStorage
import java.io.File

class FileHeartRateSensorSettingsStorage(context: Context) : HeartRateSensorSettingsStorage {
    private val file = File(context.filesDir, "settings/heart_rate_sensor.json")

    override fun read(): String? = file.takeIf(File::isFile)?.readText()

    override fun write(content: String?) {
        if (content == null) {
            file.delete()
        } else {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(content)
            check(temporary.renameTo(file)) { "Could not persist heart rate sensor settings" }
        }
    }
}
