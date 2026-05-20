package innsending.redis

import java.util.UUID
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HashedKeyTest {

    @Test
    fun `test at hashing fungerer uten prefix`() {
        val fnrKey = Key.of(Random.nextLong(10000000000, 99999999999).toString())

        val encodedKey = HashedKey.of(fnrKey)
        assertNotEquals(fnrKey.get(), encodedKey.encoded.toByteArray())

        val decodedKey = HashedKey(encodedKey.encoded).toKey()
        assertEquals(fnrKey, decodedKey)
    }

    @Test
    fun `test at hashing fungerer med prefix`() {
        val fnr = Random.nextLong(10000000000, 99999999999).toString()
        val key = Key(value = UUID.randomUUID().toString(), prefix = fnr)

        val encodedKey = HashedKey.of(key)
        assertNotEquals(key.get(), encodedKey.encoded.toByteArray())

        val decodedKey = HashedKey(encodedKey.encoded).toKey()
        assertEquals(key, decodedKey)
    }

}