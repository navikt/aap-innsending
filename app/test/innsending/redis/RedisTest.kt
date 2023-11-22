package innsending.redis

import innsending.RedisConfig
import innsending.SECURE_LOGGER
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

//    @Test
//    fun test() {
//        RedisMock().use {
//            val config = RedisConfig("http://host.docker.internal:6379", "", "")
//            SECURE_LOGGER.warn("uri: ${config.uri}")
//            val redis = Redis(config)
//            redis["a"] = "b".toByteArray()
//
//            assertEquals("b", String(redis["a"]!!))
//        }
//    }
}