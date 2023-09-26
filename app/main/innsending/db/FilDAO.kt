package innsending.db

import innsending.domene.Fil
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

class FilDAO(private val dataSource: DataSource) {

    private val insertFilSql = """
       INSERT INTO fil VALUES (?, ?, ?) 
    """

    private val updateFilSql = """
       UPDATE fil SET tittel = ? WHERE filreferanse = ? 
    """

    private val deleteFilSql = """
       DELETE FROM fil WHERE filreferanse = ? 
    """

    private val selectFilerForInnendingsreferanseSql = """
        SELECT filreferanse, tittel FROM fil WHERE innsendingsreferanse = ? 
    """


    fun insertFil(filreferanse: UUID, innsendingsreferanse: UUID, tittel: String?) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(insertFilSql).use {  preparedStatement ->
                preparedStatement.setObject(1, filreferanse)
                preparedStatement.setObject(2, innsendingsreferanse)
                if (tittel != null) {
                    preparedStatement.setString(3, tittel)
                } else {
                    preparedStatement.setNull(3, Types.VARCHAR)
                }

                preparedStatement.execute()
            }
        }
    }

    fun updateFil(filreferanse: UUID, tittel: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(updateFilSql).use {  preparedStatement ->
                preparedStatement.setString(1, tittel)
                preparedStatement.setObject(2, filreferanse)

                preparedStatement.execute()
            }
        }
    }

    fun deleteFil(filreferanse: UUID) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(deleteFilSql).use {  preparedStatement ->
                preparedStatement.setObject(1, filreferanse)

                preparedStatement.execute()
            }
        }
    }

    fun selectFilerForInnendingsreferanse(innsendingsreferanse: UUID): List<Fil> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(selectFilerForInnendingsreferanseSql).use { preparedStatement ->
                preparedStatement.setObject(1, innsendingsreferanse)

                val resultSet = preparedStatement.executeQuery()

                resultSet.map { row ->
                    Fil(
                        filreferanse = row.getUUID("filreferanse"),
                        innsendingsreferanse = innsendingsreferanse,
                        tittel = row.getString("tittel")
                    )
                }.toList()
            }
        }

}