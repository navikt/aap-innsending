package innsending.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import innsending.logger
import innsending.TokenXConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.util.*
import java.util.concurrent.TimeUnit

const val TOKENX = "tokenx"

internal fun ApplicationCall.personident(): String {
    return requireNotNull(principal<JWTPrincipal>()) {
        "principal mangler i ktor auth"
    }.getClaim("pid", String::class)
        ?: error("pid mangler i tokenx claims")
}

fun Application.authentication(config: TokenXConfig) {
    val idPortenProvider: JwkProvider = JwkProviderBuilder(config.jwks.toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    authentication {
        jwt(TOKENX) {
            verifier(idPortenProvider, config.issuer)
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, "TokenX validering feilet") }
            validate { cred ->
                val now = Date()

                if (config.clientId !in cred.audience) {
                    logger.warn("TokenX validering feilet (clientId var ikke i audience: ${cred.audience}")
                    return@validate null
                }

                if (cred.expiresAt?.before(now) == true) {
                    logger.warn("TokenX validering feilet (expired at: ${cred.expiresAt})")
                    return@validate null
                }

                if (cred.notBefore?.after(now) == true) {
                    logger.warn("TokenX validering feilet (not valid yet, valid from: ${cred.notBefore})")
                    return@validate null
                }

                if (cred.issuedAt?.after(cred.expiresAt ?: return@validate null) == true) {
                    logger.warn("TokenX validering feilet (issued after expiration: ${cred.issuedAt} )")
                    return@validate null
                }

                if (cred.getClaim("pid", String::class) == null) {
                    logger.warn("TokenX validering feilet (personident mangler i claims)")
                    return@validate null
                }

                JWTPrincipal(cred.payload)
            }
        }
    }
}
