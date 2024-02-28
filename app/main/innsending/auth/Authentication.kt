package innsending.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import innsending.SECURE_LOG
import innsending.TokenXConfig
import innsending.dto.ErrorCode
import innsending.dto.error
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.*
import java.util.concurrent.TimeUnit

const val TOKENX = "tokenx"

class PersonidentException(val error: ErrorCode) : RuntimeException(error.msg)

internal fun ApplicationCall.personident(): String {
    val principal = principal<JWTPrincipal>()
        ?: throw PersonidentException(ErrorCode.REQ_MISSING_JWT)

    return principal.getClaim("pid", String::class)
        ?: throw PersonidentException(ErrorCode.REQ_MISSING_PID)
}

fun Application.authentication(config: TokenXConfig) {
    val idPortenProvider: JwkProvider = JwkProviderBuilder(config.jwks.toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    authentication {
        jwt(TOKENX) {
            verifier(idPortenProvider, config.issuer)
            challenge { _, _ -> call.error(ErrorCode.UNAUTH_TOKENX) }
            validate { cred ->
                val now = Date()

                if (config.clientId !in cred.audience) {
                    SECURE_LOG.warn("TokenX validering feilet (clientId var ikke i audience: ${cred.audience}")
                    return@validate null
                }

                if (cred.expiresAt?.before(now) == true) {
                    SECURE_LOG.warn("TokenX validering feilet (expired at: ${cred.expiresAt})")
                    return@validate null
                }

                if (cred.notBefore?.after(now) == true) {
                    SECURE_LOG.warn("TokenX validering feilet (not valid yet, valid from: ${cred.notBefore})")
                    return@validate null
                }

                if (cred.issuedAt?.after(cred.expiresAt ?: return@validate null) == true) {
                    SECURE_LOG.warn("TokenX validering feilet (issued after expiration: ${cred.issuedAt} )")
                    return@validate null
                }

                if (cred.getClaim("pid", String::class) == null) {
                    SECURE_LOG.warn("TokenX validering feilet (personident mangler i claims)")
                    return@validate null
                }

                JWTPrincipal(cred.payload)
            }
        }
    }
}
