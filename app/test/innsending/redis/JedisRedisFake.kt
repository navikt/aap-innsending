package innsending.redis

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class JedisRedisFake : Redis {
    private val cache = mutableMapOf<String, ByteArray>()
    private val expires = mutableMapOf<String,  LocalDateTime>()

    private fun str(key: Key): String = "${key.prefix}:${key.value}"

    override fun set(key: Key, value: ByteArray, expireSec: Long) {
        cache[str(key)] = value
        expires[str(key)] = LocalDateTime.now().plusSeconds(expireSec)
    }


    override fun getKeysByPrefix(prefix: String): List<Key> {
        return cache.keys.filter { it.startsWith(prefix) }.map {
            Key(it.split(":")[1], prefix)
        }
    }

    override fun setExpire(key: Key, expireSec: Long) {
        expires[str(key)] = LocalDateTime.now().plusSeconds(expireSec)
    }

    override fun get(key: Key): ByteArray? {
        return cache[str(key)]
    }

    override fun del(key: Key) {
        cache.remove(str(key))
    }

    override fun ready(): Boolean = true

    override fun lastUpdated(key: Key): Long = 0
    override fun expiresIn(key: Key): Long {
        val expire = expires[str(key)]
        if (expire != null) {
            return ChronoUnit.SECONDS.between(LocalDateTime.now(),expire)
        }
        else {
            return 0L
        }
    }

    override fun exists(key: Key): Boolean = cache.contains(str(key))

    override fun close() {}
}