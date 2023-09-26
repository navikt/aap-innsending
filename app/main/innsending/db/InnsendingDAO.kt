package innsending.db

import innsending.domene.Innsending
import innsending.domene.NyInnsendingRequest
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class InnsendingDAO(private val dataSource: DataSource) {
    private val selectInnsendingSql = """
       SELECT * FROM innsending WHERE innsendingsreferanse = ? 
    """

    private val insertInnsendingSql = """
       INSERT INTO innsending (innsendingsreferanse, eksternreferanse, opprettet, sist_oppdatert, brukerid, brevkode) 
       VALUES (?, ?, ?, ?, ?, ?) 
    """

    private val fullførInnsendingSql = """
       UPDATE innsending SET fullfoert = ?, sendt_til_arkivering = ? WHERE innsendingsreferanse = ? 
    """

    private val updateInnsendingSql = """
       UPDATE innsending SET data = ?, sist_oppdatert = ? WHERE innsendingsreferanse = ? 
    """


    private val deleteInnsendingSql = """
       DELETE FROM innsending WHERE innsendingsreferanse = ? 
    """

    private val selectInnsendingByBrukerIdSql = """
       SELECT * FROM innsending WHERE brukerid = ? AND fullfoert IS NULL
    """

    fun getInnsending(innsendingsreferanse: UUID): Innsending {
        dataSource.connection.use { connection ->
            connection.prepareStatement(selectInnsendingSql).use { preparedStatement ->
                preparedStatement.setObject(1, innsendingsreferanse)

                val resultSet = preparedStatement.executeQuery()
                return lagInnsending(resultSet)
            }
        }
    }

    fun getInnsendingByBrukerId(brukerId: String): Innsending{
        dataSource.connection.use { connection ->
            connection.prepareStatement(selectInnsendingByBrukerIdSql).use { preparedStatement ->
                preparedStatement.setObject(1, brukerId)

                val resultSet = preparedStatement.executeQuery()
                return lagInnsending(resultSet)
            }
        }
    }

    fun insertInnsending(innsendingsreferanse: UUID, eksternreferanse: UUID?, brukerId: String, brevkode:String?) {
        dataSource.connection.use {connection ->
            connection.prepareStatement(insertInnsendingSql).use {preparedStatement ->
                preparedStatement.setObject(1, innsendingsreferanse)
                preparedStatement.setNullableObject(2, eksternreferanse, Types.OTHER)
                preparedStatement.setObject(3, Timestamp.valueOf(LocalDateTime.now()))
                preparedStatement.setObject(4, Timestamp.valueOf(LocalDateTime.now()))
                preparedStatement.setString(5, brukerId)
                preparedStatement.setString(6, brevkode)

                preparedStatement.execute()
            }
        }
    }

    fun updateInnsending(innsendingsreferanse: UUID,innsending: NyInnsendingRequest) {
        dataSource.connection.use {connection ->
            connection.prepareStatement(updateInnsendingSql).use {preparedStatement ->
                preparedStatement.setObject(1, innsending.data)
                preparedStatement.setObject(2, Timestamp.valueOf(LocalDateTime.now()))
                preparedStatement.setObject(3, innsendingsreferanse)

                preparedStatement.execute()
            }
        }
    }

    fun fullførInnsending(innsendingsreferanse: UUID) {
        dataSource.connection.use {connection ->
            connection.prepareStatement(updateInnsendingSql).use {preparedStatement ->
                preparedStatement.setObject(1, Timestamp.valueOf(LocalDateTime.now()))
                preparedStatement.setObject(2, true)
                preparedStatement.setObject(3, innsendingsreferanse)

                preparedStatement.execute()
            }
        }
    }

    fun deleteInnsending(innsendingsreferanse: UUID) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(deleteInnsendingSql).use {  preparedStatement ->
                preparedStatement.setObject(1, innsendingsreferanse)

                preparedStatement.execute()
            }
        }
    }

    private fun lagInnsending(resultSet: ResultSet) = resultSet.map { row ->
        Innsending(
            row.getUUID("innsendingsreferanse"),
            row.getString("brukerid"),
            row.getString("type"),
            row.getString("data")
        )
    }.single()

}