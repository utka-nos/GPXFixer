package com.gpxeditor.shared.data.fit

import com.gpxeditor.shared.domain.activity.ActivityDocument
import kotlin.math.roundToLong

/**
 * Edits a FIT file at the raw-message level while preserving everything the editor
 * does not touch. It walks the original record stream and, for each `record`
 * message (global number 20):
 *  - drops it when its source index is no longer present in [FitPatch];
 *  - rewrites its `position_lat`/`position_long` bytes when the matching point moved.
 *
 * Definition messages and every non-`record` message (file_id, session, lap,
 * device_info, developer/unknown messages, ...) are copied byte-for-byte, then the
 * header data size and CRCs are recomputed. This is the "patch only edited records"
 * strategy that keeps unmodelled information intact across import -> edit -> export.
 */
object FitRawPatcher {
    private const val MESSAGE_RECORD = 20
    private const val FIELD_POSITION_LAT = 0
    private const val FIELD_POSITION_LONG = 1
    private const val SEMICIRCLE_RANGE = 2147483648.0

    fun patch(original: ByteArray, patch: FitPatch): ByteArray {
        if (original.size < 14) {
            throw FitDecodeException("FIT file is too small to patch.")
        }
        val headerSize = original[0].toInt() and 0xFF
        if (headerSize < 12 || headerSize > original.size) {
            throw FitDecodeException("Unsupported FIT header size.")
        }
        val dataSize = readUInt32(original, 4)
        val dataEnd = headerSize + dataSize
        if (dataEnd + 2 > original.size) {
            throw FitDecodeException("FIT file is truncated.")
        }

        val body = ArrayList<Byte>(dataSize)
        val definitions = HashMap<Int, RecordLayout>()
        var recordIndex = 0
        var position = headerSize

        while (position < dataEnd) {
            val recordStart = position
            val header = original[position].toInt() and 0xFF

            when {
                header and 0x80 != 0 -> {
                    val localType = (header shr 5) and 0x03
                    val layout = definitions[localType]
                        ?: throw FitDecodeException("Compressed message references undefined local type $localType.")
                    val length = 1 + layout.dataSize
                    recordIndex = appendDataMessage(body, original, recordStart, length, layout, recordIndex, patch)
                    position += length
                }

                header and 0x40 != 0 -> {
                    val layout = parseDefinition(original, position)
                    definitions[layout.localType] = layout
                    appendRange(body, original, position, position + layout.definitionLength)
                    position += layout.definitionLength
                }

                else -> {
                    val localType = header and 0x0F
                    val layout = definitions[localType]
                        ?: throw FitDecodeException("Data message references undefined local type $localType.")
                    val length = 1 + layout.dataSize
                    recordIndex = appendDataMessage(body, original, recordStart, length, layout, recordIndex, patch)
                    position += length
                }
            }
        }

        return assemble(original, headerSize, body.toByteArray())
    }

    private fun appendDataMessage(
        body: ArrayList<Byte>,
        original: ByteArray,
        recordStart: Int,
        length: Int,
        layout: RecordLayout,
        recordIndex: Int,
        patch: FitPatch,
    ): Int {
        if (layout.globalMessageNumber != MESSAGE_RECORD) {
            appendRange(body, original, recordStart, recordStart + length)
            return recordIndex
        }

        val index = recordIndex
        if (index !in patch.survivingRecordIndices) {
            return recordIndex + 1 // drop the record, but keep the index aligned
        }

        val moved = patch.positions[index]
        if (moved != null && layout.latitudeOffset != null && layout.longitudeOffset != null) {
            val recordBytes = original.copyOfRange(recordStart, recordStart + length)
            writeInt32(recordBytes, layout.latitudeOffset, degreesToSemicircles(moved.latitude), layout.littleEndian)
            writeInt32(recordBytes, layout.longitudeOffset, degreesToSemicircles(moved.longitude), layout.littleEndian)
            recordBytes.forEach(body::add)
        } else {
            appendRange(body, original, recordStart, recordStart + length)
        }
        return recordIndex + 1
    }

    private fun parseDefinition(original: ByteArray, start: Int): RecordLayout {
        var position = start
        val header = original[position++].toInt() and 0xFF
        val localType = header and 0x0F
        position++ // reserved
        val littleEndian = (original[position++].toInt() and 0xFF) == 0
        val globalMessageNumber = readUInt16(original, position, littleEndian)
        position += 2
        val fieldCount = original[position++].toInt() and 0xFF

        var fieldOffset = 1 // skip the record header byte
        var dataSize = 0
        var latitudeOffset: Int? = null
        var longitudeOffset: Int? = null
        repeat(fieldCount) {
            val fieldNumber = original[position++].toInt() and 0xFF
            val size = original[position++].toInt() and 0xFF
            position++ // base type
            if (fieldNumber == FIELD_POSITION_LAT && size == 4) latitudeOffset = fieldOffset
            if (fieldNumber == FIELD_POSITION_LONG && size == 4) longitudeOffset = fieldOffset
            fieldOffset += size
            dataSize += size
        }

        if (header and 0x20 != 0) {
            val developerFieldCount = original[position++].toInt() and 0xFF
            repeat(developerFieldCount) {
                position++ // field number
                dataSize += original[position++].toInt() and 0xFF
                position++ // developer data index
            }
        }

        return RecordLayout(
            localType = localType,
            globalMessageNumber = globalMessageNumber,
            littleEndian = littleEndian,
            dataSize = dataSize,
            latitudeOffset = latitudeOffset,
            longitudeOffset = longitudeOffset,
            definitionLength = position - start,
        )
    }

    private fun assemble(original: ByteArray, headerSize: Int, body: ByteArray): ByteArray {
        val header = original.copyOfRange(0, headerSize)
        header[4] = (body.size and 0xFF).toByte()
        header[5] = ((body.size shr 8) and 0xFF).toByte()
        header[6] = ((body.size shr 16) and 0xFF).toByte()
        header[7] = ((body.size shr 24) and 0xFF).toByte()
        if (headerSize >= 14) {
            val headerCrc = FitCrc.compute(header, 0, 12)
            header[12] = (headerCrc and 0xFF).toByte()
            header[13] = ((headerCrc shr 8) and 0xFF).toByte()
        }

        val withoutCrc = header + body
        val crc = FitCrc.compute(withoutCrc, 0, withoutCrc.size)
        return withoutCrc + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
    }

    private fun appendRange(body: ArrayList<Byte>, source: ByteArray, from: Int, toExclusive: Int) {
        for (index in from until toExclusive) {
            body.add(source[index])
        }
    }

    private fun writeInt32(bytes: ByteArray, offset: Int, value: Long, littleEndian: Boolean) {
        for (i in 0 until 4) {
            val shift = if (littleEndian) 8 * i else 8 * (3 - i)
            bytes[offset + i] = ((value shr shift) and 0xFF).toByte()
        }
    }

    private fun degreesToSemicircles(degrees: Double): Long =
        (degrees * SEMICIRCLE_RANGE / 180.0).roundToLong().coerceIn(-2147483648L, 2147483647L)

    private fun readUInt16(bytes: ByteArray, offset: Int, littleEndian: Boolean = true): Int {
        val first = bytes[offset].toInt() and 0xFF
        val second = bytes[offset + 1].toInt() and 0xFF
        return if (littleEndian) first or (second shl 8) else (first shl 8) or second
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private data class RecordLayout(
        val localType: Int,
        val globalMessageNumber: Int,
        val littleEndian: Boolean,
        val dataSize: Int,
        val latitudeOffset: Int?,
        val longitudeOffset: Int?,
        val definitionLength: Int,
    )
}

/** What survived an edit, expressed against source FIT record indices. */
data class FitPatch(
    val survivingRecordIndices: Set<Int>,
    val positions: Map<Int, FitLatLon>,
) {
    companion object {
        fun from(document: ActivityDocument): FitPatch {
            val surviving = HashSet<Int>()
            val positions = HashMap<Int, FitLatLon>()
            document.tracks.forEach { track ->
                track.segments.forEach { segment ->
                    segment.points.forEach { point ->
                        val index = point.sourceIndex ?: return@forEach
                        surviving += index
                        if (point.latitude != null && point.longitude != null) {
                            positions[index] = FitLatLon(point.latitude, point.longitude)
                        }
                    }
                }
            }
            return FitPatch(surviving, positions)
        }
    }
}

data class FitLatLon(
    val latitude: Double,
    val longitude: Double,
)
