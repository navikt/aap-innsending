package innsending

import com.redis.testcontainers.RedisContainer
import innsending.redis.Redis
import java.net.URI

object InitTestRedis {
    private const val TI_SEKUNDER_MILLIS: Long = 10_000
    private const val HALVT_SEKUND_MILLIS: Long = 500

    val uri: URI

    init {

        val redisContainer = RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG))
        redisContainer.start()

        this.uri =  URI(redisContainer.redisURI)

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
