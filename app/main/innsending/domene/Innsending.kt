package innsending.domene

import java.util.*

data class Innsending(
    val innsendingsreferanse: UUID,
    val brukerId: String,
    val innsendingsType: String?,
    val data: String,
)

data class NyInnsendingRequest(
    val eksternreferanse: UUID? = null,
    val brukerId: String,
    val innsendingsType: String?,
    val data: String
)