package innsending.redis

import innsending.RedisConfig
import innsending.SECURE_LOGGER
import org.junit.jupiter.api.Test
import redis.clients.jedis.Jedis
import java.net.Socket
import java.net.URI
import kotlin.test.assertEquals

//class RedisMock : AutoCloseable {
//    private val server = RedisContainer(DockerImageName.parse("redis:7.0")).apply {
//        start()
//    }
//
//    fun getURI(): String = "${server.host}:${server.exposedPorts.first()}"
//    override fun close() = server.close()
//}

class RedisTest {
// sudo ln -nfs ~/.colima/docker.sock /var/run/docker.sock

//    @Test
//    fun test() {
//        val config = RedisConfig(URI.create("http://localhost:6379"), "", "")
//        println(config.uri.host)
//        println(config.uri.port)
//
//        SECURE_LOGGER.warn("uri: ${config.uri}")
//        val redis = Redis(config)
//        redis.set("a", "b".toByteArray())
//
//        assertEquals("b", String(redis.get("a")!!))
//    }
//
//    @Test
//    fun  jedisTest() {
//        val config = RedisConfig(URI.create("http://localhost:6379"), "", "")
//
//        val jedis = Jedis(config.uri.host, config.uri.port)
//        jedis.connect()
//        jedis.set("c", "b")
//        jedis.disconnect()
//        assertEquals("b", jedis.get("c"))
//    }
}