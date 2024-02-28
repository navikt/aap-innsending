package innsending.http

import io.ktor.client.call.*
import io.ktor.client.statement.*
import org.slf4j.Logger

interface ApiErrorResult {
    suspend fun getNullAndTrace(): Nothing?
    suspend fun getMessage(): String
}

sealed interface ApiResult {

    class Ok(
        config: HttpConfig,
        val response: HttpResponse,
    ) : ApiResult {
        val log: Logger = config.log

        suspend inline fun <reified T : Any> getOrNull(): T? {
            // todo: micrometer
            return runCatching {
                response.body<T>()
            }.onFailure {
                log.error("Failed to deserialize response '{}'", response.call.request.url, it)
            }.getOrNull()
        }
    }

    class ClientError(
        config: HttpConfig,
        private val response: HttpResponse,
    ) : ApiResult, ApiErrorResult {
        private val log: Logger = config.log

        override suspend fun getNullAndTrace(): Nothing? {
            // todo: micrometer
            log.error(getMessage())
            return null
        }

        override suspend fun getMessage(): String {
            return "HTTP ${response.status} for ${response.call.request.url} responded with ${response.bodyAsText()}"
        }
    }

    class ServerError(
        config: HttpConfig,
        private val response: HttpResponse,
    ) : ApiResult, ApiErrorResult {
        private val log: Logger = config.log

        override  suspend fun getNullAndTrace(): Nothing? {
            // todo: micrometer
            log.error(getMessage())
            return null
        }

        override suspend fun getMessage(): String {
            return "HTTP ${response.status} for ${response.call.request.url} responded with ${response.bodyAsText()}"
        }
    }

    class UnknownError(
        private val config: HttpConfig,
        val err: Throwable,
    ) : ApiResult, ApiErrorResult {
        private val log: Logger = config.log

        override  suspend fun getNullAndTrace(): Nothing? {
            // todo: micrometer
            log.error(getMessage(), err)
            return null
        }

        override suspend fun getMessage(): String {
            return "Failed unexpectedly when calling ${config.host}. Check Innsending's logs."
        }
    }

    companion object {
        fun from(config: HttpConfig, response: HttpResponse): ApiResult {
            return when (response.status.value) {
                in 100..199 -> ClientError(config, response)
                in 200..299 -> Ok(config, response)
                in 300..399 -> ClientError(config, response)
                in 400..499 -> ClientError(config, response)
                else -> ServerError(config, response)
            }
        }

        fun from(config: HttpConfig, err: Throwable): ApiResult {
            return UnknownError(config, err)
        }
    }
}
