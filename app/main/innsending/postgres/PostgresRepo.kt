package innsending.postgres

import innsending.PostgresConfig
import innsending.postgres.Hikari.flywayMigration
import innsending.routes.Innsending
import innsending.routes.Vedlegg
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

class PostgresRepo(
    config: PostgresConfig,
    private val hikari: DataSource = Hikari.createDatasource(config).apply(::flywayMigration),
    private val innsendingDAO: InnsendingDAO = InnsendingDAO(hikari),
) {

    private fun transaction(block: Connection.(InnsendingDAO) -> Unit) {
        hikari.connection.transaction {
            block(it, innsendingDAO)
        }
    }

    fun lagreSøknadMedVedlegg(
        søknadId: UUID,
        personIdent: String,
        innsending: Innsending,
        vedlegg: List<Pair<Vedlegg, ByteArray>>
    ) {
        transaction { dao ->
            dao.insertInnsendingStatement(
                søknadId = søknadId,
                personident = personIdent,
                søknad = innsending.soknad,
                connection = this
            )

            vedlegg.forEach { (vedlegg, data) ->
                dao.insertVedleggStatement(
                    søknadId = søknadId,
                    vedleggId = UUID.fromString(vedlegg.id),
                    tittel = vedlegg.tittel,
                    vedlegg = data,
                    connection = this
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
