package com.gpxeditor.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidImportFileNameTest {
    @Test
    fun recognizesTelegramContentProvider() {
        assertTrue(AndroidImportFileName.isTelegramProvider("org.telegram.messenger.provider"))
        assertFalse(AndroidImportFileName.isTelegramProvider("com.android.providers.downloads.documents"))
        assertFalse(AndroidImportFileName.isTelegramProvider(null))
    }

    @Test
    fun actionViewPrefersOriginalTitleOverNumericContentUriName() {
        val result = AndroidImportFileName.resolveViewIntent(
            title = "залупа слона.gpx",
            subject = null,
            clipLabel = null,
            uriFileName = "1752400123.gpx",
            mimeType = "application/gpx+xml",
        )

        assertEquals("залупа слона.gpx", result)
    }

    @Test
    fun actionViewUsesClipLabelAndFitMimeTypeWhenTitleIsMissing() {
        val result = AndroidImportFileName.resolveViewIntent(
            title = null,
            subject = null,
            clipLabel = "залупа слона",
            uriFileName = "1752400123",
            mimeType = "application/vnd.ant.fit",
        )

        assertEquals("залупа слона.fit", result)
    }

    @Test
    fun usesFileNameFromDocumentIdWhenProviderReturnsNumericGpxName() {
        val result = AndroidImportFileName.resolve(
            queriedDisplayName = "1752400123.gpx",
            documentId = "primary:Download/залупа слона.gpx",
            lastPathSegment = "primary:Download/залупа слона.gpx",
        )

        assertEquals("залупа слона.gpx", result)
    }

    @Test
    fun usesFileNameFromDocumentIdWhenProviderReturnsNumericFitName() {
        val result = AndroidImportFileName.resolve(
            queriedDisplayName = "1752400123",
            documentId = "primary:Download/залупа слона.fit",
            lastPathSegment = "primary:Download/залупа слона.fit",
        )

        assertEquals("залупа слона.fit", result)
    }

    @Test
    fun keepsNonNumericDisplayNameReportedByProvider() {
        val result = AndroidImportFileName.resolve(
            queriedDisplayName = "залупа слона.gpx",
            documentId = "msf:1752400123",
            lastPathSegment = "1752400123",
        )

        assertEquals("залупа слона.gpx", result)
    }
}
