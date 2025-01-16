package innsending.db

class InMemoryFilData(private val data: ByteArray) : FilData {
    override fun hent(): ByteArray? {
        return data
    }
}
