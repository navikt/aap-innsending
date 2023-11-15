package innsending.redis

import innsending.RedisConfig
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

const val EnDag: Long = 60 * 60 * 24

class RedisRepo(config: RedisConfig) {
    private val jedisPool = JedisPool(
        JedisPoolConfig(),
        HostAndPort(config.host, config.port),
        DefaultJedisClientConfig.builder()
            .ssl(true)
            .user("<redis_username>") // TODO: hva er denne
            .password(config.pwd)
            .build()
    )

    /**
     * Mellomlagrer key-value parr. Kan f.eks være en søknad eller et vedlegg
     * @param value kan ikke overstige 1GB
     */
    fun mellomlagre(key: String, value: ByteArray) {
        jedisPool.resource.use {
            it.set(key.toByteArray(), value)
            it.expire(key.toByteArray(), 3 * EnDag)
        }
    }

    fun hentMellomlagring(key: String): ByteArray? =
        try {
            jedisPool.resource.use {
                it.get(key.toByteArray())
            }
        } catch (e: Exception) {
            null
        }

    fun slettMellomlagring(key: String): Long =
        jedisPool.resource.use {
            it.del(key.toByteArray())
        }
}
