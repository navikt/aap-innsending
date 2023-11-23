package innsending.routes

import TestConfig
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import innsending.server
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test

class MellomlagringTest {

    @Test
    fun `mellomlagring kan hentes igjen`() {
        testApplication {
            application {
                server(TestConfig.default(1,2,3))
            }
        }
    }

}

class Fakes{
    val azure = embeddedServer(Netty, port = 0, module = Application::).start(wait = true)

    fun Application.azure(){
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }
    }
}