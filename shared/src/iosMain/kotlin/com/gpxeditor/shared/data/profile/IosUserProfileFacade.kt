package com.gpxeditor.shared.data.profile

import com.gpxeditor.shared.data.imported.documentsDirectory
import com.gpxeditor.shared.domain.profile.UserProfile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL

@OptIn(ExperimentalForeignApi::class)
private class IosUserProfileStorage : UserProfileStorage {
    private val file = documentsDirectory().URLByAppendingPathComponent("settings/user_profile.json")!!

    override fun read(): String? = NSString.stringWithContentsOfURL(
        url = file,
        encoding = NSUTF8StringEncoding,
        error = null,
    ) as String?

    override fun write(content: String?) {
        if (content == null) {
            file.path?.let { path ->
                if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
                    NSFileManager.defaultManager.removeItemAtURL(file, error = null)
                }
            }
            return
        }
        NSFileManager.defaultManager.createDirectoryAtURL(
            file.URLByDeletingLastPathComponent!!,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        check(
            (content as NSString).writeToURL(
                url = file,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null,
            ),
        )
    }
}

/** Callback-based facade that keeps coroutine APIs from leaking into SwiftUI. */
class IosUserProfileFacade {
    private val scope = MainScope()
    private val repository = UserProfileRepository(IosUserProfileStorage())
    private var observeJob: Job? = null

    fun profile(): UserProfile = repository.profile.value

    fun save(profile: UserProfile) = repository.save(profile)

    fun observe(onChange: (UserProfile) -> Unit) {
        observeJob?.cancel()
        observeJob = scope.launch { repository.profile.collect(onChange) }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }
}
