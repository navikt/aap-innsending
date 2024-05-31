package innsending.dto

data class MellomlagringDto(
    val soknad: ByteArray,
    val vedlegg: List<FilMetadata>?
)

data class MellomlagringRespons(
    val filId: String,
)

data class ErrorRespons(
    val feilmelding: String,
)
