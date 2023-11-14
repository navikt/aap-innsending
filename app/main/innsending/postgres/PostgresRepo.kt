package innsending.postgres

import java.util.*
import javax.sql.DataSource

class PostgresRepo(ds: DataSource) {
    private val innsendingDAO = InnsendingDAO(ds)

    fun lagreSøknad(søknadId: UUID, personident: String, søknad: ByteArray) {
        innsendingDAO.insertInnsending(
            søknadId = søknadId,
            personident = personident,
            søknad = søknad,
        )
    }

    fun lagreVedlegg(søknadId: UUID, vedleggId: UUID, vedlegg: ByteArray, tittel: String) {
        innsendingDAO.insertVedlegg(
            søknadId = søknadId,
            vedleggId = vedleggId,
            vedlegg = vedlegg,
            tittel = tittel,
        )

    }
}
