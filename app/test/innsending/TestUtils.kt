package innsending

import innsending.http.json
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.server.testing.*

internal val ApplicationTestBuilder.http
    get() = createClient {
        install(ContentNegotiation) {
            json()
        }
    }
