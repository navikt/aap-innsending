package innsending.redis

import innsending.RedisConfig
import io.ktor.util.*
import redis.clients.jedis.Jedis

class RedisJedis(private val config: RedisConfig) {
    private val jedis = Jedis(config.uri.host, config.uri.port)

    operator fun set(key: String, value: ByteArray) {
        jedis.connect()
        jedis.auth(config.username, config.password)
        jedis.set(key, value.encodeBase64())
        jedis.disconnect()
    }

    operator fun  get(key: String): ByteArray? {
        jedis.connect()
        jedis.auth(config.username, config.password)
        val res = jedis.get(key)?.toByteArray()
        jedis.disconnect()
        return res
    }

    fun expire(key: String, seconds: Long) {
        jedis.connect()
        jedis.auth(config.username, config.password)
        jedis.expire(key, seconds)
        jedis.disconnect()
    }

    fun del(key: String) {
        jedis.connect()
        jedis.auth(config.username, config.password)
        jedis.del(key)
        jedis.disconnect()
    }

    fun ready(): Boolean {
        jedis.connect()
        val res = jedis.ping() == "PONG"
        jedis.disconnect()
        return res
    }
}