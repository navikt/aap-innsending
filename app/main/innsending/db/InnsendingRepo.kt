package innsending.db

import innsending.dto.MineAapEttersendingNy
import innsending.dto.MineAapSoknadMedEttersendingNy
import innsending.postgres.InnsendingType
import no.nav.aap.komponenter.dbconnect.DBConnection
import java.util.UUID

class InnsendingRepo(private val connection: DBConnection) {
    private val hentInnsending = """
        SELECT * FROM innsending_ny WHERE id = ?
    """

    private val hentFiler = """
        SELECT * FROM fil_ny WHERE innsending_id = ?
    """

    private val lagreInnsending = """
        INSERT INTO innsending_ny (opprettet, personident, soknad, data, ekstern_referanse, type, journalpost_id, forrige_innsending_id) VALUES (?, ?, ?, ?, ?, ?, ? ,?)
    """

    private val lagreFil = """
        INSERT INTO fil_ny (tittel, data, innsending_id) VALUES (?, ?, ?)
    """

    private val hentAlleSøknader = """
        SELECT id, opprettet, journalpost_id FROM innsending_ny WHERE personident = ? AND type = ?
    """

    private val hentAlleEttersendinger = """
        SELECT * FROM innsending_ny WHERE forrige_innsending_id = ?
    """

    fun hentIdFraEksternRef(eksternRef: UUID): Long? {
        return connection.queryFirstOrNull("SELECT id FROM innsending_ny WHERE ekstern_referanse = ?"){
            setParams {
                setUUID(1, eksternRef)
            }
            setRowMapper { row ->
                row.getLong("id")
            }
        }
    }

    fun erRefTilknyttetPersonIdent(personident: String, ref: Long): Boolean {
        return connection.queryFirst("SELECT count(*)>0 as a FROM innsending_ny WHERE personident = ? AND id = ?"){
            setParams {
                setString(1, personident)
                setLong(2, ref)
            }
            setRowMapper { row ->
                row.getBoolean("a")
            }
        }
    }


    fun lagre(innsending: InnsendingNy): Long {
        val innsendingId = connection.executeReturnKey(lagreInnsending) {
            setParams {
                setLocalDateTime(1, innsending.opprettet)
                setString(2, innsending.personident)
                setBytes(3, innsending.soknad)
                setBytes(4, innsending.data)
                setUUID(5, innsending.eksternRef)
                setEnumName(6, innsending.type)
                setString(7, innsending.journalpost_Id)
                setLong(8, innsending.forrigeInnsendingId)
            }
        }
        connection.executeBatch(lagreFil, innsending.filer) {
            setParams { fil ->
                setString(1, fil.tittel)
                setBytes(2, fil.data)
                setLong(3, innsendingId)
            }
        }
        return innsendingId
    }


    fun hent(id: Long): InnsendingNy {
        return connection.queryFirst(hentInnsending) {
            setParams {
                setLong(1, id)
            }
            setRowMapper { row ->
                InnsendingNy(
                    id = row.getLong("id"),
                    opprettet = row.getLocalDateTime("opprettet"),
                    personident = row.getString("personident"),
                    soknad = row.getBytesOrNull("soknad"),
                    data = row.getBytesOrNull("data"),
                    forrigeInnsendingId = row.getLongOrNull("forrige_innsending_id"),
                    eksternRef = row.getUUID("ekstern_referanse"),
                    type = row.getEnum("type"),
                    journalpost_Id = row.getStringOrNull("journalpost_id"),
                    filer = hentFiler(id)
                )
            }
        }
    }

    fun hentFiler(innsendingId: Long): List<FilNy> {
        return connection.queryList(hentFiler) {
            setParams {
                setLong(1, innsendingId)
            }
            setRowMapper { row ->
                FilNy(
                    tittel = row.getString("tittel"),
                    data = row.getBytesOrNull("data")
                )
            }
        }
    }

    fun hentAlleSøknader(personident: String): List<MineAapSoknadMedEttersendingNy> {
        return connection.queryList(hentAlleSøknader) {
            setParams {
                setString(1, personident)
                setEnumName(2, InnsendingType.SOKNAD)
            }
            setRowMapper { row ->
                MineAapSoknadMedEttersendingNy(
                    innsendingsId = row.getLong("id"),
                    mottattDato = row.getLocalDateTime("opprettet"),
                    journalpostId = row.getStringOrNull("journalpost_id"),
                    ettersendinger = hentAlleEttersendinger(row.getLong("id"))
                )
            }
        }
    }

    fun hentAlleEttersendinger(soknadId: Long): List<MineAapEttersendingNy> {
        return connection.queryList(hentAlleEttersendinger) {
            setParams {
                setLong(1, soknadId)
            }
            setRowMapper { row ->
                MineAapEttersendingNy(
                    innsendingsId = row.getLong("id"),
                    mottattDato = row.getLocalDateTime("opprettet"),
                    journalpostId = row.getStringOrNull("journalpost_id")
                )
            }
        }
    }

    fun hentInnsendingIdForReferanse(eksternRef: UUID): Long {
        return connection.queryFirst("SELECT id FROM innsending_ny WHERE ekstern_referanse = ?"){
            setParams {
                setUUID(1, eksternRef)
            }
            setRowMapper { row ->
                row.getLong("id")
            }
        }
    }

    fun markerFerdig(innsendingId: Long, journalpostId: String) {
        connection.execute("UPDATE innsending_ny SET soknad = NULL, data=NULL, journalpost_id=? WHERE id = ?"){
            setParams {
                setString(1, journalpostId)
                setLong(2, innsendingId)
            }
        }
        connection.execute("UPDATE fil_ny SET data=NULL WHERE innsending_id = ?"){
            setParams {
                setLong(1, innsendingId)
            }
        }
    }
}
