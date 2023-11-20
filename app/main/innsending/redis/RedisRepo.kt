package innsending.redis

import innsending.RedisConfig
import innsending.SECURE_LOGGER
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Jedis

const val EnDag: Long = 60 * 60 * 24

private fun jedis(config: RedisConfig): Jedis =
    Jedis(
        HostAndPort.from(config.uri),
        DefaultJedisClientConfig.builder()
            .user(config.username)
            .password(config.password)
            .ssl(true)
            .build()
    )

class RedisRepo(private val config: RedisConfig, private val jedis: Jedis = jedis(config)) {

    /**
     * Mellomlagrer key-value parr. Kan f.eks være en søknad eller et vedlegg
     * @param value kan ikke overstige 1GB
     */
    fun mellomlagre(key: String, value: ByteArray) {
        jedis.use {
            it.set(key.toByteArray(), value)
            it.expire(key.toByteArray(), 3 * EnDag)
        }
    }

    fun hentMellomlagring(key: String): ByteArray? =
        try {
            jedis.use {
                it.get(key.toByteArray())
            }
        } catch (e: Exception) {
            null
        }

    fun slettMellomlagring(key: String): Long =
        jedis.use {
            it.del(key.toByteArray())
        }

    fun isReady() = jedis.use {
        runCatching {
            it.ping() == "PONG"
        }.getOrElse {
            SECURE_LOGGER.warn("Failed to ping Redis, $it")
            false
        }
    }
}
