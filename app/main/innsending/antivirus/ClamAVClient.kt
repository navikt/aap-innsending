package innsending.antivirus

import com.fasterxml.jackson.annotation.JsonProperty
import innsending.Config
import innsending.http.ApiResult
import innsending.http.HttpClientWrapper
import innsending.http.Path
import io.ktor.http.*
import io.micrometer.core.instrument.MeterRegistry

class ClamAVClient(
    config: Config,
    registry: MeterRegistry,
) : HttpClientWrapper(
    config.clamAV,
    registry,
) {
    suspend fun hasVirus(fil: ByteArray, contentType: ContentType): Boolean {
        val result = http.put(Path.from("/scan"), fil) {
            contentType(contentType)
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

    override suspend fun getToken(): String {
        return "no auth"
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