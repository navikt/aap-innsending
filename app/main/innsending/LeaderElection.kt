package innsending

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.InetAddress

data class LeaderResponse(
    val name: String,
    val last_update: String,
)

private val log = LoggerFactory.getLogger(LeaderElection::class.java)

class LeaderElection(
    private val config: Config,
    private val client: HttpClient = silentHttpClient(),
) {
    private val hostname = InetAddress.getLocalHost().hostName

    fun isLeader(): Boolean {
        try {
            val response = runBlocking {
                client.get("http://${config.leaderElectorPath}").body<LeaderResponse>()
            }
            val leader = response.name
            val isLeader = hostname == leader
            if (isLeader) {
                log.info("I ($hostname) am the leader")
            } else {
                log.info("I ($hostname) am not the leader, leader is $leader")
            }
            return isLeader
        } catch (e: Exception) {
            log.error("Failed to get leader", e)
            return false
        }
    }
}

fun silentHttpClient(): HttpClient =
    HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                registerModule(JavaTimeModule())
            }
        }
    }
