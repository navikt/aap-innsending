package innsending.kafka

object MinSideProducerHolder {

    private var producer: KafkaProducer? = null

    fun setProducer(producer: KafkaProducer) {
        this.producer = producer
    }

    fun producer(): KafkaProducer {
        return requireNotNull(producer) { "Producer was not set." }
    }

}
