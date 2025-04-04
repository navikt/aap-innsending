package innsending.jobb

import innsending.db.InnsendingRepo
import innsending.kafka.KafkaProducer
import innsending.kafka.MinSideProducerHolder
import innsending.postgres.InnsendingType
import no.nav.aap.komponenter.dbconnect.DBConnection
import no.nav.aap.motor.Jobb
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

    companion object : Jobb {
        override fun konstruer(connection: DBConnection): JobbUtfører {
            val innsendingRepo = InnsendingRepo(connection)

            return MinSideNotifyJobbUtfører(
                innsendingRepo,
                MinSideProducerHolder.producer()
            )
        }

        override fun type(): String {
            return "innsending.minside"
        }

        override fun navn(): String {
            return "Minside notify"
        }

        override fun beskrivelse(): String {
            return "Sier ifra til min side om at en søknad er sendt inn"
        }
    }
}
