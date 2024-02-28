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
        @PublishedApi
        internal val client by lazy {
            HttpClientFactory.create()
        }

        suspend fun get(
            path: Path,
            request: HttpRequestBuilder.() -> Unit = {},
        ): ApiResult {
            val maybeResult = doCall(path) {
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
            val maybeResult = doCall(path) {
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

        @PublishedApi
        internal suspend fun doCall(path: Path, block: suspend (Path) -> HttpResponse): Result<HttpResponse> {
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

class Path private constructor(private val path: String) {
    init {
        require(path.startsWith("/")) { "Path must start with /" }
    }

    companion object {
        fun from(path: String) = Path(path)
    }

    override fun toString(): String = path
}
