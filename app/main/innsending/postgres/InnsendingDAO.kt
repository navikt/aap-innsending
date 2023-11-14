package innsending.postgres

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

    fun insertInnsending(søknadId: UUID, personident: String, søknad: ByteArray) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(insertInnsendingSql).use { preparedStatement ->
                preparedStatement.setObject(1, søknadId)
                preparedStatement.setObject(2, Timestamp.valueOf(LocalDateTime.now()))
                preparedStatement.setObject(3, personident)
                preparedStatement.setObject(4, søknad)

                preparedStatement.execute()
            }
        }
    }

    fun deleteInnsending(id: UUID) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(deleteInnsendingSql).use { preparedStatement ->
                preparedStatement.setObject(1, id)

                preparedStatement.execute()
            }
        }
    }
}
