package innsending.redis

import innsending.RedisConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RedisRepoTest {

    @Test
    fun `mellomlagring kan hentes igjen`() {
        val redisRepo = RedisRepo(RedisConfig("", "", ""), RedisMock)
        redisRepo.mellomlagre("key", "value".toByteArray())

        assertEquals("value", String(redisRepo.hentMellomlagring("key")!!))
    }

    @Test
    fun `mellomlagring kan slettes`() {
        val redisRepo = RedisRepo(RedisConfig("", "", ""), RedisMock)
        redisRepo.mellomlagre("key", "value".toByteArray())

        assertEquals("value", String(redisRepo.hentMellomlagring("key")!!))

        redisRepo.slettMellomlagring("key")

        assertEquals(null, redisRepo.hentMellomlagring("key"))
    }

}