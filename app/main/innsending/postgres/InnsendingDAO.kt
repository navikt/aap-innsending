package innsending.postgres

import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class InnsendingDAO(private val dataSource: DataSource) {
    private val insertInnsendingSql = """
       INSERT INTO innsending (id, opprettet, personident, data) 
       VALUES (?, ?, ?, ?) 
    """

    private val deleteInnsendingSql = """
       DELETE FROM innsending WHERE id = ? 
    """

    private val insertVedleggSql = """
       INSERT INTO fil (id, innsending_id, tittel, data) 
       VALUES (?, ?, ?, ?) 
    """

    private val selectAlleInnsendingerIdSql = """
        SELECT id FROM innsending
    """

    private val selectInnsendingSql = """
        SELECT * FROM innsending WHERE id = ?
    """

    private val selectFiler = """
       SELECT * from fil WHERE innsending_id = ? 
    """

    fun insertInnsending(søknadId: UUID, personident: String, søknad: ByteArray) {
        dataSource.connection.use { connection ->
            insertInnsendingStatement(søknadId, personident, søknad, connection)
        }
    }

    fun insertInnsendingStatement(søknadId: UUID, personident: String, søknad: ByteArray, connection: Connection) {
        val preparedStatement = connection.prepareStatement(insertInnsendingSql)
        preparedStatement.setObject(1, søknadId)
        preparedStatement.setObject(2, Timestamp.valueOf(LocalDateTime.now()))
        preparedStatement.setObject(3, personident)
        preparedStatement.setObject(4, søknad)

        preparedStatement.execute()
    }

    fun insertVedleggStatement(
        søknadId: UUID,
        vedleggId: UUID,
        vedlegg: ByteArray,
        tittel: String,
        connection: Connection
    ) {
        val preparedStatement = connection.prepareStatement(insertVedleggSql)
        preparedStatement.setObject(1, vedleggId)
        preparedStatement.setObject(2, søknadId)
        preparedStatement.setObject(3, tittel)
        preparedStatement.setObject(4, vedlegg)

        preparedStatement.execute()
    }

    fun deleteInnsending(id: UUID) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(deleteInnsendingSql).use { preparedStatement ->
                preparedStatement.setObject(1, id)

                preparedStatement.execute()
            }
        }
    }

    fun insertVedlegg(søknadId: UUID, vedleggId: UUID, vedlegg: ByteArray, tittel: String) {
        dataSource.connection.use { connection ->
            insertVedleggStatement(søknadId, vedleggId, vedlegg, tittel, connection)
        }
    }

    fun selectInnsendinger(): List<UUID> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(selectAlleInnsendingerIdSql).use { preparedStatement ->
                val resultat = preparedStatement.executeQuery()

                resultat.map { row ->
                    row.getUUID("id")
                }
            }
        }

    fun selectInnsendingMedVedlegg(søknadId: UUID): InnsendingMedFiler {
        dataSource.connection.use { connection ->
            val innsending = connection.prepareStatement(selectInnsendingSql).use { preparedStatement ->
                preparedStatement.setObject(1, søknadId)

                val resultSet = preparedStatement.executeQuery()

                resultSet.map { row ->
                    InnsendingDb(
                        id = row.getUUID("id"),
                        opprettet = row.getTimestamp("opprettet").toLocalDateTime(),
                        personident = row.getString("personident"),
                        data = row.getBytes("data")
                    )
                }.single()
            }

            val vedleggListe = connection.prepareStatement(selectFiler).use { preparedStatement ->
                preparedStatement.setObject(1, søknadId)
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
}
