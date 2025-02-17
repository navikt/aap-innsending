package innsending.dto

import innsending.postgres.InnsendingType
import java.time.LocalDateTime
import java.util.UUID

data class Logg(
    val journalpost: String,
    val mottattDato: LocalDateTime,
    val innsendingsId: UUID
)
data class ValiderFiler(
    val filer: List<FilMetadata>,
)
data class Innsending(
    /**
     * Kvittering er JSON for å produsere kvitterings-PDF til bruker
     */
    val kvittering: Map<String, Any>? = null,
    /*
     * soknad er søknad i JSON for å lagre orginalsøknad i joark
     */
    val soknad: Map<String, Any>? = null,
    /*
     * Filer er vedlegg til søknad ELLER Generell ettersendelse
     */
    val filer: List<FilMetadata>,
) {
    var type: InnsendingType
    init {
        if ((soknad == null && kvittering != null) || (soknad != null && kvittering == null)) {
            throw IllegalArgumentException("Kvittering og søknad må være satt samtidig")
        }
        type = if (soknad == null) {
            InnsendingType.ETTERSENDING
        } else {
            InnsendingType.SOKNAD
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Innsending) return false

        if (kvittering != other.kvittering) return false
        if (filer != other.filer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kvittering?.hashCode() ?: 0
        result = 31 * result + filer.hashCode()
        return result
    }
}

data class FilMetadata(
    val id: String,
    val tittel: String,
)

data class MineAapSoknadMedEttersendingNy(
    val mottattDato: LocalDateTime,
    val journalpostId: String?,
    val innsendingsId: UUID,
    val ettersendinger: List<MineAapEttersendingNy>
)

data class MineAapSoknadMedEttersendinger(
    val mottattDato: LocalDateTime,
    val journalpostId: String?,
    val innsendingsId: UUID,
    val ettersendinger: List<MineAapEttersending>
)

data class MineAapEttersendingNy(
    val mottattDato: LocalDateTime,
    val journalpostId: String?,
    val innsendingsId: UUID
)

data class MineAapEttersending(
    val mottattDato: LocalDateTime,
    val journalpostId: String?,
    val innsendingsId: UUID
)
