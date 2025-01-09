package innsending.redis

import org.slf4j.LoggerFactory
import java.net.InetAddress

/**
 *  Time to live in seconds should be set to a value higher than the scheduler interval
 */
private const val TTL = 61L

class LeaderElector(private val redis: Redis) {
    private val pod = InetAddress.getLocalHost().hostName
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Check if this pod is the leader
     */
    fun elected(): Boolean {
        return false
    }

    /**
     * First to claim leadership wins
     */
    private fun electSelf(key: Key): Boolean {
        log.info("Electing $pod as leader for $TTL sec")
        redis.set(key, pod.toByteArray(), TTL)
        return true
    }
}
