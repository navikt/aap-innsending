package innsending.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.core.instrument.MeterRegistry

abstract class HttpClientWrapper(val config: HttpConfig, registry: MeterRegistry) {
    abstract suspend fun getToken(): String

    val http by lazy {
        Client()
    }

    private val statusMeter by lazy {
        registry.createCounter(
            name = "http_client_status",
            tags = listOf("client", "path", "status")
        )
    }

    private val latencyMeter by lazy {
        registry.createTimer(
            name = "${config.alias}_client_seconds",
        )
    }

    inner class Client internal constructor() {
        val client by lazy {
            HttpClientFactory.create()
        }

        suspend fun get(
            path: Path,
            request: HttpRequestBuilder.() -> Unit = {},
        ): ApiResult {
            val maybeResult = traceCall(path) {
                client.get(config.host + path) {
                    bearerAuth(getToken())
                    accept(ContentType.Application.Json)
                    apply(request)
                }
            }

            return maybeResult
                .map { ApiResult.from(config, it) }
                .getOrElse { ApiResult.from(config, it) }
        }

        suspend inline fun <reified REQ : Any> post(
            path: Path,
            body: REQ,
            crossinline request: HttpRequestBuilder.() -> Unit = {},
        ): ApiResult {
            val maybeResult = traceCall(path) {
                client.post(config.host + path) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(getToken())
                    accept(ContentType.Application.Json)
                    setBody(body)
                    apply(request)
                }
            }

            return maybeResult
                .map { response -> ApiResult.from(config, response) }
                .getOrElse { err -> ApiResult.from(config, err) }
        }

        suspend inline fun <reified REQ : Any> put(
            path: Path,
            body: REQ,
            crossinline request: HttpRequestBuilder.() -> Unit = {},
        ): ApiResult {
            val maybeResult = traceCall(path) {
                client.put(config.host + path) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(getToken())
                    accept(ContentType.Application.Json)
                    setBody(body)
                    apply(request)
                }
            }

            return maybeResult
                .map { response -> ApiResult.from(config, response) }
                .getOrElse { err -> ApiResult.from(config, err) }
        }

        //        @PublishedApi
        suspend fun traceCall(path: Path, block: suspend (Path) -> HttpResponse): Result<HttpResponse> {
            return latencyMeter.timer {
                runCatching {
                    block(path)
                }.onFailure {
                    statusMeter.inc(listOf(config.alias, path.toString(), "error"))
                    config.log.error("Failed to execute POST $path", it)
                }.onSuccess {
                    statusMeter.inc(listOf(config.alias, path.toString(), it.status.value.toString()))
                }
            }
        }
    }
}

@JvmInline
value class Path private constructor(private val path: String) {

    companion object {
        fun from(path: String): Path {
            require(path.startsWith("/")) { "Path must start with /" }
            return Path(path)
        }
    }

    override fun toString(): String = path
}
