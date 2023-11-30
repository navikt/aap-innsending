package innsending.postgres

import innsending.routes.Fil
import innsending.routes.Innsending
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class PostgresRepo(private val hikari: DataSource) {
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
        PostgresDAO.selectInnsendingMedFiler(søknadId, con)
    }

    fun slettInnsending(id: UUID) = hikari.transaction { con ->
        PostgresDAO.deleteInnsending(id, con)
    }

    fun lagreInnsending(
        innsendingId: UUID,
        personIdent: String,
        mottattDato: LocalDateTime,
        innsending: Innsending,
        fil: List<Pair<Fil, ByteArray>>
    ) {
        hikari.transaction { con ->
            PostgresDAO.insertInnsending(
                innsendingId = innsendingId,
                personident = personIdent,
                mottattDato = mottattDato,
                data = innsending.soknad,
                con = con,
            )

            fil.forEach { (fil, data) ->
                PostgresDAO.insertFil(
                    innsendingId = innsendingId,
                    filId = UUID.fromString(fil.id),
                    tittel = fil.tittel,
                    fil = data,
                    con = con,
                )
            }
        }
    }
}
