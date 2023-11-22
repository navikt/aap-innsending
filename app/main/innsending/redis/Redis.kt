package innsending.redis

import innsending.RedisConfig
import innsending.SECURE_LOGGER
import io.ktor.util.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Socket
import java.nio.ByteBuffer

const val EnDag: Long = 60 * 60 * 24


open class Redis(private val config: RedisConfig) {
    internal open fun connect(): Managed = ManagedImpl(config).also {
//        SECURE_LOGGER.info("calling AUTH with ${config.username}:${config.password}")
//        it.call("AUTH", config.username, config.password)
    }

    operator fun set(key: String, value: ByteArray): Unit = connect().use {
        SECURE_LOGGER.info("calling SET with $key:$value")
        it.call("SET", key, value)
    }

    operator fun get(key: String): ByteArray? = connect().use {
        SECURE_LOGGER.info("calling GET with $key")
        it.call("GET", key)
    }

    fun expire(key: String, seconds: Long): Unit = connect().use {
        SECURE_LOGGER.info("calling EXPIRE $key:$seconds")
        it.call("EXPIRE", key, seconds)
    }

    fun del(key: String): Unit = connect().use {
        SECURE_LOGGER.info("calling DEL $key")
        it.call("DEL", key)
    }

    fun ready(): Boolean = connect().use {
//        SECURE_LOGGER.info("calling PING")
        val maybePong = it.call("PING")?.encodeBase64()
        SECURE_LOGGER.warn("PING = $maybePong")
        it.call("PING")?.encodeBase64() == "PONG"
    }

    interface Managed : AutoCloseable {
        fun call(vararg args: Any): ByteArray?
    }

    class ManagedImpl(config: RedisConfig) : Managed, AutoCloseable {
        private val socket = Socket(config.uri.host, config.uri.port).also {
            SECURE_LOGGER.info("creating socket with host: ${config.uri.host} and port: ${config.uri.port}")
        }
        private val writer = Encoder(BufferedOutputStream(socket.getOutputStream()))
        private val reader = Parser(BufferedInputStream(socket.getInputStream()))

        // See [docs](https://redis.io/commands) for all commands
        override fun call(vararg args: Any): ByteArray? {
            writer.write(args.toList())
            writer.flush()

            return when(val parsed = reader.parse()) {
                is ByteArray -> {
                    SECURE_LOGGER.info("ByteArray: ${parsed.encodeBase64()}")
                    parsed
                }
                is Long -> {
                    SECURE_LOGGER.info("Long: $parsed")
                    error("long not supported")
                }
                is List<*> -> {
                    parsed.forEach {
                        when (it) {
                            is ByteArray -> SECURE_LOGGER.info("List.ByteArray: ${it.encodeBase64()}")
                            is Long -> SECURE_LOGGER.info("Long: $it")
                            else -> SECURE_LOGGER.info("List.* $${it?.javaClass?.canonicalName}")
                        }
                    }
                    error("list not supported")
                }
                null -> null
                else -> {
                    SECURE_LOGGER.info("Any: ${parsed.javaClass.canonicalName}")
                    error("unknown not supported")
                }
            }

        }

//        private inline fun <reified T : Any> read(): T? = reader.parse() as T?

        override fun close() = socket.close()
    }
}
