package com.gpxeditor.android

internal object AndroidImportFileName {
    fun isTelegramProvider(authority: String?): Boolean =
        authority?.startsWith("org.telegram.") == true && authority.endsWith(".provider")

    fun resolveViewIntent(
        title: String?,
        subject: String?,
        clipLabel: String?,
        uriFileName: String?,
        mimeType: String?,
    ): String? {
        val intentFileName = sequenceOf(title, subject, clipLabel)
            .mapNotNull { it.cleanFileName() }
            .firstOrNull { it.hasTrackExtension() }
            ?: sequenceOf(title, subject, clipLabel)
                .mapNotNull { it.cleanFileName() }
                .firstOrNull { !it.isOpaqueNumericName() }

        return (intentFileName ?: uriFileName.cleanFileName())
            ?.withExtensionFor(mimeType)
    }

    fun resolve(
        queriedDisplayName: String?,
        documentId: String?,
        lastPathSegment: String?,
    ): String? {
        val displayName = queriedDisplayName.cleanFileName()
        val uriFileName = sequenceOf(documentId, lastPathSegment)
            .mapNotNull { it.fileNameFromUriPart() }
            .firstOrNull { !it.isOpaqueNumericName() }
            ?: sequenceOf(documentId, lastPathSegment)
                .mapNotNull { it.fileNameFromUriPart() }
                .firstOrNull()

        return when {
            displayName == null -> uriFileName
            displayName.isOpaqueNumericName() && uriFileName != null && !uriFileName.isOpaqueNumericName() -> uriFileName
            else -> displayName
        }
    }

    private fun String?.fileNameFromUriPart(): String? = this
        ?.substringAfterLast('/')
        ?.substringAfterLast(':')
        .cleanFileName()

    private fun String?.cleanFileName(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun String.isOpaqueNumericName(): Boolean {
        val stem = substringBeforeLast('.', this)
        return stem.isNotEmpty() && stem.all(Char::isDigit)
    }

    private fun String.hasTrackExtension(): Boolean =
        endsWith(".gpx", ignoreCase = true) || endsWith(".fit", ignoreCase = true)

    private fun String.withExtensionFor(mimeType: String?): String = when {
        hasTrackExtension() -> this
        mimeType.equals("application/vnd.ant.fit", ignoreCase = true) -> "$this.fit"
        mimeType.equals("application/gpx+xml", ignoreCase = true) -> "$this.gpx"
        else -> this
    }
}
