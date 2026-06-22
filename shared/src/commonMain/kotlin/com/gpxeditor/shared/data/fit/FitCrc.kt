package com.gpxeditor.shared.data.fit

/**
 * CRC-16 used by the FIT protocol, computed one nibble at a time as described in
 * the FIT SDK. Returns the running checksum over `bytes[from until toExclusive]`.
 */
internal object FitCrc {
    private val TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400,
        0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401,
        0x5000, 0x9C01, 0x8801, 0x4400,
    )

    fun compute(bytes: ByteArray, from: Int, toExclusive: Int): Int {
        var crc = 0
        for (index in from until toExclusive) {
            val byte = bytes[index].toInt() and 0xFF
            crc = updateNibble(crc, byte and 0x0F)
            crc = updateNibble(crc, (byte shr 4) and 0x0F)
        }
        return crc
    }

    private fun updateNibble(crc: Int, nibble: Int): Int {
        val tmp = TABLE[crc and 0x0F]
        val shifted = (crc shr 4) and 0x0FFF
        return (shifted xor tmp xor TABLE[nibble]) and 0xFFFF
    }
}
