package innsending.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.micrometer.core.instrument.MeterRegistry

class RestClient(
    private val config: HttpConfig,
    registry: MeterRegistry
) {
    private val client by lazy { HttpClientFactory.default() }

    suspend fun get(
        path: Path,
        req: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult {
        return traceCall(path) {
            client.get(config.host + path, req)
        }.map { res ->
            ApiResult.from(config, res)
        }.getOrElse { err ->
            ApiResult.from(config, err)
        }
    }

    suspend fun post(
        path: Path,
        req: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult {
        return traceCall(path) {
            client.post(config.host + path, req)
        }.map { res ->
            ApiResult.from(config, res)
        }.getOrElse { err ->
            ApiResult.from(config, err)
        }
    }

    suspend fun put(
        path: Path,
        req: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult {
        return traceCall(path) {
            client.put(config.host + path, req)
        }.map { res ->
            ApiResult.from(config, res)
        }.getOrElse { err ->
            ApiResult.from(config, err)
        }
    }

    private suspend fun traceCall(path: Path, block: suspend (Path) -> HttpResponse): Result<HttpResponse> {
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
}
