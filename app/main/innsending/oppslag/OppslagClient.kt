package innsending.oppslag

import innsending.Config
import innsending.http.HttpClientFactory
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.aap.ktor.client.auth.azure.AzureAdTokenProvider
import java.util.*

class OppslagClient(config: Config) {
    private val client = HttpClientFactory.create()
    private val oppslagConfig = config.oppslag
    private val tokenProvider = AzureAdTokenProvider(config.azure)

    suspend fun hentNavn(personident: String): Navn {
        val res = client.get(oppslagConfig.host + "/person/navn") {
            accept(ContentType.Application.Json)
            bearerAuth(tokenProvider.getClientCredentialToken(oppslagConfig.scope))
            header("personident", personident)
            header("Nav-CallId", UUID.randomUUID().toString())
        }

        return if (res.status.isSuccess()) {
            res.body<Navn>()
        } else {
            error("klarte ikke hente navn fra PDL")
        }
    }
}

data class Navn(
    val fornavn: String,
    val etternavn: String,
)