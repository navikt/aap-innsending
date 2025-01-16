package innsending.db

import no.nav.aap.komponenter.dbconnect.DBConnection

class LazyFilData(private val filId: Long, private val connection: DBConnection) : FilData {
    override fun hent(): ByteArray? {
        return connection.queryFirstOrNull("SELECT data FROM fil_ny WHERE id = ?") {
            setParams {
                setLong(1, filId)
            }
            setRowMapper {
                it.getBytesOrNull("data")
            }
        }
    }
}
