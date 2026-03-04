package innsending.db

import innsending.dto.MineAapEttersendingNy
import innsending.dto.MineAapSoknadMedEttersendingNy
import innsending.postgres.InnsendingType
import innsending.postgres.PostgresTestBase
import no.nav.aap.behandlingsflyt.kontrakt.hendelse.dokumenter.SøknadV0
import no.nav.aap.komponenter.dbconnect.transaction
import no.nav.aap.komponenter.json.DefaultJsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class InnsendingRepoTest : PostgresTestBase() {

    @BeforeEach
    fun ryddTabeller() {
        dataSource.transaction { con ->
            con.execute("TRUNCATE innsending_ny CASCADE")
            con.execute("TRUNCATE fil_ny CASCADE")
        }
    }

    private val enSøknad = SøknadV0(
        student = null,
        yrkesskade = "Nei",
        oppgitteBarn = null,
    )

    private fun lagSøknad(
        personident: String = "12345678910",
        eksternRef: UUID = UUID.randomUUID(),
        filer: List<FilNy> = emptyList(),
        journalpostId: String? = null,
    ): Long {
        return dataSource.transaction { con ->
            InnsendingRepo(con).lagre(
                InnsendingNy(
                    id = null,
                    opprettet = LocalDateTime.now(),
                    personident = personident,
                    soknad = DefaultJsonMapper.toJson(enSøknad).toByteArray(),
                    data = "data".toByteArray(),
                    eksternRef = eksternRef,
                    forrigeInnsendingId = null,
                    type = InnsendingType.SOKNAD,
                    journalpost_Id = journalpostId,
                    filer = filer,
                )
            )
        }
    }

    @Test
    fun `lagre og hent søknad`() {
        val eksternRef = UUID.randomUUID()
        val id = lagSøknad(eksternRef = eksternRef)

        val hentet = dataSource.transaction { con ->
            InnsendingRepo(con).hent(id)
        }

        assertThat(hentet).usingRecursiveComparison().ignoringFields("opprettet").isEqualTo(
            InnsendingNy(
                id = id,
                opprettet = LocalDateTime.now(),
                personident = "12345678910",
                soknad = DefaultJsonMapper.toJson(enSøknad).toByteArray(),
                data = "data".toByteArray(),
                eksternRef = eksternRef,
                forrigeInnsendingId = null,
                type = InnsendingType.SOKNAD,
                journalpost_Id = null,
                filer = emptyList(),
            )
        )
    }

    @Test
    fun `lagre og hent filer`() {
        val filer = listOf(
            FilNy("vedlegg1", InMemoryFilData("innhold1".toByteArray())),
            FilNy("vedlegg2", InMemoryFilData("innhold2".toByteArray())),
        )
        val id = lagSøknad(filer = filer)

        val hentet = dataSource.transaction { con ->
            InnsendingRepo(con).hentFiler(id)
        }

        assertThat(hentet).hasSize(2)
        assertThat(hentet.map { it.tittel }).containsAll(listOf("vedlegg1", "vedlegg2"))
    }

    @Test
    fun `hentIdFraEksternRef returnerer id for kjent referanse`() {
        val eksternRef = UUID.randomUUID()
        val id = lagSøknad(eksternRef = eksternRef)

        val funnetId = dataSource.transaction { con ->
            InnsendingRepo(con).hentIdFraEksternRef(eksternRef)
        }

        assertThat(funnetId).isEqualTo(id)
    }

    @Test
    fun `hentIdFraEksternRef returnerer null for ukjent referanse`() {
        val funnetId = dataSource.transaction { con ->
            InnsendingRepo(con).hentIdFraEksternRef(UUID.randomUUID())
        }

        assertThat(funnetId).isNull()
    }

    @Test
    fun `erRefTilknyttetPersonIdent returnerer true for korrekt kombinasjon`() {
        val eksternRef = UUID.randomUUID()
        lagSøknad(personident = "12345678910", eksternRef = eksternRef)

        val resultat = dataSource.transaction { con ->
            InnsendingRepo(con).erRefTilknyttetPersonIdent("12345678910", eksternRef)
        }

        assertThat(resultat).isTrue()
    }

    @Test
    fun `erRefTilknyttetPersonIdent returnerer false for feil personident`() {
        val eksternRef = UUID.randomUUID()
        lagSøknad(personident = "12345678910", eksternRef = eksternRef)

        val resultat = dataSource.transaction { con ->
            InnsendingRepo(con).erRefTilknyttetPersonIdent("99999999999", eksternRef)
        }

        assertThat(resultat).isFalse()
    }

    @Test
    fun `hentAlleSøknader returnerer bare søknader for gitt personident`() {
        lagSøknad(personident = "12345678910")
        lagSøknad(personident = "12345678910")
        lagSøknad(personident = "99999999999")

        val søknader = dataSource.transaction { con ->
            InnsendingRepo(con).hentAlleSøknader("12345678910")
        }

        assertThat(søknader).hasSize(2)
    }

    @Test
    fun `hentSøknadMedReferanse returnerer søknad med ettersendinger`() {
        val soknadRef = UUID.randomUUID()
        val soknadId = lagSøknad(eksternRef = soknadRef)

        // Legg til ettersending koblet til søknaden
        dataSource.transaction { con ->
            InnsendingRepo(con).lagre(
                InnsendingNy(
                    id = null,
                    opprettet = LocalDateTime.now(),
                    personident = "12345678910",
                    soknad = null,
                    data = null,
                    eksternRef = UUID.randomUUID(),
                    forrigeInnsendingId = soknadId,
                    type = InnsendingType.ETTERSENDING,
                    journalpost_Id = null,
                    filer = emptyList(),
                )
            )
        }

        val søknad = dataSource.transaction { con ->
            InnsendingRepo(con).hentSøknadMedReferanse(soknadRef)
        }

        assertThat(søknad).usingRecursiveComparison()
            .ignoringFields("mottattDato", "ettersendinger.mottattDato", "ettersendinger.innsendingsId")
            .isEqualTo(
                MineAapSoknadMedEttersendingNy(
                    innsendingsId = soknadRef,
                    mottattDato = LocalDateTime.now(),
                    journalpostId = null,
                    ettersendinger = listOf(
                        MineAapEttersendingNy(
                            innsendingsId = UUID.randomUUID(),
                            mottattDato = LocalDateTime.now(),
                            journalpostId = null,
                        )
                    ),
                )
            )
    }

    @Test
    fun `hentSøknadMedReferanse returnerer null for ukjent referanse`() {
        val søknad = dataSource.transaction { con ->
            InnsendingRepo(con).hentSøknadMedReferanse(UUID.randomUUID())
        }

        assertThat(søknad).isNull()
    }

    @Test
    fun `markerFerdig setter journalpostId og nullstiller data`() {
        val eksternRef = UUID.randomUUID()
        val id = lagSøknad(eksternRef = eksternRef)

        dataSource.transaction { con ->
            InnsendingRepo(con).markerFerdig(id, "JP-001")
        }

        val hentet = dataSource.transaction { con ->
            InnsendingRepo(con).hent(id)
        }

        assertThat(hentet).usingRecursiveComparison().ignoringFields("opprettet").isEqualTo(
            InnsendingNy(
                id = id,
                opprettet = LocalDateTime.now(),
                personident = "12345678910",
                soknad = null,
                data = null,
                eksternRef = eksternRef,
                forrigeInnsendingId = null,
                type = InnsendingType.SOKNAD,
                journalpost_Id = "JP-001",
                filer = emptyList(),
            )
        )
    }

    @Test
    fun `markerFerdig nullstiller fildata`() {
        val filer = listOf(FilNy("vedlegg", InMemoryFilData("innhold".toByteArray())))
        val id = lagSøknad(filer = filer)

        dataSource.transaction { con ->
            InnsendingRepo(con).markerFerdig(id, "JP-002")
        }

        // Fildata skal være null i databasen etter markerFerdig
        val harData = dataSource.transaction { con ->
            con.queryFirst("SELECT count(*) > 0 AS har_data FROM fil_ny WHERE innsending_id = ? AND data IS NOT NULL") {
                setParams { setLong(1, id) }
                setRowMapper { it.getBoolean("har_data") }
            }
        }
        assertThat(harData).isFalse()
    }
}
