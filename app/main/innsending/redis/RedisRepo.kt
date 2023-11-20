package innsending.redis

import innsending.RedisConfig
import innsending.SECURE_LOGGER
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

const val EnDag: Long = 60 * 60 * 24

private fun jedisPool(config:RedisConfig): JedisPool{
    val port = config.uri.substringAfter("rediss://").split(":")[1].toInt()
    val host = config.uri.removeSuffix(":$port")
    return JedisPool(JedisPoolConfig(),host, port, 2000, config.username, config.password, true)
}

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
            try {
                it.ping()=="PONG"
            }catch (e:Exception){
                SECURE_LOGGER.warn("Klarte ikke å pinge redis",e)
                SECURE_LOGGER.warn("Redis uri: ${config.uri}")
                false
            }
    }

}
