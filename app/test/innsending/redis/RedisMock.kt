package innsending.redis

import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

class RedisPoolMock:JedisPool() {
    override fun getResource(): Jedis = RedisMock

}

object RedisMock:Jedis(){
    private val dataStore = mutableMapOf<String,ByteArray>()
    override fun get(key: ByteArray): ByteArray? {
        return dataStore[String(key)]
    }
    override fun expire(key: ByteArray, seconds: Long): Long = -1

    override fun set(key: ByteArray, value: ByteArray): String {
        dataStore[String(key)]=value
        return "OK"
    }

    override fun del(key: ByteArray): Long {
        dataStore.remove(String(key))
        return -1
    }
}