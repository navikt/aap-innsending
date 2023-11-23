package no.nav.aap.redis

import java.io.OutputStream

private const val CR = '\r'.code
private const val LF = '\n'.code
private val CRLF = byteArrayOf(LF.toByte(), CR.toByte())

class Encoder(private val out: OutputStream) {

    /**
     * Redis [documentation](https://redis.io/docs/reference/protocol-spec/#arrays)
     */
    fun write(list: List<*>) {
        out.write('*'.toByteArray())
        out.write(list.size.toByteArray())
        out.write(CRLF)
        list.forEach {
            when (it) {
                is ByteArray -> write(it)
                is String -> write(it.encodeToByteArray())
                is Long -> write(it)
                is Int -> write(it.toLong())
                is List<*> -> write(it)
                else -> error("Unsupported type: ${it?.javaClass?.canonicalName}")
            }
        }
    }

    /*
     * Redis [documentation](https://redis.io/docs/reference/protocol-spec/#bulk-strings)
     */
    private fun write(value: ByteArray) {
        out.write('$'.toByteArray())
        out.write(value.size.toByteArray())
        out.write(CRLF)
        out.write(value)
        out.write(CRLF)
    }

    /**
     * Redis [documentation](https://redis.io/docs/reference/protocol-spec/#integers)
     */
    private fun write(value: Long) {
        out.write(':'.toByteArray())
        out.write(value.toByteArray())
        out.write(CRLF)
    }

    fun flush() = out.flush()
}

internal fun Char.toByteArray() = byteArrayOf(code.toByte())
internal fun Number.toByteArray(size: Int = 4) = ByteArray(size) { i -> (toLong() shr (i * 8)).toByte() }
