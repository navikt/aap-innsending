package innsending.postgres

import innsending.PostgresConfig
import innsending.postgres.Hikari.flywayMigration
import innsending.routes.Innsending
import innsending.routes.Vedlegg
import java.util.*
import javax.sql.DataSource

class PostgresRepo(
    config: PostgresConfig,
    private val hikari: DataSource = Hikari.createDatasource(config).apply(::flywayMigration),
) {

    fun hentAlleInnsendinger(): List<UUID> = hikari.transaction { con ->
        PostgresDAO.selectInnsendinger(con)
    }

    fun hentInnsending(søknadId: UUID): InnsendingMedFiler = hikari.transaction { con ->
        PostgresDAO.selectInnsendingMedVedlegg(søknadId, con)
    }

    fun slettInnsending(id: UUID) = hikari.transaction { con ->
        PostgresDAO.deleteInnsending(id, con)
    }

    fun lagreSøknadMedVedlegg(
        søknadId: UUID,
        personIdent: String,
        innsending: Innsending,
        vedlegg: List<Pair<Vedlegg, ByteArray>>
    ) {
        hikari.transaction { con ->
            PostgresDAO.insertInnsending(
                søknadId = søknadId,
                personident = personIdent,
                søknad = innsending.soknad,
                con = con,
            )

            vedlegg.forEach { (vedlegg, data) ->
                PostgresDAO.insertVedlegg(
                    søknadId = søknadId,
                    vedleggId = UUID.fromString(vedlegg.id),
                    tittel = vedlegg.tittel,
                    vedlegg = data,
                    con = con,
                )
            }
        }
    }

    fun lagreVedlegg(søknadId: UUID, vedleggId: UUID, vedlegg: ByteArray, tittel: String) {
        hikari.transaction { con ->
            PostgresDAO.insertVedlegg(
                søknadId = søknadId,
                vedleggId = vedleggId,
                vedlegg = vedlegg,
                tittel = tittel,
                con = con,
            )
        }
    }
}
