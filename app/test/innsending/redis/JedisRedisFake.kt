package innsending.redis

class JedisRedisFake : Redis {
    private val cache = mutableMapOf<String, ByteArray>()

    override fun set(key: String, value: ByteArray) {
        cache[key] = value
    }

    override fun get(key: String): ByteArray? = cache[key]

    override fun expire(key: String, seconds: Long) {}

    override fun del(key: String) {
        cache.remove(key)
    }

    override fun ready(): Boolean = true
}