package innsending.postgres

import com.fasterxml.jackson.databind.ObjectMapper

enum class InnsendingType { SOKNAD, ETTERSENDING }

fun Map<String, Any>.toByteArray(): ByteArray {
    val mapper = ObjectMapper()
    return mapper.writeValueAsBytes(this)
}
