package no.nav.aap.redis

import java.net.URI

data class RedisConfig(
    val uri: URI,
    val username: String,
    val password: String,
)
