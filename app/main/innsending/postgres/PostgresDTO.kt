package innsending.postgres

import java.time.LocalDateTime
import java.util.*

data class InnsendingDb(
    val id: UUID,
    val opprettet: LocalDateTime,
    val personident: String,
    val data: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InnsendingDb

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

data class InnsendingMedFiler(
    val id: UUID,
    val opprettet: LocalDateTime,
    val personident: String,
    val data: ByteArray?,
    val fil: List<Fil>
) {
    data class Fil(
        val id: UUID,
        val tittel: String,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Fil

            return id == other.id
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InnsendingMedFiler

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
