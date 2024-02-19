package innsending.postgres

import com.fasterxml.jackson.databind.ObjectMapper
import innsending.dto.FilMetadata
import innsending.dto.Innsending
import innsending.dto.MineAapSoknad
import innsending.dto.MineAapSoknadMedEttersendinger
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

enum class InnsendingType { SOKNAD, ETTERSENDING }

class PostgresRepo(private val hikari: DataSource) {

    fun erRefTilknyttetPersonIdent(personident: String, ref: UUID): Boolean {
        return hikari.transaction { con ->
            PostgresDAO.erRefTilknyttetPersonIdent(personident, ref, con)
        }
    }

    fun loggførJournalføring(
        personIdent: String,
        mottattDato: LocalDateTime,
        journalpostId: String,
        innsendingsId: UUID,
        type: InnsendingType
    ) {
        hikari.transaction { con ->
            PostgresDAO.insertLogg(
                personident = personIdent,
                mottattDato = mottattDato,
                journalpostId = journalpostId,
                innsendingId = innsendingsId,
                type = type.name,
                con = con
            )
        }
    }

    fun kobleSoeknadEttersending(soknadRef: UUID, ettersendingRef: UUID) {
        hikari.transaction { con ->
            PostgresDAO.insertSoknadEttersending(soknadRef, ettersendingRef, con)
        }
    }

    fun hentSoeknadEttersendelser(soknadRef: UUID): List<UUID> {
        return hikari.transaction { con ->
            PostgresDAO.selectSoknadEttersendelser(soknadRef, con)
        }
    }

    fun hentAlleSøknader(personident: String): List<MineAapSoknad> = hikari.transaction { con ->
        val innsendinger = PostgresDAO.selectInnsendingerByPersonIdent(personident, con)
        val logger = PostgresDAO.selectLogg(personident, InnsendingType.SOKNAD.name, con)
            .map {
                MineAapSoknad(
                    journalpostId = it.journalpost,
                    mottattDato = it.mottattDato,
                    innsendingsId = it.innsendingsId
                )
            }.toList()
        innsendinger + logger
    }


    fun hentAlleInnsendinger(): List<UUID> = hikari.transaction { con ->
        PostgresDAO.selectInnsendinger(con)
    }

    fun hentInnsending(søknadId: UUID): InnsendingMedFiler? = hikari.transaction { con ->
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
        fil: List<Pair<FilMetadata, ByteArray>>,
        referanseId: UUID? = null
    ) {
        hikari.transaction { con ->
            PostgresDAO.insertInnsending(
                innsendingId = innsendingId,
                personident = personIdent,
                mottattDato = mottattDato,
                soknad = innsending.soknad?.toByteArray(),
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

            referanseId?.let {
                PostgresDAO.insertSoknadEttersending(it, innsendingId, con)
            }
        }
    }

    fun hentSøknadMedEttersendelser(innsendingId: UUID): MineAapSoknadMedEttersendinger? = hikari.transaction { con ->
        PostgresDAO.selectSoknadMedEttersendelser(innsendingId, con)
    }
}

fun Map<String, Any>.toByteArray(): ByteArray {
    val mapper = ObjectMapper()
    return mapper.writeValueAsBytes(this)
}
