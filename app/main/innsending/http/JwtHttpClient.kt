package innsending.http

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.Logger

abstract class JwtHttpClient(val config: HttpConfig) {
    abstract suspend fun getToken(): String

    val http by lazy { Client() }

    inner class Client {
        @PublishedApi
        internal val client = HttpClientFactory.create()

        suspend inline fun <reified REQ : Any, RES> get(
            path: Path,
            body: REQ,
            crossinline request: HttpRequestBuilder.() -> Unit = {},
        ): HttpResult<RES>? {
            val catchedResponse: Result<HttpResponse> =
                config.latencyMeter.timed {
                    runCatching {
                        client.get(config.host + path) {
                            bearerAuth(getToken())
                            accept(ContentType.Application.Json)
                            setBody(body)
                            apply(request)
                        }
                    }
                }

            return catchedResponse.fold(::onSuccess, ::onFailure)
        }

        suspend inline fun <reified REQ : Any, RES> post(
            path: Path,
            body: REQ,
            crossinline request: HttpRequestBuilder.() -> Unit = {},
        ): HttpResult<RES>? {
            val catchedResponse: Result<HttpResponse> =
                config.latencyMeter.timed {
                    runCatching {
                        client.post(config.host + path) {
                            contentType(ContentType.Application.Json)
                            bearerAuth(getToken())
                            accept(ContentType.Application.Json)
                            setBody(body)
                            apply(request)
                        }
                    }
                }

            return catchedResponse.fold(::onSuccess, ::onFailure)
        }
    }


    @PublishedApi
    internal fun <RES> onSuccess(response: HttpResponse): HttpResult<RES> {
        return when (response.status.value) {
            in 200..299 -> HttpResult.Ok(config, response)
            in 400..499 -> HttpResult.ClientError(config, response)
            in 500..599 -> HttpResult.ServerError(config, response)
            else -> HttpResult.ServerError(config, response)
        }
    }

    @PublishedApi
    internal fun <RES> onFailure(
        e: Throwable,
    ): HttpResult<RES>? {
        config.log.error("Failed to execute POST operation", e)
        return null
    }

    @PublishedApi
    internal suspend inline fun <reified RES : Any> HttpResponse.tryToBody(): RES? {
        if (status.isSuccess()) {
            return runCatching {
                body<RES>()
            }.getOrNull()
        }
        return null
    }
}

class Path private constructor(private val path: String) {
    init {
        require(path.startsWith("/")) { "Path must start with /" }
    }

    companion object {
        fun from(path: String) = Path(path)
    }

    override fun toString(): String = path
}

open class HttpConfig(
    val host: String,
    val log: Logger,
    val latencyMeter: Meter.LATENCY,
)

sealed class HttpResult<T>(config: HttpConfig) {
    val log by lazy { config.log }

    class Ok<T>(config: HttpConfig, val response: HttpResponse) : HttpResult<T>(config) {

        suspend inline fun <reified T : Any> getOrNull(): T? {
            return runCatching {
                response.body<T>()
            }.onFailure {
                log.error("Failed to deserialize response '{}'", response.call.request.url, it)
            }.getOrNull()
        }
    }

    class ClientError<T>(val config: HttpConfig, private val response: HttpResponse) : HttpResult<T>(config) {
        suspend fun traceError(): Nothing? {
            // todo: micrometer
            log.error("HTTP ${response.status} for ${response.call.request.url} responded with ${response.bodyAsText()}")
            return null
        }
    }

    class ServerError<T>(val config: HttpConfig, private val response: HttpResponse) : HttpResult<T>(config) {
        suspend fun traceError(): Nothing? {
            // todo: micrometer
            log.error("HTTP ${response.status} for ${response.call.request.url} responded with ${response.bodyAsText()}")
            return null
        }
    }
}
