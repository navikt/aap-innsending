package innsending.postgres

import java.sql.Connection
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDateTime
import java.util.*

object PostgresDAO {
    private const val DELETE_INNSENDING = """DELETE FROM innsending WHERE id = ?"""
    private const val INSERT_VEDLEGG = """INSERT INTO fil (id, innsending_id, tittel, data) VALUES (?, ?, ?, ?)"""
    private const val SELECT_INNSENDING_IDS = """SELECT id FROM innsending"""
    private const val SELECT_INNSENDINGER = """SELECT * FROM innsending WHERE id = ?"""
    private const val SELECT_FILER = """SELECT * FROM fil WHERE innsending_id = ?"""
    private const val INSERT_INNSENDING = """
        INSERT INTO innsending (id, opprettet, personident, data) VALUES (?, ?, ?, ?)
    """

    fun insertInnsending(innsendingId: UUID, personident: String, data: ByteArray?, con: Connection) {
        val stmt = con.prepareStatement(INSERT_INNSENDING)
        stmt.setObject(1, innsendingId)
        stmt.setObject(2, Timestamp.valueOf(LocalDateTime.now()))
        stmt.setObject(3, personident)
        stmt.setNullableObject(4, data, Types.BINARY)
        stmt.execute()
    }

    fun deleteInnsending(id: UUID, con: Connection) {
        val stmt = con.prepareStatement(DELETE_INNSENDING)
        stmt.setObject(1, id)
        stmt.execute()
    }

    fun insertVedlegg(innsendingId: UUID, vedleggId: UUID, vedlegg: ByteArray, tittel: String, con: Connection) {
        val stmt = con.prepareStatement(INSERT_VEDLEGG)
        stmt.setObject(1, vedleggId)
        stmt.setObject(2, innsendingId)
        stmt.setObject(3, tittel)
        stmt.setObject(4, vedlegg)
        stmt.execute()
    }

    fun selectInnsendinger(con: Connection): List<UUID> {
        val stmt = con.prepareStatement(SELECT_INNSENDING_IDS)
        val resultat = stmt.executeQuery()
        return resultat.map { row -> row.getUUID("id") }
    }

    fun selectInnsendingMedVedlegg(innsendingId: UUID, con: Connection): InnsendingMedFiler {
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

        val vedleggListe = con.prepareStatement(SELECT_FILER).use { preparedStatement ->
            preparedStatement.setObject(1, innsendingId)
            val resultSet = preparedStatement.executeQuery()

            resultSet.map { row ->
                InnsendingMedFiler.Vedlegg(
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
            vedlegg = vedleggListe
        )
    }
}
