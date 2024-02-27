package innsending

import innsending.http.HttpClientFactory
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import java.net.InetAddress

data class LeaderResponse(
    val name: String,
    val last_update: String,
)

class LeaderElection(
    private val config: Config,
    private val client: HttpClient = HttpClientFactory.create(),
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
                SECURE_LOGGER.info("I ($hostname) am the leader")
            } else {
                SECURE_LOGGER.info("I ($hostname) am not the leader, leader is $leader")
            }
            return isLeader
        } catch (e: Exception) {
            SECURE_LOGGER.error("Failed to get leader", e)
            return false
        }
    }
}