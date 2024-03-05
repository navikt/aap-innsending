package innsending.redis

import java.net.InetAddress

class LeaderElection(private val redis: Redis) {

    private val hostname = InetAddress.getLocalHost().hostName

    fun isLeader(): Boolean {
        electLeader()
        val key = Key(hostname, "leader")
        return redis[key] != null
    }

    private fun electLeader() {
        val firstOrSelf = redis.getKeysByPrefix("pod")
            .map { key -> key.copy(prefix = "leader") }
            .firstOrNull()
            ?: registerSelf().copy(prefix = "leader")

        redis.set(firstOrSelf, byteArrayOf(), 3)
    }

    private fun registerSelf(): Key {
        val key = Key(hostname, "pod")
        redis.set(key, byteArrayOf(), 3)
        return key
    }
}
