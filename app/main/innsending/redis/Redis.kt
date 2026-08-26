package innsending.redis

import innsending.RedisConfig
import innsending.logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.RedisClient
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
    private val client: RedisClient
) : AutoCloseable {
    constructor(config: RedisConfig) : this(
        RedisClient.builder()
            .hostAndPort(HostAndPort(config.uri.host, config.uri.port))
            .clientConfig(
                DefaultJedisClientConfig.builder().ssl(true).user(config.username).password(config.password).build()
            )
            .build()
    )

    constructor(uri: URI) : this(RedisClient.create(uri))

    @Deprecated("Keys traverserer alle keys i redis og skal dermed ikke brukes.")
    fun getKeysByPrefix(prefix: String): List<Key> {
        val keys = client.keys("$prefix:*")
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

    private fun splitKey(splittedKey: String): Key {
        val split = splittedKey.split(":")
        return Key(split[1], split[0])
    }

    fun set(key: Key, value: ByteArray, expireSec: Long) {
        client.set(key.get(), value, SetParams().ex(expireSec))
    }

    fun setExpire(key: Key, expireSec: Long) {
        val updatedRows = client.expire(key.get(), expireSec)
        if (updatedRows == 0L) {
            logger.warn("Forventet å oppdatere TTL, men nøkkelen ble ikke oppdatert")
            logger.warn("Forventet å oppdatere TTL, men nøkkelen[{}] ble ikke oppdatert", key)
        }
    }

    operator fun get(key: Key): ByteArray? = client.get(key.get())

    fun del(key: Key) {
        client.del(key.get())
    }

    fun ready(): Boolean = client.ping() == "PONG"

    fun lastUpdated(key: Key): LocalDateTime =
        LocalDateTime.now().minusSeconds(EnDagSekunder - client.ttl(key.get()))

    fun expiresIn(key: Key): Long = client.ttl(key.get())

    fun exists(key: Key): Boolean = client.exists(key.get())

    override fun close() {
        client.close()
    }
}
