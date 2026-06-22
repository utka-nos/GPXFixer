package com.gpxeditor.shared.data.fit

/**
 * Low-level decoder for the FIT record stream. It walks definition and data
 * messages (including compressed-timestamp data messages) and produces a flat
 * list of [FitMessage]s keyed by global message number. Mapping those messages
 * into the domain model is the job of [FitActivityDecoder].
 */
internal class FitMessageDecoder(
    private val bytes: ByteArray,
    private val start: Int,
    private val end: Int,
) {
    private var position = start
    private val definitions = HashMap<Int, FitDefinition>()
    private var lastTimestamp: Long? = null

    fun decode(): List<FitMessage> {
        val messages = ArrayList<FitMessage>()
        while (position < end) {
            val header = readByte()
            if (header and 0x80 != 0) {
                messages += readCompressedTimestampMessage(header)
            } else if (header and 0x40 != 0) {
                readDefinitionMessage(header)
            } else {
                messages += readDataMessage(header and 0x0F)
            }
        }
        return messages
    }

    private fun readDefinitionMessage(header: Int) {
        val localType = header and 0x0F
        readByte() // reserved
        val littleEndian = readByte() == 0
        val globalMessageNumber = readUInt16(littleEndian)
        val fieldCount = readByte()
        val fields = ArrayList<FitFieldDefinition>(fieldCount)
        repeat(fieldCount) {
            val fieldNumber = readByte()
            val size = readByte()
            val baseType = readByte()
            fields += FitFieldDefinition(fieldNumber, size, baseType)
        }

        var developerFieldsSize = 0
        if (header and 0x20 != 0) {
            val developerFieldCount = readByte()
            repeat(developerFieldCount) {
                readByte() // field number
                developerFieldsSize += readByte() // size
                readByte() // developer data index
            }
        }

        definitions[localType] = FitDefinition(
            globalMessageNumber = globalMessageNumber,
            littleEndian = littleEndian,
            fields = fields,
            developerFieldsSize = developerFieldsSize,
        )
    }

    private fun readDataMessage(localType: Int): FitMessage {
        val definition = definitions[localType]
            ?: throw FitDecodeException("Data message references undefined local type $localType.")
        val fields = readFields(definition)
        (fields[FIELD_TIMESTAMP]?.intValue())?.let { lastTimestamp = it }
        return FitMessage(definition.globalMessageNumber, fields)
    }

    private fun readCompressedTimestampMessage(header: Int): FitMessage {
        val localType = (header shr 5) and 0x03
        val timeOffset = (header and 0x1F).toLong()
        val definition = definitions[localType]
            ?: throw FitDecodeException("Compressed message references undefined local type $localType.")
        val fields = HashMap(readFields(definition))

        val previous = lastTimestamp
        if (previous != null) {
            val timestamp = if (timeOffset >= (previous and 0x1F)) {
                (previous and 0x1FL.inv()) + timeOffset
            } else {
                (previous and 0x1FL.inv()) + timeOffset + 0x20
            }
            lastTimestamp = timestamp
            fields[FIELD_TIMESTAMP] = FitFieldValue(integers = listOf(timestamp), string = null)
        }
        return FitMessage(definition.globalMessageNumber, fields)
    }

    private fun readFields(definition: FitDefinition): Map<Int, FitFieldValue> {
        val fields = HashMap<Int, FitFieldValue>(definition.fields.size)
        for (field in definition.fields) {
            fields[field.fieldNumber] = readFieldValue(field, definition.littleEndian)
        }
        position += definition.developerFieldsSize
        if (position > end) {
            throw FitDecodeException("FIT data message overruns the record stream.")
        }
        return fields
    }

    private fun readFieldValue(field: FitFieldDefinition, littleEndian: Boolean): FitFieldValue {
        val fieldStart = position
        val fieldEnd = fieldStart + field.size
        if (fieldEnd > end) {
            throw FitDecodeException("FIT field overruns the record stream.")
        }

        val baseType = FitBaseType.forByte(field.baseType)
        val value = if (baseType.isString) {
            FitFieldValue(integers = emptyList(), string = readString(fieldStart, field.size))
        } else {
            val elementSize = baseType.size
            val count = if (elementSize > 0) field.size / elementSize else 0
            val integers = ArrayList<Long>(count)
            var elementPosition = fieldStart
            repeat(count) {
                val raw = readRaw(elementPosition, elementSize, littleEndian)
                if (raw != baseType.invalid) {
                    integers += baseType.interpret(raw, elementSize)
                }
                elementPosition += elementSize
            }
            FitFieldValue(integers = integers, string = null)
        }

        position = fieldEnd
        return value
    }

    private fun readString(offset: Int, size: Int): String? {
        var length = 0
        while (length < size && bytes[offset + length].toInt() != 0) {
            length++
        }
        if (length == 0) {
            return null
        }
        val slice = bytes.copyOfRange(offset, offset + length)
        return slice.decodeToString().trim().takeIf { it.isNotEmpty() }
    }

    private fun readRaw(offset: Int, size: Int, littleEndian: Boolean): Long {
        var raw = 0L
        if (littleEndian) {
            for (i in 0 until size) {
                raw = raw or ((bytes[offset + i].toLong() and 0xFF) shl (8 * i))
            }
        } else {
            for (i in 0 until size) {
                raw = (raw shl 8) or (bytes[offset + i].toLong() and 0xFF)
            }
        }
        return raw
    }

    private fun readByte(): Int {
        if (position >= end) {
            throw FitDecodeException("Unexpected end of FIT record stream.")
        }
        return bytes[position++].toInt() and 0xFF
    }

    private fun readUInt16(littleEndian: Boolean): Int {
        val value = readRaw(position, 2, littleEndian).toInt()
        position += 2
        return value
    }

    private companion object {
        const val FIELD_TIMESTAMP = 253
    }
}

internal data class FitMessage(
    val globalMessageNumber: Int,
    val fields: Map<Int, FitFieldValue>,
) {
    fun field(number: Int): FitFieldValue? = fields[number]
}

internal class FitFieldValue(
    private val integers: List<Long>,
    private val string: String?,
) {
    fun intValue(): Long? = integers.firstOrNull()

    fun doubleValue(): Double? = integers.firstOrNull()?.toDouble()

    fun stringValue(): String? = string
}

internal data class FitFieldDefinition(
    val fieldNumber: Int,
    val size: Int,
    val baseType: Int,
)

internal data class FitDefinition(
    val globalMessageNumber: Int,
    val littleEndian: Boolean,
    val fields: List<FitFieldDefinition>,
    val developerFieldsSize: Int,
)

internal class FitDecodeException(message: String) : Exception(message)

/**
 * FIT base type table indexed by the low 5 bits of the base type byte. Each
 * entry carries its element size and the sentinel that marks an invalid value.
 */
internal class FitBaseType private constructor(
    val size: Int,
    private val signed: Boolean,
    val isString: Boolean,
    val invalid: Long,
) {
    fun interpret(raw: Long, size: Int): Long {
        if (!signed || size >= 8) {
            return raw
        }
        val signBit = 1L shl (size * 8 - 1)
        return if (raw and signBit != 0L) raw - (1L shl (size * 8)) else raw
    }

    companion object {
        private val TABLE = arrayOf(
            FitBaseType(1, signed = false, isString = false, invalid = 0xFFL), // 0 enum
            FitBaseType(1, signed = true, isString = false, invalid = 0x7FL), // 1 sint8
            FitBaseType(1, signed = false, isString = false, invalid = 0xFFL), // 2 uint8
            FitBaseType(2, signed = true, isString = false, invalid = 0x7FFFL), // 3 sint16
            FitBaseType(2, signed = false, isString = false, invalid = 0xFFFFL), // 4 uint16
            FitBaseType(4, signed = true, isString = false, invalid = 0x7FFFFFFFL), // 5 sint32
            FitBaseType(4, signed = false, isString = false, invalid = 0xFFFFFFFFL), // 6 uint32
            FitBaseType(1, signed = false, isString = true, invalid = 0x00L), // 7 string
            FitBaseType(4, signed = false, isString = false, invalid = 0xFFFFFFFFL), // 8 float32
            FitBaseType(8, signed = false, isString = false, invalid = -1L), // 9 float64
            FitBaseType(1, signed = false, isString = false, invalid = 0x00L), // 10 uint8z
            FitBaseType(2, signed = false, isString = false, invalid = 0x00L), // 11 uint16z
            FitBaseType(4, signed = false, isString = false, invalid = 0x00L), // 12 uint32z
            FitBaseType(1, signed = false, isString = false, invalid = 0xFFL), // 13 byte
            FitBaseType(8, signed = true, isString = false, invalid = 0x7FFFFFFFFFFFFFFFL), // 14 sint64
            FitBaseType(8, signed = false, isString = false, invalid = -1L), // 15 uint64
            FitBaseType(8, signed = false, isString = false, invalid = 0x00L), // 16 uint64z
        )

        fun forByte(baseTypeByte: Int): FitBaseType {
            val index = baseTypeByte and 0x1F
            return TABLE.getOrElse(index) { TABLE[13] }
        }
    }
}
