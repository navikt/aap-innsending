package innsending.http

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.core.instrument.MeterRegistry

abstract class HttpClientWrapper(val config: HttpConfig, registry: MeterRegistry) {
    abstract suspend fun getToken(): String

    val http by lazy { Client() }

    private val statusMeter by lazy {
        registry.createCounter(
            name = "http_client_status",
            tags = listOf("client", "path", "status")
        )
    }

    val latencyMeter by lazy {
        registry.createTimer(
            name = "${config.alias}_client_seconds",
        )
    }

    inner class Client internal constructor() {
        @PublishedApi
        internal val client = HttpClientFactory.create()

        suspend inline fun <reified REQ : Any, RES> get(
            path: Path,
            body: REQ,
            crossinline request: HttpRequestBuilder.() -> Unit = {},
        ): HttpResult<RES>? {
            val catchedResponse: Result<HttpResponse> =
                latencyMeter.timer {
                    runCatching {
                        client.get(config.host + path) {
                            bearerAuth(getToken())
                            accept(ContentType.Application.Json)
                            setBody(body)
                            apply(request)
                        }
                    }
                }

            return catchedResponse.fold(
                onSuccess = { wrapSuccess(path, it) },
                onFailure = { wrapFailure(path, it) }
            )
        }

        suspend inline fun <reified REQ : Any, RES> post(
            path: Path,
            body: REQ,
            crossinline request: HttpRequestBuilder.() -> Unit = {},
        ): HttpResult<RES>? {
            val catchedResponse: Result<HttpResponse> =
                latencyMeter.timer {
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

            return catchedResponse.fold(
                onSuccess = { wrapSuccess(path, it) },
                onFailure = { wrapFailure(path, it) }
            )
        }
    }


    @PublishedApi
    internal fun <RES> wrapSuccess(path: Path, response: HttpResponse): HttpResult<RES> {
        statusMeter.inc(listOf(config.alias, path.toString(), response.status.value.toString()))

        return when (response.status.value) {
            in 200..299 -> HttpResult.Ok(config, response)
            in 400..499 -> HttpResult.ClientError(config, response)
            in 500..599 -> HttpResult.ServerError(config, response)
            else -> HttpResult.ServerError(config, response)
        }
    }

    @PublishedApi
    internal fun <RES> wrapFailure(path: Path, e: Throwable): HttpResult<RES>? {
        statusMeter.inc(listOf(config.alias, path.toString(), "error"))
        config.log.error("Failed to execute POST $path", e)
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
