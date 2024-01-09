package innsending.postgres

import com.fasterxml.jackson.databind.ObjectMapper
import innsending.routes.Fil
import innsending.routes.Innsending
import innsending.routes.Logg
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

enum class InnsendingType { SOKNAD, ETTERSENDING }

class PostgresRepo(private val hikari: DataSource) {
    fun loggførJournalføring(
        personIdent: String,
        mottattDato: LocalDateTime,
        journalpostId: String,
        type: InnsendingType
    ) {
        hikari.transaction { con ->
            PostgresDAO.insertLogg(
                personident = personIdent,
                mottattDato = mottattDato,
                journalpostId = journalpostId,
                type = type.name,
                con = con
            )
        }
    }

    fun hentAlleSøknader(personident: String): List<Logg> = hikari.transaction { con ->
        PostgresDAO.selectLogg(personident, InnsendingType.SOKNAD.name, con)
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
                data = innsending.kvittering?.toByteArray(),
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

fun Map<String,Any>.toByteArray(): ByteArray{
    val mapper = ObjectMapper()
    return mapper.writeValueAsBytes(this)
}
