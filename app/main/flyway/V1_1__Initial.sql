-- give access to IAM users (GCP)
GRANT ALL ON ALL TABLES IN SCHEMA PUBLIC TO cloudsqliamuser;

CREATE TABLE innsending
(
    innsendingsreferanse    UUID PRIMARY KEY,
    opprettet               TIMESTAMP NOT NULL,
    fullfoert               TIMESTAMP DEFAULT NULL,
    brukerid                VARCHAR(11) NOT NULL,
    brevkode                TEXT NOT NULL -- TODO skal alle innsendinger ha brevkode
);

CREATE TABLE fil
(
    filreferanse            UUID PRIMARY KEY,
    innsendingsreferanse    UUID REFERENCES innsending (innsendingsreferanse),
    tittel                  TEXT
);

-- TODO Kanskje søknad skal bli en generell representasjon av "noe blir innsendt"?
-- TODO Søknad må versjoneres og migreres
CREATE TABLE soknad
(
    soknad_id               UUID PRIMARY KEY,
    innsendingsreferanse    UUID REFERENCES innsending (innsendingsreferanse),
    brukerid                VARCHAR(11),
    opprettet               TIMESTAMP NOT NULL,
    innsendt                TIMESTAMP DEFAULT NULL,
    versjon                 INT,
    data                    JSON
);

-- Vedleggskrav? Kanskje i eget API (varsel)
