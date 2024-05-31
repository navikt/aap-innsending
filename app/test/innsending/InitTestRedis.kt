package innsending

import com.redis.testcontainers.RedisContainer
import java.net.URI

object InitTestRedis {
    val uri: URI

    init {
        var uri: URI = URI.create("redis://localhost:6379")
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            val redis = RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG))
            redis.start()
            uri = URI(redis.redisURI)
        }
        Thread.sleep(10000L)
        this.uri = uri
    }
}
