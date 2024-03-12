package innsending.http

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import innsending.SECURE_LOGGER
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.jackson.*

internal object HttpClientFactory {
    fun create(logLevel: LogLevel = LogLevel.INFO): HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 5_000
        }

        install(HttpRequestRetry)

        install(Logging) {
            logger = ClientLogger
            level = logLevel
        }

        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(JavaTimeModule())
            }
        }
    }
}

internal object ClientLogger : Logger {
    override fun log(message: String) {
        SECURE_LOGGER.info(message)
    }
}
