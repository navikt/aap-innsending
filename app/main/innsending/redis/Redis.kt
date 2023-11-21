package innsending.redis

import innsending.RedisConfig
import io.ktor.util.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Socket

const val EnDag: Long = 60 * 60 * 24


open class Redis(private val config: RedisConfig) {
    internal open fun connect(): Managed = ManagedImpl(config).also {
        it.call("AUTH", config.username, config.password)
    }

    operator fun set(key: String, value: ByteArray): Unit = connect().use {
        it.call("SET", key, value)
    }

    operator fun get(key: String): ByteArray? = connect().use {
        it.call("GET", key)
    }

    fun expire(key: String, seconds: Long): Unit = connect().use {
        it.call("EXPIRE", key, seconds)
    }

    fun del(key: String): Unit = connect().use {
        it.call("DEL", key)
    }

    fun ready(): Boolean = connect().use {
        it.call("PING")?.encodeBase64() == "PONG"
    }

    interface Managed : AutoCloseable {
        fun call(vararg args: Any): ByteArray?
    }

    class ManagedImpl(config: RedisConfig) : Managed, AutoCloseable {
        private val socket = Socket(config.uri.host, config.uri.port)
        private val writer = Encoder(BufferedOutputStream(socket.getOutputStream()))
        private val reader = Parser(BufferedInputStream(socket.getInputStream()))

        // See [docs](https://redis.io/commands) for all commands
        override fun call(vararg args: Any): ByteArray? {
            writer.write(args.toList())
            writer.flush()
            return reader.parse()
        }

//        private inline fun <reified T : Any> read(): T? = reader.parse() as T?

        override fun close() = socket.close()
    }
}
