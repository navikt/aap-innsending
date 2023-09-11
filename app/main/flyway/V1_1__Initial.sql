-- give access to IAM users (GCP)
GRANT ALL ON ALL TABLES IN SCHEMA PUBLIC TO cloudsqliamuser;

CREATE TABLE innsending
(
    innsendingsreferanse    UUID PRIMARY KEY,
    opprettet               TIMESTAMP NOT NULL,
    fullfort                TIMESTAMP DEFAULT NULL,
    sist_oppdatert          TIMESTAMP NOT NULL,
    sendt_til_arkivering    BOOLEAN DEFAULT FALSE NOT NULL,
    brukerid                VARCHAR(11) NOT NULL,
    brevkode                TEXT NOT NULL, -- TODO skal alle innsendinger ha brevkode
    type                    TEXT NOT NULL,
    versjon                 INT,
    data                    JSON
);

CREATE TABLE fil
(
    filreferanse            UUID PRIMARY KEY,
    innsendingsreferanse    UUID REFERENCES innsending (innsendingsreferanse),
    tittel                  TEXT
);


-- Vedleggskrav? Kanskje i eget API (varsel)
