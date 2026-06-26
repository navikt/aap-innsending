package innsending.http

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import innsending.logger
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.jackson.*
import org.slf4j.LoggerFactory

private val APP_LOGGER: org.slf4j.Logger = LoggerFactory.getLogger(HttpClientFactory::class.java)


internal object HttpClientFactory {
    fun create(logLevel: LogLevel = LogLevel.INFO): HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 5_000
        }

        install(Logging) {
            logger = ClientLogger(logLevel)
            level = logLevel
        }

        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(JavaTimeModule())
            }
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }
    }
}

internal class ClientLogger(level: LogLevel) : Logger {
    override fun log(message: String) {
        log.info(message)
    }

    private val log = when (level) {
        /**
         * HTTP code, method and url is logged
         */
        LogLevel.INFO, LogLevel.NONE -> APP_LOGGER

        /**
         *  HTTP code, method, url, headers request body and response body is logged
         */
        else -> logger
    }
}
