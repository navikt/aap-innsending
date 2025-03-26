package innsending.kafka

import innsending.logger
import no.nav.tms.microfrontend.MicrofrontendMessageBuilder
import no.nav.tms.microfrontend.Sensitivitet
import org.apache.kafka.clients.producer.ProducerRecord

class MinSideKafkaProducer(config: KafkaConfig) : KafkaProducer, AutoCloseable {
    private val producer = KafkaFactory.createProducer("min-side-mikrofrontend", config)
    private val topic = "min-side.aapen-microfrontend-v1"

    override fun produce(personident: String) {
        val record = createRecord(personident)
        producer.send(record) { metadata, err ->
            if (err != null) {
                logger.error("Klarte ikke enable mikrofrontend for bruker", err)
                throw KafkaProducerException("Klarte ikke enable mikrofrontend for bruker")
            } else {
                logger.debug("Enablet mikrofrontend for {}: {}", personident, metadata)
            }
        }.get() // Blocking call to ensure the message is sent
    }

    private fun createRecord(personident: String): ProducerRecord<String, String> {
        val json = MicrofrontendMessageBuilder.enable {
            ident = personident
            initiatedBy = "aap"
            microfrontendId = "aap" // todo: bytt til aap-min-side-microfrontend i aap-min-side-microfrontend sin workflow når vi har skrudd av soknad-api
            sensitivitet = Sensitivitet.HIGH
        }.text()

        return ProducerRecord(topic, personident, json)
    }

    override fun close() = producer.close()
}
