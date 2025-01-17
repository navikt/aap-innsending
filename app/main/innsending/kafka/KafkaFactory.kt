package innsending.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.Serdes.StringSerde
import org.apache.kafka.common.serialization.Serializer
import java.util.*

data class KafkaConfig(
    val brokers: String,
    val truststorePath: String,
    val keystorePath: String,
    val credstorePsw: String,
)

class KafkaFactory private constructor() {
    companion object {
        fun <T : Any> createProducer(
            clientId: String,
            config: KafkaConfig,
            serializer: Serializer<T>,
        ): Producer<String, T> {
            fun properties(): Properties = Properties().apply {
                this[CommonClientConfigs.CLIENT_ID_CONFIG] = clientId
                this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = config.brokers
                this[ProducerConfig.ACKS_CONFIG] = "all"
                this[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "5"
                this[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SSL"
                this[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "JKS"
                this[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = config.truststorePath
                this[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = config.credstorePsw
                this[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
                this[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = config.keystorePath
                this[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = config.credstorePsw
                this[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = config.credstorePsw
                this[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] = ""
            }

            return KafkaProducer(properties(), StringSerde().serializer(), serializer)
        }

        fun createProducer(clientId: String, config: KafkaConfig): Producer<String, String> =
            createProducer(clientId, config, StringSerde().serializer())
    }
}