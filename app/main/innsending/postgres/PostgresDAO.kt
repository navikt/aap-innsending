package innsending.postgres

import innsending.routes.Logg
import java.sql.Connection
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDateTime
import java.util.*

object PostgresDAO {
    private const val DELETE_INNSENDING = """DELETE FROM innsending WHERE id = ?"""
    private const val INSERT_FIL = """INSERT INTO fil (id, innsending_id, tittel, data) VALUES (?, ?, ?, ?)"""
    private const val SELECT_INNSENDING_IDS = """SELECT id FROM innsending"""
    private const val SELECT_INNSENDINGER = """SELECT * FROM innsending WHERE id = ?"""
    private const val SELECT_FILER = """SELECT * FROM fil WHERE innsending_id = ?"""
    private const val INSERT_INNSENDING = """
        INSERT INTO innsending (id, opprettet, personident, data) VALUES (?, ?, ?, ?)
    """
    private const val INSERT_LOGG = """
        INSERT INTO logg (personident, mottatt_dato, journalpost_id, type) VALUES (?, ?, ?, ?) 
        ON CONFLICT DO NOTHING
    """
    private const val SELECT_LOGG = """
        SELECT journalpost_id, mottatt_dato FROM logg 
        WHERE personident = ? AND type = ?
        ORDER BY mottatt_dato DESC
    """

    fun insertLogg(personident: String, mottattDato: LocalDateTime, journalpostId: String, type: String, con: Connection) {
        val stmt = con.prepareStatement(INSERT_LOGG)
        stmt.setString(1, personident)
        stmt.setTimestamp(2, Timestamp.valueOf(mottattDato))
        stmt.setString(3, journalpostId)
        stmt.setString(4, type)
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
                mottattDato = row.getTimestamp("mottatt_dato").toLocalDateTime()
            )
        }
    }

    fun insertInnsending(innsendingId: UUID, personident: String, mottattDato: LocalDateTime, data: ByteArray?, con: Connection) {
        val stmt = con.prepareStatement(INSERT_INNSENDING)
        stmt.setObject(1, innsendingId)
        stmt.setObject(2, Timestamp.valueOf(mottattDato))
        stmt.setObject(3, personident)
        stmt.setNullableObject(4, data, Types.BINARY)
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

    fun selectInnsendingMedFiler(innsendingId: UUID, con: Connection): InnsendingMedFiler {
        val innsending = con.prepareStatement(SELECT_INNSENDINGER).use { stmt ->
            stmt.setObject(1, innsendingId)
            val resultSet = stmt.executeQuery()

            resultSet.map { row ->
                InnsendingDb(
                    id = row.getUUID("id"),
                    opprettet = row.getTimestamp("opprettet").toLocalDateTime(),
                    personident = row.getString("personident"),
                    data = row.getBytes("data")
                )
            }.single()
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
            data = innsending.data,
            fil = filer
        )
    }
}
