CREATE TABLE innsending
(
    id                      UUID PRIMARY KEY,
    opprettet               TIMESTAMP NOT NULL,
    personident             VARCHAR(11) NOT NULL,
    soknad                  BYTEA,
    data                    BYTEA
);

CREATE TABLE fil
(
    id                      UUID PRIMARY KEY,
    innsending_id           UUID REFERENCES innsending (id) ON DELETE CASCADE,
    tittel                  TEXT,
    data                    BYTEA -- ligger i redis
);

CREATE TABLE logg(
    personident         VARCHAR(11) NOT NULL,
    mottatt_dato        TIMESTAMP NOT NULL,
    journalpost_id      TEXT UNIQUE NOT NULL,
    innsending_id       UUID NOT NULL,
    type                TEXT NOT NULL -- Kan være 'soknad' eller 'ettersendelse'
);

CREATE TABLE soknad_ettersending (
    id SERIAL PRIMARY KEY,
    innsending_soknad_ref UUID NOT NULL,
    innsending_ettersending_ref UUID NOT NULL,
    UNIQUE (innsending_soknad_ref, innsending_ettersending_ref)
);

