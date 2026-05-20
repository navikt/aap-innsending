package innsending.redis

import innsending.RedisConfig
import innsending.logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.params.SetParams
import java.net.URI
import java.time.LocalDateTime

const val EnDagSekunder: Long = 60 * 60 * 24

data class Key(
    val value: String,
    val prefix: String = "",
) {
    fun get(): ByteArray = "$prefix:$value".toByteArray()
}

private val logger = LoggerFactory.getLogger("Redis")

class Redis private constructor(
    private val pool: JedisPool
) : AutoCloseable {
    constructor(config: RedisConfig) : this(
        JedisPool(
            JedisPoolConfig(),
            HostAndPort(config.uri.host, config.uri.port),
            DefaultJedisClientConfig.builder().ssl(true).user(config.username).password(config.password).build()
        )
    )

    constructor(uri: URI) : this(JedisPool(uri))

    @Deprecated("Keys traverserer alle keys i redis og skal dermed ikke brukes.")
    fun getKeysByPrefix(prefix: String): List<Key> {
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

    fun set(key: Key, value: ByteArray, expireSec: Long) {
        pool.resource.use {
            it.set(key.get(), value, SetParams().ex(expireSec))
        }
    }

    fun setExpire(key: Key, expireSec: Long) {
        pool.resource.use {
            val updatedRows = it.expire(key.get(), expireSec)
            if (updatedRows == 0L) {
                logger.warn("Forventet å oppdatere TTL, men nøkkelen ble ikke oppdatert")
                logger.warn("Forventet å oppdatere TTL, men nøkkelen[{}] ble ikke oppdatert", key)
            }
        }
    }

    operator fun get(key: Key): ByteArray? {
        pool.resource.use {
            return it.get(key.get())
        }
    }

    fun del(key: Key) {
        pool.resource.use {
            it.del(key.get())
        }
    }

    fun ready(): Boolean {
        pool.resource.use {
            return it.ping() == "PONG"
        }
    }

    fun lastUpdated(key: Key): LocalDateTime {
        pool.resource.use {
            return LocalDateTime.now().minusSeconds(EnDagSekunder - it.ttl(key.get()))
        }
    }

    fun expiresIn(key: Key): Long {
        pool.resource.use {
            return it.ttl(key.get())
        }
    }

    fun exists(key: Key): Boolean {
        pool.resource.use {
            return it.exists(key.get())
        }
    }

    override fun close() {
        pool.close()
    }
}
