package innsending.db

import com.fasterxml.jackson.core.type.TypeReference
import innsending.postgres.InnsendingType
import no.nav.aap.komponenter.json.DefaultJsonMapper
import java.time.LocalDateTime
import java.util.UUID

data class InnsendingNy(
    val id: Long?,
    val opprettet: LocalDateTime,
    val personident: String,
    val soknad: ByteArray?,
    val data: ByteArray?,
    val eksternRef: UUID,
    val forrigeInnsendingId: Long?,
    val type: InnsendingType,
    val journalpost_Id: String?,
    val filer: List<FilNy>
) {
    fun kvitteringToMap(): Map<String, Any> {
        requireNotNull(data) { "data is null" }
        val mapper = DefaultJsonMapper.objectMapper()
        val tr = object : TypeReference<Map<String, Any>>() {}
        return mapper.readValue(data, tr)
    }
}

data class FilNy(
    val tittel: String,
    val data: FilData?
)
