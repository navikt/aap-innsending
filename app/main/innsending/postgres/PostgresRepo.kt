package innsending.postgres

import innsending.routes.Innsending
import innsending.routes.Vedlegg
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

class PostgresRepo(val ds: DataSource) {
    private val innsendingDAO = InnsendingDAO(ds)
    fun transaction(block: (connection: Connection, innsendingDao: InnsendingDAO) -> Unit) {
        ds.connection.transaction {
            block(it, innsendingDAO)
        }
    }

    fun lagreSøknadMedVedlegg(søknadId: UUID, personIdent: String, innsending: Innsending, vedlegg: List<Pair<Vedlegg, ByteArray>>) {
        transaction { con, dao ->
            dao.insertInnsendingStatement(
                søknadId = søknadId,
                personident = personIdent,
                søknad = innsending.søknad,
                connection = con
            )


            vedlegg.forEach { (vedlegg,data) ->

                dao.insertVedleggStatement(
                    søknadId = søknadId.toString(),
                    vedleggId = vedlegg.id,
                    tittel = vedlegg.tittel,
                    vedlegg = data,
                    connection = con
                )
            }
        }
    }


fun lagreVedlegg(søknadId: String, vedleggId: String, vedlegg: ByteArray, tittel: String) {
    innsendingDAO.insertVedlegg(
        søknadId = søknadId,
        vedleggId = vedleggId,
        vedlegg = vedlegg,
        tittel = tittel,
    )

}
}
