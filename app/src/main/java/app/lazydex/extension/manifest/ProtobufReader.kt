package app.lazydex.extension.manifest

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

class ProtobufReader(private val input: InputStream) {
    constructor(bytes: ByteArray) : this(ByteArrayInputStream(bytes))

    fun hasMore(): Boolean = input.available() > 0

    fun readVarint(): Long {
        var value = 0L
        var shift = 0
        while (shift < 64) {
            val b = input.read()
            if (b == -1) {
                if (shift == 0) return -1L
                throw IllegalStateException("Unexpected EOF while reading varint")
            }
            value = value or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) return value
            shift += 7
        }
        throw IllegalStateException("Varint too long")
    }

    fun readTag(): Pair<Int, Int>? {
        val tag = readVarint()
        if (tag == -1L) return null
        val fieldNumber = (tag ushr 3).toInt()
        val wireType = (tag and 0x07).toInt()
        return Pair(fieldNumber, wireType)
    }

    fun readBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read == -1) throw IllegalStateException("Unexpected EOF while reading bytes")
            offset += read
        }
        return bytes
    }

    fun readString(): String {
        val len = readVarint().toInt()
        val bytes = readBytes(len)
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun skipField(wireType: Int) {
        when (wireType) {
            0 -> readVarint()
            1 -> readBytes(8)
            2 -> {
                val len = readVarint().toInt()
                readBytes(len)
            }
            5 -> readBytes(4)
            else -> throw IllegalStateException("Unsupported wire type: $wireType")
        }
    }
}
