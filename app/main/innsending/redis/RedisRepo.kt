package innsending.redis

import innsending.RedisConfig
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

const val EnDag: Long = 60 * 60 * 24

private fun jedisPool(config:RedisConfig)= JedisPool(
    JedisPoolConfig(),
    HostAndPort.from(config.uri),
    DefaultJedisClientConfig.builder()
        .ssl(true)
        .user(config.username)
        .password(config.password)
        .build()
    )

class RedisRepo(private val config: RedisConfig, private val jedisPool: JedisPool = jedisPool(config)) {

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

    fun isReady() = jedisPool.resource.use {
            it.ping()=="PONG"
        }

}
