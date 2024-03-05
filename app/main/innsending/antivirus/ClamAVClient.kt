package innsending.antivirus

import com.fasterxml.jackson.annotation.JsonProperty
import innsending.Config
import innsending.http.ApiResult
import innsending.http.Path
import innsending.http.RestClient
import io.ktor.client.request.*
import io.ktor.http.*
import io.micrometer.core.instrument.MeterRegistry

class ClamAVClient(
    config: Config,
    registry: MeterRegistry,
) {
    private val client: RestClient = RestClient(config.clamAV, registry)

    suspend fun hasVirus(fil: ByteArray, contentType: ContentType): Boolean {
        val result = client.put(Path.from("/scan")) {
            contentType(contentType)
            accept(ContentType.Application.Json)
            setBody(fil)
        }

        val scanResult: List<ScanResult> = when (result) {
            is ApiResult.Ok -> result.getOrNull<List<ScanResult>>().orEmpty()
            is ApiResult.ClientError -> result.getNullAndTrace() ?: emptyList()
            is ApiResult.ServerError -> result.getNullAndTrace() ?: emptyList()
            is ApiResult.UnknownError -> result.getNullAndTrace() ?: emptyList()
        }

        return scanResult.any {
            it.result == ScanResult.Result.FOUND
        }
    }
}


data class ScanResult(@JsonProperty("Result") val result: Result) {
    enum class Result {
        FOUND,
        OK,
        NONE
    }

    companion object {
        val FEIL = listOf(ScanResult(Result.NONE))
    }
}