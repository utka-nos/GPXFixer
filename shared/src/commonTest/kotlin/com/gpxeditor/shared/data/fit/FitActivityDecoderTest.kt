package com.gpxeditor.shared.data.fit

import com.gpxeditor.shared.domain.fit.FitDecodeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FitActivityDecoderTest {
    @Test
    fun decodesRecordMessagesIntoActivityPoints() {
        val builder = FitFileBuilder()
        // Definition for the record message (global number 20), local type 0.
        builder.addDefinition(
            localType = 0,
            globalMessageNumber = 20,
            fields = listOf(
                FieldDef(number = 253, size = 4, baseType = 0x86), // timestamp, uint32
                FieldDef(number = 0, size = 4, baseType = 0x85), // position_lat, sint32
                FieldDef(number = 1, size = 4, baseType = 0x85), // position_long, sint32
                FieldDef(number = 2, size = 2, baseType = 0x84), // altitude, uint16
                FieldDef(number = 3, size = 1, baseType = 0x02), // heart_rate, uint8
            ),
        )
        // 2020-01-01T00:00:00Z == FIT timestamp 946771200.
        builder.addRecord(
            localType = 0,
            values = listOf(
                u32(946771200L),
                s32(degreesToSemicircles(41.7151)),
                s32(degreesToSemicircles(44.8271)),
                u16(((512.4 + 500.0) * 5.0).toInt()),
                u8(142),
            ),
        )

        val result = FitActivityDecoder().decode(builder.build())

        val success = assertIs<FitDecodeResult.Success>(result)
        val point = success.document.tracks.single().segments.single().points.single()
        assertEquals("2020-01-01T00:00:00Z", point.time)
        assertEquals(41.7151, point.latitude!!, absoluteTolerance = 1e-4)
        assertEquals(44.8271, point.longitude!!, absoluteTolerance = 1e-4)
        assertEquals(512.4, point.elevationMeters!!, absoluteTolerance = 1e-1)
        assertEquals(142, point.heartRateBpm)
    }

    @Test
    fun dropsInvalidFieldValues() {
        val builder = FitFileBuilder()
        builder.addDefinition(
            localType = 0,
            globalMessageNumber = 20,
            fields = listOf(
                FieldDef(number = 0, size = 4, baseType = 0x85), // position_lat
                FieldDef(number = 1, size = 4, baseType = 0x85), // position_long
                FieldDef(number = 3, size = 1, baseType = 0x02), // heart_rate
            ),
        )
        builder.addRecord(
            localType = 0,
            values = listOf(
                s32(degreesToSemicircles(41.7151)),
                s32(degreesToSemicircles(44.8271)),
                u8(0xFF), // invalid uint8 sentinel
            ),
        )

        val result = FitActivityDecoder().decode(builder.build())

        val success = assertIs<FitDecodeResult.Success>(result)
        val point = success.document.tracks.single().segments.single().points.single()
        assertEquals(41.7151, point.latitude!!, absoluteTolerance = 1e-4)
        assertNull(point.heartRateBpm)
    }

    @Test
    fun returnsFailureOnCrcMismatch() {
        val builder = FitFileBuilder()
        builder.addDefinition(
            localType = 0,
            globalMessageNumber = 20,
            fields = listOf(FieldDef(number = 3, size = 1, baseType = 0x02)),
        )
        builder.addRecord(localType = 0, values = listOf(u8(120)))
        val bytes = builder.build()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte() // corrupt the CRC

        val result = FitActivityDecoder().decode(bytes)

        val failure = assertIs<FitDecodeResult.Failure>(result)
        assertEquals("Invalid FIT CRC.", failure.error.message)
    }

    @Test
    fun returnsFailureWhenSignatureMissing() {
        val bytes = ByteArray(16) // valid 14-byte header size, but no ".FIT" signature
        bytes[0] = 14

        val result = FitActivityDecoder().decode(bytes)

        val failure = assertIs<FitDecodeResult.Failure>(result)
        assertEquals("Not a FIT file.", failure.error.message)
    }

    private fun degreesToSemicircles(degrees: Double): Long {
        return (degrees * 2147483648.0 / 180.0).toLong()
    }

    private class FieldDef(val number: Int, val size: Int, val baseType: Int)

    /** Minimal FIT file writer: a 12-byte header, a record stream, and a trailing CRC. */
    private class FitFileBuilder {
        private val records = ArrayList<Byte>()

        fun addDefinition(localType: Int, globalMessageNumber: Int, fields: List<FieldDef>) {
            records += (0x40 or localType).toByte() // definition message header
            records += 0x00 // reserved
            records += 0x00 // architecture: little-endian
            records += (globalMessageNumber and 0xFF).toByte()
            records += ((globalMessageNumber shr 8) and 0xFF).toByte()
            records += fields.size.toByte()
            for (field in fields) {
                records += field.number.toByte()
                records += field.size.toByte()
                records += field.baseType.toByte()
            }
        }

        fun addRecord(localType: Int, values: List<ByteArray>) {
            records += localType.toByte() // data message header
            for (value in values) {
                records += value.toList()
            }
        }

        fun build(): ByteArray {
            val header = ByteArray(12)
            header[0] = 12 // header size
            header[1] = 0x10 // protocol version
            // profile version bytes [2..3] left as 0
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

private fun u16(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value shr 8) and 0xFF).toByte(),
)

private fun u32(value: Long): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value shr 8) and 0xFF).toByte(),
    ((value shr 16) and 0xFF).toByte(),
    ((value shr 24) and 0xFF).toByte(),
)

private fun s32(value: Long): ByteArray = u32(value and 0xFFFFFFFFL)
