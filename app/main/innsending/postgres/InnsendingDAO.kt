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


    fun insertInnsending(søknadId: UUID, personident: String, søknad: ByteArray) {
        dataSource.connection.use { connection ->
            insertVedleggStatement(søknadId.toString(), søknadId.toString(), søknad, "søknad", connection)
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
        søknadId: String,
        vedleggId: String,
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

    fun insertVedlegg(søknadId: String, vedleggId: String, vedlegg: ByteArray, tittel: String) {
        dataSource.connection.use { connection ->
            insertVedleggStatement(søknadId, vedleggId, vedlegg, tittel, connection)
        }

    }
}
