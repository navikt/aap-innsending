package innsending.jobb

import innsending.db.InnsendingRepo
import innsending.kafka.KafkaProducer
import innsending.kafka.MinSideProducerHolder
import innsending.postgres.InnsendingType
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.ConnectionJobbSpesifikasjon
import no.nav.aap.motor.JobbInput
import no.nav.aap.motor.JobbUtfører

class MinSideNotifyJobbUtfører(
    val innsendingRepo: InnsendingRepo,
    val producer: KafkaProducer
) : JobbUtfører {


    override fun utfør(input: JobbInput) {
        val innsendingId = input.sakId()
        val innsending = innsendingRepo.hent(innsendingId)
        require(innsending.type == InnsendingType.SOKNAD)

        producer.produce(innsending.personident)
    }

    companion object : ConnectionJobbSpesifikasjon {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            val innsendingRepo = InnsendingRepo(connection)

            return MinSideNotifyJobbUtfører(
                innsendingRepo,
                MinSideProducerHolder.producer()
            )
        }

        override val type: String = "innsending.minside"

        override val navn: String = "Minside notify"

        override val beskrivelse: String =
            "Sier ifra til min side om at en søknad er sendt inn"
    }
}
