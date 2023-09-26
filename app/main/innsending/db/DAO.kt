package innsending.db

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*

private class ResultSetSequence(private val resultSet: ResultSet) : Sequence<ResultSet> {
    override fun iterator(): Iterator<ResultSet> {
        return ResultSetIterator()
    }

    private inner class ResultSetIterator : Iterator<ResultSet> {
        override fun hasNext(): Boolean {
            return resultSet.next()
        }

        override fun next(): ResultSet {
            return resultSet
        }
    }
}

fun <T : Any> ResultSet.map(block: (rs: ResultSet) -> T): Sequence<T> {
    return ResultSetSequence(this).map(block)
}

fun <T> Connection.transaction(block: (connection: Connection) -> T): T {
    return this.use { connection ->
        try {
            connection.autoCommit = false
            val result = block(this)
            connection.commit()
            result
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }
}

fun ResultSet.getUUID(columnLabel: String): UUID = UUID.fromString(this.getString(columnLabel))

fun PreparedStatement.setNullableObject(index: Int, obj: Any?, type: Int) {
    if (obj != null) {
        this.setObject(index, obj)
    } else {
        this.setNull(index, type)
    }
}
