package innsending.db

import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class SoknadDAO (private val dataSource: DataSource){
    private val insertSoknadSql = """
       INSERT INTO soknad VALUES (?, ?, ?, ?, ?, ?, ?) 
    """

    private val fullførSoknadSql = """
       UPDATE soknad SET innsendt = ?, data=? WHERE innsendingsreferanse = ? 
    """

    private val updateSoknadSql = """
       UPDATE soknad SET data=? WHERE soknad_id = ? 
    """

    private val deleteSoknadSql = """
       DELETE FROM soknad WHERE soknad_id = ? 
    """

    fun insertSoknad(søknadId:UUID, innsendingsreferanse: UUID, brukerId: String, version:Int, data: String){
        dataSource.connection.use {connection ->
            connection.prepareStatement(insertSoknadSql).use {preparedStatement ->
                preparedStatement.setObject(1,søknadId)
                preparedStatement.setObject(2,innsendingsreferanse)
                preparedStatement.setString(3, brukerId)
                preparedStatement.setObject(4, Timestamp.valueOf(LocalDateTime.now())) //TODO: skal denne settes her eller være satt når den kommer inn?
                preparedStatement.setNull(5, Types.TIMESTAMP) //TODO: kan denne være satt når den kommer første gang?
                preparedStatement.setInt(6, version)
                preparedStatement.setString(7, data)

                preparedStatement.execute()
            }
        }
    }

    fun fullforSoknad(innsendingsreferanse: UUID){
        dataSource.connection.use {connection ->
            connection.prepareStatement(fullførSoknadSql).use {preparedStatement ->
                preparedStatement.setObject(1, Timestamp.valueOf(LocalDateTime.now()))
                preparedStatement.setObject(2, innsendingsreferanse)

                preparedStatement.execute()
            }
        }
    }
    fun updateSoknad(søknadId: UUID, data: String){
        dataSource.connection.use {connection ->
            connection.prepareStatement(updateSoknadSql).use {preparedStatement ->
                preparedStatement.setObject(1, data)
                preparedStatement.setObject(2, søknadId)

                preparedStatement.execute()
            }
        }
    }

    fun deleteSoknad(søknadId: UUID) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(deleteSoknadSql).use {  preparedStatement ->
                preparedStatement.setObject(1, søknadId)

                preparedStatement.execute()
            }
        }
    }
}