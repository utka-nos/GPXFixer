package com.gpxeditor.shared.data.fit

import com.gpxeditor.shared.domain.activity.ActivityDocument
import com.gpxeditor.shared.domain.activity.ActivityPoint
import com.gpxeditor.shared.domain.activity.ActivitySegment
import com.gpxeditor.shared.domain.activity.ActivityTrack
import com.gpxeditor.shared.domain.fit.FitDecodeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FitRawPatcherTest {
    @Test
    fun dropsDeletedRecordsAndPreservesUnknownMessages() {
        val deviceMarker = 0xDEADBEEFL
        val original = buildFile(deviceMarker)

        // Keep records 0 and 2, drop record 1, move record 0.
        val patch = FitPatch(
            survivingRecordIndices = setOf(0, 2),
            positions = mapOf(0 to FitLatLon(latitude = 42.5, longitude = 45.5)),
        )

        val patched = FitRawPatcher.patch(original, patch)
        val result = FitActivityDecoder().decode(patched)

        val success = assertIs<FitDecodeResult.Success>(result)
        val points = success.document.tracks.single().segments.single().points
        assertEquals(2, points.size)

        // Record 0 was moved.
        assertEquals(42.5, points[0].latitude!!, absoluteTolerance = 1e-4)
        assertEquals(45.5, points[0].longitude!!, absoluteTolerance = 1e-4)
        // Record 2 survived untouched (record 1 was dropped).
        assertEquals(41.2, points[1].latitude!!, absoluteTolerance = 1e-4)

        // file_id was preserved (decoder reads time_created into startTime).
        assertEquals("2020-01-01T00:00:00Z", success.document.metadata.startTime)
        // The unknown device_info message survived byte-for-byte.
        assertTrue(containsLittleEndianUInt32(patched, deviceMarker), "device_info bytes should be preserved")
    }

    @Test
    fun patchFromDocumentCollectsSurvivingIndicesAndMoves() {
        val document = ActivityDocument(
            tracks = listOf(
                ActivityTrack(
                    segments = listOf(
                        ActivitySegment(
                            points = listOf(
                                ActivityPoint(latitude = 1.0, longitude = 2.0, sourceIndex = 0),
                                ActivityPoint(latitude = 3.0, longitude = 4.0, sourceIndex = 5),
                                ActivityPoint(heartRateBpm = 120, sourceIndex = 9), // no coordinates
                            ),
                        ),
                    ),
                ),
            ),
        )

        val patch = FitPatch.from(document)

        assertEquals(setOf(0, 5, 9), patch.survivingRecordIndices)
        assertEquals(setOf(0, 5), patch.positions.keys)
        assertEquals(FitLatLon(1.0, 2.0), patch.positions[0])
    }

    private fun buildFile(deviceMarker: Long): ByteArray {
        val builder = FitFileBuilder()
        // file_id (global 0), local 1.
        builder.addDefinition(
            localType = 1,
            globalMessageNumber = 0,
            fields = listOf(
                FieldDef(0, 1, 0x00), // type, enum
                FieldDef(4, 4, 0x86), // time_created, uint32
            ),
        )
        builder.addData(localType = 1, values = listOf(u8(4), u32(946771200L)))

        // record (global 20), local 0.
        builder.addDefinition(
            localType = 0,
            globalMessageNumber = 20,
            fields = listOf(
                FieldDef(253, 4, 0x86), // timestamp
                FieldDef(0, 4, 0x85), // position_lat
                FieldDef(1, 4, 0x85), // position_long
                FieldDef(3, 1, 0x02), // heart_rate
            ),
        )
        builder.addData(0, listOf(u32(946771200L), s32(semicircles(41.0)), s32(semicircles(44.0)), u8(140)))
        builder.addData(0, listOf(u32(946771201L), s32(semicircles(41.1)), s32(semicircles(44.1)), u8(141)))
        builder.addData(0, listOf(u32(946771202L), s32(semicircles(41.2)), s32(semicircles(44.2)), u8(142)))

        // device_info-like unknown message (global 23), local 2.
        builder.addDefinition(
            localType = 2,
            globalMessageNumber = 23,
            fields = listOf(FieldDef(200, 4, 0x86)),
        )
        builder.addData(2, listOf(u32(deviceMarker)))

        return builder.build()
    }

    private fun semicircles(degrees: Double): Long = (degrees * 2147483648.0 / 180.0).toLong()

    private fun containsLittleEndianUInt32(bytes: ByteArray, value: Long): Boolean {
        val target = u32(value)
        outer@ for (start in 0..bytes.size - target.size) {
            for (i in target.indices) {
                if (bytes[start + i] != target[i]) continue@outer
            }
            return true
        }
        return false
    }

    private class FieldDef(val number: Int, val size: Int, val baseType: Int)

    private class FitFileBuilder {
        private val records = ArrayList<Byte>()

        fun addDefinition(localType: Int, globalMessageNumber: Int, fields: List<FieldDef>) {
            records += (0x40 or localType).toByte()
            records += 0x00
            records += 0x00 // little-endian
            records += (globalMessageNumber and 0xFF).toByte()
            records += ((globalMessageNumber shr 8) and 0xFF).toByte()
            records += fields.size.toByte()
            for (field in fields) {
                records += field.number.toByte()
                records += field.size.toByte()
                records += field.baseType.toByte()
            }
        }

        fun addData(localType: Int, values: List<ByteArray>) {
            records += localType.toByte()
            for (value in values) {
                records += value.toList()
            }
        }

        fun build(): ByteArray {
            val header = ByteArray(12)
            header[0] = 12
            header[1] = 0x10
            val dataSize = records.size
            header[4] = (dataSize and 0xFF).toByte()
            header[5] = ((dataSize shr 8) and 0xFF).toByte()
            header[6] = ((dataSize shr 16) and 0xFF).toByte()
            header[7] = ((dataSize shr 24) and 0xFF).toByte()
            header[8] = '.'.code.toByte()
            header[9] = 'F'.code.toByte()
            header[10] = 'I'.code.toByte()
            header[11] = 'T'.code.toByte()

            val body = header + records.toByteArray()
            val crc = FitCrc.compute(body, 0, body.size)
            return body + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
        }
    }
}

private fun u8(value: Int): ByteArray = byteArrayOf((value and 0xFF).toByte())

private fun u32(value: Long): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value shr 8) and 0xFF).toByte(),
    ((value shr 16) and 0xFF).toByte(),
    ((value shr 24) and 0xFF).toByte(),
)

private fun s32(value: Long): ByteArray = u32(value and 0xFFFFFFFFL)
