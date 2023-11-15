package innsending.postgres

import innsending.routes.Innsending
import innsending.routes.Vedlegg
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

class PostgresRepo(private val ds: DataSource) {
    private val innsendingDAO = InnsendingDAO(ds)

    private fun transaction(block: (connection: Connection, innsendingDao: InnsendingDAO) -> Unit) {
        ds.connection.transaction {
            block(it, innsendingDAO)
        }
    }

    fun lagreSøknadMedVedlegg(
        søknadId: UUID,
        personIdent: String,
        innsending: Innsending,
        vedlegg: List<Pair<Vedlegg, ByteArray>>
    ) {
        transaction { con, dao ->
            dao.insertInnsendingStatement(
                søknadId = søknadId,
                personident = personIdent,
                søknad = innsending.soknad,
                connection = con
            )

            vedlegg.forEach { (vedlegg, data) ->
                dao.insertVedleggStatement(
                    søknadId = søknadId,
                    vedleggId = UUID.fromString(vedlegg.id),
                    tittel = vedlegg.tittel,
                    vedlegg = data,
                    connection = con
                )
            }
        }
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
