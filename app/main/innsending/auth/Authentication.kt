package innsending.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import innsending.Config
import innsending.SECURE_LOGGER
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.util.*
import java.util.concurrent.TimeUnit

fun Application.authentication(config: Config) {
    val idPortenProvider: JwkProvider = JwkProviderBuilder(config.tokenx.jwks)
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    authentication {
        jwt("tokenx") {
            verifier(idPortenProvider, config.tokenx.issuer)
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, "TokenX validering feilet") }
            validate { cred ->
                val now = Date()

                if (config.tokenx.clientId !in cred.audience) {
                    SECURE_LOGGER.warn("TokenX validering feilet (clientId var ikke i audience: ${cred.audience}")
                    return@validate null
                }

                if (cred.expiresAt?.before(now) == true) {
                    SECURE_LOGGER.warn("TokenX validering feilet (expired at: ${cred.expiresAt})")
                    return@validate null
                }

                if (cred.notBefore?.after(now) == true) {
                    SECURE_LOGGER.warn("TokenX validering feilet (not valid yet, valid from: ${cred.notBefore})")
                    return@validate null
                }

                if (cred.issuedAt?.after(cred.expiresAt ?: return@validate null) == true) {
                    SECURE_LOGGER.warn("TokenX validering feilet (issued after expiration: ${cred.issuedAt} )")
                    return@validate null
                }

                JWTPrincipal(cred.payload)
            }
        }
    }
}
