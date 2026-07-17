package com.gpxeditor.android.data.profile

import android.content.Context
import com.gpxeditor.shared.data.profile.UserProfileStorage
import java.io.File

class FileUserProfileStorage(context: Context) : UserProfileStorage {
    private val file = File(context.filesDir, "settings/user_profile.json")

    override fun read(): String? = file.takeIf(File::isFile)?.readText()

    override fun write(content: String?) {
        if (content == null) {
            file.delete()
        } else {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(content)
            check(temporary.renameTo(file)) { "Could not persist user profile" }
        }
    }
}
