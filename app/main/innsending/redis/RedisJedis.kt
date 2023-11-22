package innsending.redis

import innsending.RedisConfig
import io.ktor.util.*
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

class RedisJedis(private val config: RedisConfig) {
    private val pool = JedisPool(
        JedisPoolConfig(),
        HostAndPort(config.uri.host, config.uri.port),
        DefaultJedisClientConfig.builder()
            .ssl(true)
            .user(config.username)
            .password(config.password)
            .build()
    )

    operator fun set(key: String, value: ByteArray) {
        pool.resource.use {
            it.set(key, value.encodeBase64())
        }
    }

    operator fun get(key: String): ByteArray? {
        pool.resource.use {
            return it.get(key)?.toByteArray()
        }
    }

    fun expire(key: String, seconds: Long) {
        pool.resource.use {
            it.expire(key, seconds)
        }
    }

    fun del(key: String) {
        pool.resource.use {
            it.del(key)
        }
    }

    fun ready(): Boolean {
        pool.resource.use {
            return it.ping() == "PONG"
        }
    }
}