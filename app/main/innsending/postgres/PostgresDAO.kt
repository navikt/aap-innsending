package innsending.postgres

import innsending.dto.Logg
import innsending.dto.MineAapEttersending
import innsending.dto.MineAapSoknad
import innsending.dto.MineAapSoknadMedEttersendinger
import java.sql.Connection
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDateTime
import java.util.*

object PostgresDAO {
    private const val DELETE_INNSENDING = """DELETE FROM innsending WHERE id = ?"""
    private const val INSERT_FIL = """INSERT INTO fil (id, innsending_id, tittel, data) VALUES (?, ?, ?, ?)"""
    private const val SELECT_INNSENDING_IDS = """SELECT id FROM innsending"""
    private const val SELECT_INNSENDING = """SELECT * FROM innsending WHERE id = ?"""
    private const val SELECT_INNSENDINGER_BY_PERSONIDENT = """SELECT * FROM innsending WHERE personident = ? AND soknad IS NOT NULL"""
    private const val SELECT_FILER = """SELECT * FROM fil WHERE innsending_id = ?"""
    private const val INSERT_INNSENDING = """
        INSERT INTO innsending (id, opprettet, personident, soknad, data) VALUES (?, ?, ?, ?, ?)
    """
    private const val INSERT_LOGG = """
        INSERT INTO logg (personident, mottatt_dato, journalpost_id, type, innsending_id) VALUES (?, ?, ?, ?, ?) 
        ON CONFLICT DO NOTHING
    """
    private const val SELECT_LOGG = """
        SELECT journalpost_id, mottatt_dato, innsending_id FROM logg 
        WHERE personident = ? AND type = ?
        ORDER BY mottatt_dato DESC
    """
    private const val INSERT_SOKNAD_ETTERSENDING = """
        INSERT INTO soknad_ettersending (innsending_soknad_ref, innsending_ettersending_ref) VALUES (?, ?) ON CONFLICT DO NOTHING
    """
    private const val SELECT_SOKNAD_ETTERSENDING = """
        SELECT innsending_ettersending_ref FROM soknad_ettersending WHERE innsending_soknad_ref = ?
    """
    private const val SELECT_LOGG_INNSENDING = """
        SELECT journalpost_id, mottatt_dato, innsending_id FROM logg 
        WHERE innsending_id = ?
        ORDER BY mottatt_dato DESC
    """
    private const val SELECT_ETTERSENDINGER_FOR_INNSENDING = """
        SELECT * FROM logg WHERE innsending_id IN (
            SELECT innsending_ettersending_ref AS innsending_id FROM soknad_ettersending WHERE innsending_soknad_ref = ?
        )
    """

    fun erRefTilknyttetPersonIdent(personident: String, ref: UUID, con: Connection): Boolean {
        val innsending_stmt = con.prepareStatement("SELECT * FROM innsending WHERE personident = ? AND id = ?")
        innsending_stmt.setString(1, personident)
        innsending_stmt.setObject(2, ref)
        val innsending_resultat = innsending_stmt.executeQuery()

        val logg_stmt = con.prepareStatement("SELECT * FROM logg WHERE personident = ? AND innsending_id = ?")
        logg_stmt.setString(1, personident)
        logg_stmt.setObject(2, ref)
        val logg_resultat = logg_stmt.executeQuery()
        return logg_resultat.next() || innsending_resultat.next()

    }

    fun insertSoknadEttersending(soknadRef: UUID, ettersendingRef: UUID, con: Connection) {
        val stmt = con.prepareStatement(INSERT_SOKNAD_ETTERSENDING)
        stmt.setObject(1, soknadRef)
        stmt.setObject(2, ettersendingRef)
        stmt.execute()
    }

    fun selectSoknadEttersendelser(soknadRef: UUID, con: Connection): List<UUID> {
        val stmt = con.prepareStatement(SELECT_SOKNAD_ETTERSENDING)
        stmt.setObject(1, soknadRef)
        val resultat = stmt.executeQuery()
        return resultat.map { row -> row.getUUID("ettersending_id") }.toList()
    }

    fun insertLogg(
        personident: String,
        mottattDato: LocalDateTime,
        journalpostId: String,
        innsendingId: UUID,
        type: String,
        con: Connection
    ) {
        val stmt = con.prepareStatement(INSERT_LOGG)
        stmt.setString(1, personident)
        stmt.setTimestamp(2, Timestamp.valueOf(mottattDato))
        stmt.setString(3, journalpostId)
        stmt.setString(4, type)
        stmt.setObject(5, innsendingId)
        stmt.execute()
    }

    fun selectLogg(personident: String, type: String, con: Connection): List<Logg> {
        val stmt = con.prepareStatement(SELECT_LOGG)
        stmt.setString(1, personident)
        stmt.setString(2, type)
        val resultat = stmt.executeQuery()
        return resultat.map { row ->
            Logg(
                journalpost = row.getString("journalpost_id"),
                mottattDato = row.getTimestamp("mottatt_dato").toLocalDateTime(),
                innsendingsId = row.getObject("innsending_id") as UUID
            )
        }
    }

    fun insertInnsending(
        innsendingId: UUID,
        personident: String,
        mottattDato: LocalDateTime,
        soknad: ByteArray?,
        data: ByteArray?,
        con: Connection
    ) {
        val stmt = con.prepareStatement(INSERT_INNSENDING)
        stmt.setObject(1, innsendingId)
        stmt.setObject(2, Timestamp.valueOf(mottattDato))
        stmt.setObject(3, personident)
        stmt.setNullableObject(4, soknad, Types.BINARY)
        stmt.setNullableObject(5, data, Types.BINARY)
        stmt.execute()
    }

    fun deleteInnsending(id: UUID, con: Connection) {
        val stmt = con.prepareStatement(DELETE_INNSENDING)
        stmt.setObject(1, id)
        stmt.execute()
        println("slettet")
    }

    fun insertFil(innsendingId: UUID, filId: UUID, fil: ByteArray, tittel: String, con: Connection) {
        val stmt = con.prepareStatement(INSERT_FIL)
        stmt.setObject(1, filId)
        stmt.setObject(2, innsendingId)
        stmt.setObject(3, tittel)
        stmt.setObject(4, fil)
        stmt.execute()
    }

    fun selectInnsendinger(con: Connection): List<UUID> {
        val stmt = con.prepareStatement(SELECT_INNSENDING_IDS)
        val resultat = stmt.executeQuery()
        return resultat.map { row -> row.getUUID("id") }
    }

    fun selectInnsendingerByPersonIdent(personident: String, con: Connection): List<MineAapSoknad> {
        val stmt = con.prepareStatement(SELECT_INNSENDINGER_BY_PERSONIDENT)
        stmt.setString(1, personident)
        val resultat = stmt.executeQuery()
        return resultat.map { row ->
            MineAapSoknad(
                mottattDato = row.getTimestamp("opprettet").toLocalDateTime(),
                journalpostId = null,
                innsendingsId = row.getUUID("id")
                )
        }.toList()
    }

    fun selectInnsendingMedFiler(innsendingId: UUID, con: Connection): InnsendingMedFiler? {
        val innsending = con.prepareStatement(SELECT_INNSENDING).use { stmt ->
            stmt.setObject(1, innsendingId)
            val resultSet = stmt.executeQuery()

            resultSet.map { row ->
                InnsendingDb(
                    id = row.getUUID("id"),
                    opprettet = row.getTimestamp("opprettet").toLocalDateTime(),
                    personident = row.getString("personident"),
                    søknad = row.getBytes("soknad"),
                    data = row.getBytes("data")
                )
            }.singleOrNull()
        }

        if (innsending == null) {
            return null
        }

        val filer = con.prepareStatement(SELECT_FILER).use { preparedStatement ->
            preparedStatement.setObject(1, innsendingId)
            val resultSet = preparedStatement.executeQuery()

            resultSet.map { row ->
                InnsendingMedFiler.Fil(
                    id = row.getUUID("id"),
                    tittel = row.getString("tittel"),
                    data = row.getBytes("data")
                )
            }
        }

        return InnsendingMedFiler(
            id = innsending.id,
            opprettet = innsending.opprettet,
            personident = innsending.personident,
            søknad = innsending.søknad,
            data = innsending.data,
            fil = filer
        )
    }

    fun selectSoknadMedEttersendelser(innsendingId: UUID, con: Connection): MineAapSoknadMedEttersendinger? {
        val ettersendinger = con.prepareStatement(SELECT_ETTERSENDINGER_FOR_INNSENDING).use { stmt ->
            stmt.setObject(1, innsendingId)
            val resultSet = stmt.executeQuery()

            resultSet.map { row ->
                MineAapEttersending(
                    journalpostId = row.getString("journalpost_id"),
                    mottattDato = row.getTimestamp("mottatt_dato").toLocalDateTime(),
                    innsendingsId = row.getObject("innsending_id") as UUID
                )
            }
        }

        val soknadMedEttersendinger = con.prepareStatement(SELECT_LOGG_INNSENDING).use { stmt ->
            stmt.setObject(1, innsendingId)

            val resultSet = stmt.executeQuery()

            resultSet.map { row ->
                MineAapSoknadMedEttersendinger(
                    journalpostId = row.getString("journalpost_id"),
                    mottattDato = row.getTimestamp("mottatt_dato").toLocalDateTime(),
                    innsendingsId = row.getObject("innsending_id") as UUID,
                    ettersendinger = ettersendinger
                )
            }.singleOrNull()
        }

        if (soknadMedEttersendinger == null) {
            val ikkeArkivertSøknadMedEttersendinger = con.prepareStatement(SELECT_INNSENDING).use { stmt ->
                stmt.setObject(1, innsendingId)

                val resultSet = stmt.executeQuery()

                resultSet.map { row ->
                    MineAapSoknadMedEttersendinger(
                        journalpostId = null,
                        mottattDato = row.getTimestamp("opprettet").toLocalDateTime(),
                        innsendingsId = row.getObject("id") as UUID,
                        ettersendinger = ettersendinger
                    )
                }.singleOrNull()
            }
            return ikkeArkivertSøknadMedEttersendinger
        } else {
            return soknadMedEttersendinger
        }
    }
}
