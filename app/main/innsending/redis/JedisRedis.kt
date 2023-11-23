package innsending.redis

import innsending.RedisConfig
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

interface Redis {
    operator fun set(key: String, value: ByteArray)
    operator fun get(key: String): ByteArray?
    fun expire(key: String, seconds: Long)
    fun del(key: String)
    fun ready(): Boolean
}

class JedisRedis(config: RedisConfig): Redis {
    private val pool = JedisPool(
        JedisPoolConfig(),
        HostAndPort(config.uri.host, config.uri.port),
        DefaultJedisClientConfig.builder()
            .ssl(true)
            .user(config.username)
            .password(config.password)
            .build()
    )

    override operator fun set(key: String, value: ByteArray) {
        pool.resource.use {
            it.set(key.toByteArray(), value)
        }
    }

    override operator fun get(key: String): ByteArray? {
        pool.resource.use {
            return it.get(key.toByteArray())
        }
    }

    override fun expire(key: String, seconds: Long) {
        pool.resource.use {
            it.expire(key, seconds)
        }
    }

    override fun del(key: String) {
        pool.resource.use {
            it.del(key)
        }
    }

    override fun ready(): Boolean {
        pool.resource.use {
            return it.ping() == "PONG"
        }
    }
}