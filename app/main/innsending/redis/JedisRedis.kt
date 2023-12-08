package innsending.redis

import innsending.RedisConfig
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

const val EnDagSekunder: Long = 60 * 60 * 24

interface Redis {
    fun set(key: String, value: ByteArray, expireSec: Long)
    operator fun get(key: String): ByteArray?
    fun del(key: String)
    fun ready(): Boolean
    fun exists(key: String): Boolean
}

class JedisRedis(config: RedisConfig) : Redis {
    private val pool = JedisPool(
        JedisPoolConfig(),
        HostAndPort(config.uri.host, config.uri.port),
        DefaultJedisClientConfig.builder().ssl(true).user(config.username).password(config.password).build()
    )

    override fun set(key: String, value: ByteArray, expireSec: Long) {
        pool.resource.use {
            it.set(key.toByteArray(), value)
            it.expire(key.toByteArray(), expireSec)
        }
    }

    override operator fun get(key: String): ByteArray? {
        pool.resource.use {
            return it.get(key.toByteArray())
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

    override fun exists(key: String): Boolean {
        pool.resource.use {
            return it.exists(key)
        }
    }
}
