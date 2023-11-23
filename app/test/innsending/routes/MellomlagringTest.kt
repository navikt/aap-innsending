package innsending.routes

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import innsending.Fakes
import innsending.Fakes.Companion.port
import innsending.TestConfig
import innsending.redis.JedisRedisFake
import innsending.server
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*
import kotlin.test.assertEquals


class MellomlagringTest {

    fun token (tokenxPort:Int):String = RSAKeyGenerator(2048).generate().let { key ->
        val myKey = JWKSet.parse("""{
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
}""").getKeyByKeyId("localhost-signer") as RSAKey
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(myKey.keyID).type(JOSEObjectType.JWT).build()
        val expiry = Date(Date().time + 1000 * 60)
        val claims = JWTClaimsSet.Builder()
            .issuer("tokenx")
            .audience("aap-innsending")
            .expirationTime(expiry)
            .claim("pid", "12345678910")
            .build()
        SignedJWT(header, claims).apply { sign(RSASSASigner(key.toPrivateKey())) }.serialize()

    }

    @Test
    fun `mellomlagring kan hentes igjen`() {
        Fakes().use {
            val jedis = JedisRedisFake()
            testApplication {
                application {
                    server(
                        config = TestConfig.default(it.azure.port, it.tokenx.port, it.joark.port),
                        redis = jedis
                    )

                }
                client.post("/mellomlagring/søknad") {
                    contentType(ContentType.Application.Json)
                    header("NAV-PersonIdent", "12345678910")
                    bearerAuth(token(it.tokenx.port))
                    setBody("""{"soknadId":"1234"}""")
                }.apply {
                    assertEquals(status,HttpStatusCode.OK)
                }
                assertEquals(String(requireNotNull(jedis.get("12345678910"))), """{"soknadId":"1234"}""")
            }
        }
    }

}

