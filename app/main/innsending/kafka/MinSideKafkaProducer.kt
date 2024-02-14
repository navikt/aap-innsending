package innsending.kafka

import no.nav.aap.kafka.KafkaConfig
import no.nav.aap.kafka.KafkaFactory
import no.nav.tms.microfrontend.MicrofrontendMessageBuilder
import no.nav.tms.microfrontend.Sensitivitet
import org.apache.kafka.clients.producer.ProducerRecord

class MinSideKafkaProducer(config: KafkaConfig): KafkaProducer, AutoCloseable {
    private val producer = KafkaFactory.createProducer("min-side-mikrofrontend", config)
    private val topic = "minside.aapen-microfrontend-v1"

    override fun produce(personident: String) {
        val record = createRecord(personident)
        producer.send(record)
    }

    private fun createRecord(key: String): ProducerRecord<String, String> {
        val value = MicrofrontendMessageBuilder.enable {
            ident = "12345678910"
            initiatedBy = "aap"
            microfrontendId = "aap-min-side-microfrontendaap-min-side-microfrontend"
            sensitivitet = Sensitivitet.HIGH
        }.text()

        return ProducerRecord(topic, key, value)
    }

    override fun close() = producer.close()
}
