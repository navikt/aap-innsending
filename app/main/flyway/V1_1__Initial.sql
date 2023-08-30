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
    soknad_id               UUID REFERENCES soknad (soknad_id), -- TODO Skal denne være med?
    tittel                  TEXT
);

CREATE TABLE soknad -- TODO, kan vi lagre fullført søknad lenge?
(
    soknad_id               UUID PRIMARY KEY,
    brukerid                VARCHAR(11),
    opprettet               TIMESTAMP NOT NULL,
    innsendt                TIMESTAMP DEFAULT NULL,
    data                    JSON
);

