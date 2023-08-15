package innsending.kafka

import no.nav.aap.kafka.streams.v2.Topic
import no.nav.aap.kafka.streams.v2.serde.JsonSerde

object Topics {
    val innsending = Topic("aap.innsending.v1", JsonSerde.jackson<Any>())
}
