package innsending.kafka

interface KafkaProducer: AutoCloseable {
    fun produce(personident: String)
}