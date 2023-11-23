package innsending

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking

class Fakes : AutoCloseable {
    val azure = embeddedServer(Netty, port = 0, module = Application::azure).apply { start() }
    val tokenx = embeddedServer(Netty, port = 0, module = Application::tokenx).apply { start() }
    val joark = embeddedServer(Netty, port = 0, module = Application::joark).apply { start() }
    override fun close() {
        azure.stop()
        tokenx.stop()
        joark.stop()
    }

    companion object {
        val NettyApplicationEngine.port get() = runBlocking { resolvedConnectors() }.first { it.type == ConnectorType.HTTP }.port
    }
}

fun Application.azure() {
    install(ContentNegotiation) {
        jackson()
    }
}

fun Application.tokenx() {
    install(ContentNegotiation) {
        jackson()
    }

    routing {
        get("/.well-known/jwks.json") {
            call.respondText(
                """{
    "keys": [
        {
            "kty": "RSA",
            "d": "MRf73iiXUEhJFxDTtJ5rEHNQsAG8XFuXkz9vXXbMp1_OTo11bEx3SnHiwmO_mSAAeXWNJniLw07V1-nk551h5in_ueAPwXTOf8qddacvDEBZwcxeqfu_Kjh1R0ji8Xn1a037CpH2IO34Lyw2gmsGFdMZgDwa5Z0KJjPCU6W8tF6CA-2omAdNzrFaWtaPFpBC0NzYaaB111bKIXxngG97Cnu81deEEKmX-vL-O4tpvUUybuquxrlFvVlTeYlrQqv50_IKsKSYkg-iu1cbqIiWrRq9eTmA6EppmZbqHjKSM5JYFbPB_oZ9QeHKnp1_MTom-jKMEpw18qq-PzdX_skZWQ",
            "e": "AQAB",
            "use": "sig",
            "kid": "localhost-signer",
            "alg": "RS256",
            "n": "lFTMP9TSUwLua0G8M7foqmdUS2us1-JOF8H_tClVG3IEQMRvMmHJoGSdldWDHsNwRG3Wevl_8fZoGocw9hPqj93j-vI4-ZkbxwhPyRqlS0FNIPD1Ln5R6AmHu7b-paRIz3lvqpyTRwnGBI9weE4u6WOpOQ8DjJMNPq4WcM42AgDJAvc6UuhcWW_MLIsjkKp_VYKxzthSuiRAxXi8Pz4ZhiTAEZI-UN61DYU9YEFNujg5XtIQsRwQn1Vj7BknGwkdf_iCGJgDlKUOz9hAojOMXTAwetUx6I5nngIM5vaXWJCmKn6SzcTYgHWWVrn8qaSazioaydLaYN9NuQ0MdIvsQw"
        }
    ]
}""".trimIndent()
            )
        }
        get("/jwks") {
            call.respondText(
                """{
    "keys": [
        {
            "kty": "RSA",
            "d": "MRf73iiXUEhJFxDTtJ5rEHNQsAG8XFuXkz9vXXbMp1_OTo11bEx3SnHiwmO_mSAAeXWNJniLw07V1-nk551h5in_ueAPwXTOf8qddacvDEBZwcxeqfu_Kjh1R0ji8Xn1a037CpH2IO34Lyw2gmsGFdMZgDwa5Z0KJjPCU6W8tF6CA-2omAdNzrFaWtaPFpBC0NzYaaB111bKIXxngG97Cnu81deEEKmX-vL-O4tpvUUybuquxrlFvVlTeYlrQqv50_IKsKSYkg-iu1cbqIiWrRq9eTmA6EppmZbqHjKSM5JYFbPB_oZ9QeHKnp1_MTom-jKMEpw18qq-PzdX_skZWQ",
            "e": "AQAB",
            "use": "sig",
            "kid": "localhost-signer",
            "alg": "RS256",
            "n": "lFTMP9TSUwLua0G8M7foqmdUS2us1-JOF8H_tClVG3IEQMRvMmHJoGSdldWDHsNwRG3Wevl_8fZoGocw9hPqj93j-vI4-ZkbxwhPyRqlS0FNIPD1Ln5R6AmHu7b-paRIz3lvqpyTRwnGBI9weE4u6WOpOQ8DjJMNPq4WcM42AgDJAvc6UuhcWW_MLIsjkKp_VYKxzthSuiRAxXi8Pz4ZhiTAEZI-UN61DYU9YEFNujg5XtIQsRwQn1Vj7BknGwkdf_iCGJgDlKUOz9hAojOMXTAwetUx6I5nngIM5vaXWJCmKn6SzcTYgHWWVrn8qaSazioaydLaYN9NuQ0MdIvsQw"
        }
    ]
}""".trimIndent()
            )
        }

    }
}

fun Application.joark() {
    install(ContentNegotiation) {
        jackson()
    }

    routing {
        post("/rest/journalpostapi/v1/journalpost") {
            call.respond(HttpStatusCode.OK, "OK")
        }
    }
}