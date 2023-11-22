package innsending.redis

import innsending.RedisConfig
import io.ktor.util.*
import redis.clients.jedis.Jedis

class RedisJedis(private val config: RedisConfig) {
    private fun connection(): Jedis {
        return Jedis(config.uri.host, config.uri.port)
    }

    operator fun set(key: String, value: ByteArray) {
        connection().use {
            it.connect()
            it.auth(config.username, config.password)
            it.set(key, value.encodeBase64())
            it.disconnect()
        }
    }

    operator fun get(key: String): ByteArray? {
        connection().use {
            it.connect()
            it.auth(config.username, config.password)
            val res = it.get(key)?.toByteArray()
            it.disconnect()
            return res
        }
    }

    fun expire(key: String, seconds: Long) {
        connection().use {
            it.connect()
            it.auth(config.username, config.password)
            it.expire(key, seconds)
            it.disconnect()
        }
    }

    fun del(key: String) {
        connection().use {
            it.connect()
            it.auth(config.username, config.password)
            it.del(key)
            it.disconnect()
        }
    }

    fun ready(): Boolean {
        connection().use {
            it.connect()
            val res = it.ping() == "PONG"
            it.disconnect()
            return res
        }
    }
}