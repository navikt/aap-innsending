-- give access to IAM users (GCP)
GRANT ALL ON ALL TABLES IN SCHEMA PUBLIC TO cloudsqliamuser;

CREATE TABLE innsending
(
    id                      UUID PRIMARY KEY,
    opprettet               TIMESTAMP NOT NULL,
    personident             VARCHAR(11) NOT NULL,
    data                    JSON
);

CREATE TABLE fil
(
    id                      UUID PRIMARY KEY,
    innsending_id           UUID REFERENCES innsending (id),
    tittel                  TEXT,
    fil                     BYTEA, -- ligger i redis
);
