package innsending.redis

import innsending.RedisConfig
import io.ktor.util.*
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import redis.clients.jedis.JedisPool

class RedisJedis(private val config: RedisConfig) {
    private val pool = JedisPool(
        GenericObjectPoolConfig(),
        config.uri.substringBeforeLast(":"),
        config.uri.substringAfterLast(":").toInt()
    )

    operator fun set(key: String, value: ByteArray) {
        pool.resource.use {
            it.connect()
            it.auth(config.username, config.password)
            it.set(key, value.encodeBase64())
            it.disconnect()
        }
    }

    operator fun get(key: String): ByteArray? {
        pool.resource.use {
            it.auth(config.username, config.password)
            return it.get(key)?.toByteArray()
        }
    }

    fun expire(key: String, seconds: Long) {
        pool.resource.use {
            it.auth(config.username, config.password)
            it.expire(key, seconds)
        }
    }

    fun del(key: String) {
        pool.resource.use {
            it.auth(config.username, config.password)
            it.del(key)
        }
    }

    fun ready(): Boolean {
        pool.resource.use {
            return it.ping() == "PONG"
        }
    }
}