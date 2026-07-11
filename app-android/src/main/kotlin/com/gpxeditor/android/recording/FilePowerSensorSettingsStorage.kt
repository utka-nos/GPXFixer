package com.gpxeditor.android.recording

import android.content.Context
import com.gpxeditor.shared.data.ble.PowerSensorSettingsStorage
import java.io.File

class FilePowerSensorSettingsStorage(context: Context) : PowerSensorSettingsStorage {
    private val file = File(context.filesDir, "settings/power_sensor.json")

    override fun read(): String? = file.takeIf(File::isFile)?.readText()

    override fun write(content: String?) {
        if (content == null) {
            file.delete()
        } else {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(content)
            check(temporary.renameTo(file)) { "Could not persist power sensor settings" }
        }
    }
}
