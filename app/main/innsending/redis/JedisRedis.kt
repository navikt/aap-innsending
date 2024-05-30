package innsending.redis

import innsending.RedisConfig
import org.slf4j.LoggerFactory
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.time.LocalDateTime

const val EnDagSekunder: Long = 60 * 60 * 24

data class Key(
    val value: String,
    val prefix: String = "",
) {
    fun get(): ByteArray = "$prefix:$value".toByteArray()
}

private val logger = LoggerFactory.getLogger("Redis")

interface Redis : AutoCloseable {
    fun set(key: Key, value: ByteArray, expireSec: Long)
    fun setExpire(key: Key, expireSec: Long)
    operator fun get(key: Key): ByteArray?
    fun del(key: Key)
    fun ready(): Boolean
    fun getKeysByPrefix(prefix: String): List<Key>
    fun lastUpdated(key: Key): LocalDateTime
    fun expiresIn(key: Key): Long
    fun exists(key: Key): Boolean
}

class JedisRedis(config: RedisConfig) : Redis {
    private val pool = JedisPool(
        JedisPoolConfig(),
        HostAndPort(config.uri.host, config.uri.port),
        DefaultJedisClientConfig.builder().ssl(true).user(config.username).password(config.password).build()
    )


    override fun getKeysByPrefix(prefix: String): List<Key> {
        pool.resource.use {
            val keys = it.keys("$prefix:*")
            val size = keys.size
            if (size > 0) {
                logger.info("Fant {} nøkler", size)
            }

            return keys.flatMap { keyString ->
                val split = keyString.split(" ").map { splittedKey ->
                    splitKey(splittedKey)
                }
                split
            }
        }
    }

    private fun splitKey(splittedKey: String): Key {
        val split = splittedKey.split(":")
        return Key(split[1], split[0])
    }

    override fun set(key: Key, value: ByteArray, expireSec: Long) {
        pool.resource.use {
            it.set(key.get(), value)
            it.expire(key.get(), expireSec)
        }
    }

    override fun setExpire(key: Key, expireSec: Long) {
        pool.resource.use {
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

    override fun lastUpdated(key: Key): LocalDateTime {
        val oneDayInSeconds = 60 * 60 * 24L
        pool.resource.use {
            return LocalDateTime.now().minusSeconds(oneDayInSeconds - it.ttl(key.get()))
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

    override fun close() {
        pool.close()
    }
}
