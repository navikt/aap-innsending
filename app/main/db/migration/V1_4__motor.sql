CREATE TABLE innsending_ny
(
    id                    BIGSERIAL PRIMARY KEY,
    opprettet             TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    personident           VARCHAR(11)                            NOT NULL,
    soknad                BYTEA,
    data                  BYTEA,
    ekstern_referanse     UUID                                   NOT NULL UNIQUE,
    type                  VARCHAR(50)                            NOT NULL,
    forrige_innsending_id BIGINT                                 NULL REFERENCES innsending_ny (id),
    journalpost_id        VARCHAR(50)  DEFAULT NULL
);

CREATE INDEX IDX_INNSENDING_NY_REFERANSE ON innsending_ny (ekstern_referanse);

CREATE TABLE fil_ny
(
    id            BIGSERIAL PRIMARY KEY,
    innsending_id BIGINT NOT NULL REFERENCES innsending_ny (id),
    tittel        TEXT   NOT NULL,
    data          BYTEA
);

INSERT INTO innsending_ny (opprettet, personident, soknad, data, ekstern_referanse, type, forrige_innsending_id,
                           journalpost_id)
SELECT l.mottatt_dato,
       l.personident,
       NULL,
       NULL,
       l.innsending_id,
       l.type,
       (SELECT i.id
        from soknad_ettersending s
                 JOIN innsending_ny i ON i.ekstern_referanse = s.innsending_soknad_ref
        WHERE s.innsending_ettersending_ref = l.innsending_id),
       l.journalpost_id
FROM Logg l
ORDER BY l.mottatt_dato;



CREATE TABLE JOBB
(
    ID            BIGSERIAL                              NOT NULL PRIMARY KEY,
    STATUS        VARCHAR(50)  DEFAULT 'KLAR'            NOT NULL,
    TYPE          VARCHAR(50)                            NOT NULL,
    SAK_ID        BIGINT                                 NULL REFERENCES innsending_ny (id),
    BEHANDLING_ID BIGINT                                 NULL,
    parameters    text                                   NULL,
    payload       text                                   NULL,
    NESTE_KJORING TIMESTAMP(3)                           NOT NULL,
    OPPRETTET_TID TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
);



CREATE INDEX IDX_JOBB_STATUS_SAK_BEHANDLING ON JOBB (STATUS, SAK_ID, BEHANDLING_ID, NESTE_KJORING);
CREATE INDEX IDX_JOBB_SAK ON JOBB (SAK_ID);
CREATE INDEX IDX_JOBB_BEHANDLING ON JOBB (BEHANDLING_ID);
CREATE INDEX IDX_JOBB_SAK_BEHANDLING ON JOBB (SAK_ID, BEHANDLING_ID);
CREATE INDEX IDX_JOBB_STATUS ON JOBB (STATUS);
CREATE INDEX IDX_JOBB_TYPE ON JOBB (TYPE);
CREATE INDEX IDX_JOBB_STATUS_NESTE_KJORING ON JOBB (STATUS, NESTE_KJORING);
CREATE INDEX IDX_JOBB_NESTE_KJORING ON JOBB (NESTE_KJORING);
CREATE INDEX IDX_JOBB_NESTE_KJORING_SAK_BEHANDLING ON JOBB (SAK_ID, BEHANDLING_ID, NESTE_KJORING);

CREATE TABLE JOBB_HISTORIKK
(
    ID            BIGSERIAL                              NOT NULL PRIMARY KEY,
    JOBB_ID       BIGINT                                 NOT NULL REFERENCES JOBB (ID),
    STATUS        VARCHAR(50)                            NOT NULL,
    FEILMELDING   TEXT                                   NULL,
    OPPRETTET_TID TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IDX_JOBB_HISTORIKK_ID_STATUS ON JOBB_HISTORIKK (JOBB_ID, STATUS);
CREATE INDEX IDX_JOBB_HISTORIKK_STATUS ON JOBB_HISTORIKK (STATUS);
CREATE INDEX IDX_JOBB_HISTORIKK_TID ON JOBB_HISTORIKK (OPPRETTET_TID);
CREATE INDEX IDX_JOBB_HISTORIKK_JOBB_ID ON JOBB_HISTORIKK (JOBB_ID);
