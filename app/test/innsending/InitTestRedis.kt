package innsending

import com.redis.testcontainers.RedisContainer
import innsending.redis.Redis
import java.net.URI

object InitTestRedis {
    private const val TI_SEKUNDER_MILLIS: Long = 10_000
    private const val HALVT_SEKUND_MILLIS: Long = 500

    val uri: URI

    init {
        var uri: URI = URI.create("redis://localhost:6379")
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            val redis = RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG))
            redis.start()
            uri = URI(redis.redisURI)
        }
        this.uri = uri

        val redis = Redis(uri)
        val timeout = System.currentTimeMillis() + TI_SEKUNDER_MILLIS
        while (System.currentTimeMillis() < timeout) {
            try {
                redis.ready()
                break
            } catch (_: Throwable) {
                Thread.sleep(HALVT_SEKUND_MILLIS)
            }
        }
        redis.close()
    }
}
