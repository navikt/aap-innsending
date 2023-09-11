package innsending.domene

import java.util.*

data class Ettersendelse(
    val søknadId: UUID?,
    val vedlegg: List<Vedlegg>,
)

data class Vedlegg (
    val vedleggType:String,
    val filreferanser: List<UUID>,
)
