package innsending.antivirus

import com.fasterxml.jackson.annotation.JsonProperty
import innsending.http.HttpClientFactory
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class ClamAVClient(private val host: String) {
    private val httpClient = HttpClientFactory.create()
    suspend fun hasVirus(fil: ByteArray, contentType: ContentType): Boolean =
        httpClient.put("$host/scan") {
            accept(ContentType.Application.Json)
            setBody(fil)
            contentType(contentType)
        }
            .body<List<ScanResult>>()
            .any { it.result == ScanResult.Result.FOUND }
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