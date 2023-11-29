package innsending.postgres

import innsending.PostgresConfig
import innsending.postgres.Hikari.flywayMigration
import innsending.routes.Innsending
import innsending.routes.Vedlegg
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class PostgresRepo(
    config: PostgresConfig,
    environment: String,
    private val hikari: DataSource = Hikari.createDatasource(config).apply {
        flywayMigration(this, environment)
    },
) {
    fun loggførJournalføring(
        personIdent: String,
        mottattDato: LocalDateTime,
        journalpostId: String
    ) {
        hikari.transaction { con ->
            PostgresDAO.insertLogg(
                personident = personIdent,
                mottattDato = mottattDato,
                journalpostId = journalpostId,
                con = con
            )
        }
    }

    fun hentAlleInnsendinger(): List<UUID> = hikari.transaction { con ->
        PostgresDAO.selectInnsendinger(con)
    }

    fun hentInnsending(søknadId: UUID): InnsendingMedFiler = hikari.transaction { con ->
        PostgresDAO.selectInnsendingMedVedlegg(søknadId, con)
    }

    fun slettInnsending(id: UUID) = hikari.transaction { con ->
        PostgresDAO.deleteInnsending(id, con)
    }

    fun lagreInnsending(
        innsendingId: UUID,
        personIdent: String,
        mottattDato: LocalDateTime,
        innsending: Innsending,
        vedlegg: List<Pair<Vedlegg, ByteArray>>
    ) {
        hikari.transaction { con ->
            PostgresDAO.insertInnsending(
                innsendingId = innsendingId,
                personident = personIdent,
                mottattDato = mottattDato,
                data = innsending.soknad,
                con = con,
            )

            vedlegg.forEach { (vedlegg, data) ->
                PostgresDAO.insertVedlegg(
                    innsendingId = innsendingId,
                    vedleggId = UUID.fromString(vedlegg.id),
                    tittel = vedlegg.tittel,
                    vedlegg = data,
                    con = con,
                )
            }
        }
    }
}
