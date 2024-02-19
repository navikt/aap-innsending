package innsending.http

import io.ktor.client.call.*
import io.ktor.client.statement.*

sealed class HttpResult<T>(config: HttpConfig) {
    val log by lazy { config.log }

    class Ok<T>(
        config: HttpConfig,
        val response: HttpResponse,
    ) : HttpResult<T>(config) {
        suspend inline fun <reified T : Any> getOrNull(): T? {
            return runCatching {
                response.body<T>()
            }.onFailure {
                log.error("Failed to deserialize response '{}'", response.call.request.url, it)
            }.getOrNull()
        }
    }

    class ClientError<T>(
        val config: HttpConfig,
        private val response: HttpResponse,
    ) : HttpResult<T>(config) {
        suspend fun traceError(): Nothing? {
            // todo: micrometer
            log.error("HTTP ${response.status} for ${response.call.request.url} responded with ${response.bodyAsText()}")
            return null
        }
    }

    class ServerError<T>(
        val config: HttpConfig,
        private val response: HttpResponse,
    ) : HttpResult<T>(config) {
        suspend fun traceError(): Nothing? {
            // todo: micrometer
            log.error("HTTP ${response.status} for ${response.call.request.url} responded with ${response.bodyAsText()}")
            return null
        }
    }
}
