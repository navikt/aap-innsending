package innsending.redis

class JedisRedisFake : Redis {
    private val cache = mutableMapOf<String, ByteArray>()

    private fun str(key: Key): String = "${key.prefix}:${key.value}"

    override fun set(key: Key, value: ByteArray, expireSec: Long) {
        cache[str(key)] = value
    }

    override fun get(key: Key): ByteArray? {
        return cache[str(key)]
    }

    override fun del(key: Key) {
        cache.remove(str(key))
    }

    override fun ready(): Boolean = true

    override fun getAllKeys(): List<String> = cache.keys.toList()
    override fun createdAt(key: Key): Long = 0
    override fun expiresIn(key: Key): Long = 0

    override fun exists(key: Key): Boolean = cache.contains(str(key))
}