package innsending.redis

import innsending.RedisConfig
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals

class RedisFake(config: RedisConfig) : Redis(config) {
    private val cache = mutableMapOf<String, ByteArray>()

    override fun connect(): Managed = object : Managed, AutoCloseable {
        override fun close() {}

        override fun call(vararg args: Any): ByteArray? {
            return when (args[0].toString()) {
                "GET" -> cache[args[1] as String]
                "DEL" -> cache.remove(args[1] as String)
                "PING" -> "PONG".toByteArray()
                "SET" -> {
                    cache[args[1] as String] = args[2] as ByteArray
                    null
                }

                else -> null
            }
        }
    }
}

class RedisFakeTest {
    @Test
    fun `fakes set and get`() {
        val redis: Redis = RedisFake(RedisConfig("mock:123", "", ""))
        redis["key"] = "value".toByteArray()
        assertEquals("value", String(requireNotNull(redis["key"])))
    }

    @Test
    fun `fakes del`() {
        val redis: Redis = RedisFake(RedisConfig("mock:123", "", ""))
        redis["key"] = "value".toByteArray()
        assertEquals("value", String(requireNotNull(redis["key"])))
        redis.del("key")
        assertEquals(null, redis.get("key"))
    }
}
