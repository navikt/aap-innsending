package innsending.redis

import innsending.RedisConfig
import innsending.SECURE_LOGGER
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

const val EnDagSekunder: Long = 60 * 60 * 24

data class Key(
    val value: String,
    val prefix: String = "",
) {
    fun get(): ByteArray = "$prefix:$value".toByteArray()
}

interface Redis {
    fun set(key: Key, value: ByteArray, expireSec: Long)
    operator fun get(key: Key): ByteArray?
    fun del(key: Key)
    fun ready(): Boolean
    fun createdAt(key: Key): Long
    fun expiresIn(key: Key): Long
    fun exists(key: Key): Boolean
}

class JedisRedis(config: RedisConfig) : Redis {
    private val pool = JedisPool(
        JedisPoolConfig(),
        HostAndPort(config.uri.host, config.uri.port),
        DefaultJedisClientConfig.builder().ssl(true).user(config.username).password(config.password).build()
    )


    override fun set(key: Key, value: ByteArray, expireSec: Long) {
        pool.resource.use {
            it.set(key.get(), value)
            it.expire(key.get(), expireSec)
        }
    }

    override operator fun get(key: Key): ByteArray? {
        pool.resource.use {
            return it.get(key.get())
        }
    }

    override fun del(key: Key) {
        pool.resource.use {
            it.del(key.get())
        }
    }

    override fun ready(): Boolean {
        pool.resource.use {
            return it.ping() == "PONG"
        }
    }

    override fun createdAt(key: Key): Long {
        pool.resource.use {
            return it.objectIdletime(key.get())
        }
    }

    override fun expiresIn(key: Key): Long {
        pool.resource.use {
            return it.ttl(key.get())
        }
    }

    override fun exists(key: Key): Boolean {
        pool.resource.use {
            return it.exists(key.get())
        }
    }
}
