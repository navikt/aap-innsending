package innsending.dto

import java.net.URL
import java.time.LocalDateTime

data class MellomlagringRespons(
    val filId: String,
)

data class SøknadFinnesRespons(
    val tittel: String,
    val link: URL,
    val sistEndret: LocalDateTime,
)
