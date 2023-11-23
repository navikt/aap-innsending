package no.nav.aap.redis

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Socket

open class Redis(private val config: RedisConfig) {
    internal open fun connect(): Managed = ManagedImpl().also {
        it.call("AUTH", config.username, config.password)
    }

    operator fun set(key: String, value: ByteArray): Unit = connect().use {
        it.call("SET", key, value)
    }

    operator fun get(key: String): ByteArray? = connect().use {
        it.call("GET", key) as ByteArray?
    }

    fun expire(key: String, seconds: Long): Unit = connect().use {
        it.call("EXPIRE", key, seconds)
    }

    fun del(key: String): Unit = connect().use {
        it.call("DEL", key)
    }

    fun ready(): Boolean = connect().use {
        it.call("PING")?.let { response -> String(response as ByteArray) } == "PONG"
    }

    interface Managed : AutoCloseable {
        fun call(vararg args: Any): Any?
    }

    inner class ManagedImpl : Managed, AutoCloseable {
        private val socket = Socket(config.uri.host, config.uri.port)
        private val writer = Encoder(BufferedOutputStream(socket.getOutputStream()))
        private val reader = Parser(BufferedInputStream(socket.getInputStream()))

        // See [docs](https://redis.io/commands)
        override fun call(vararg args: Any): Any? {
            writer.write(args.toList())
            writer.flush()
            return reader.parse()
        }

        override fun close() {
            call("QUIT")
            socket.close()
        }
    }
}
