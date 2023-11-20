package innsending.redis

import innsending.RedisConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RedisRepoTest {
    private val redisMock = RedisMock
    private val redisConfig = RedisConfig("","","")


    @Test
    fun `mellomlagring kan hentes igjen`() {
        val redisPoolMock = RedisPoolMock()
        val redisRepo = RedisRepo(redisConfig, redisPoolMock)
        redisRepo.mellomlagre("key", "value".toByteArray())

        assertEquals("value",String(redisRepo.hentMellomlagring("key")!!))
    }

    @Test
    fun `mellomlagring kan slettes`(){
        val redisPoolMock = RedisPoolMock()
        val redisRepo = RedisRepo(redisConfig, redisPoolMock)
        redisRepo.mellomlagre("key", "value".toByteArray())

        assertEquals("value",String(redisRepo.hentMellomlagring("key")!!))

        redisRepo.slettMellomlagring("key")

        assertEquals(null,redisRepo.hentMellomlagring("key"))

    }

}