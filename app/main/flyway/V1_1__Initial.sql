-- give access to IAM users (GCP)
GRANT ALL ON ALL TABLES IN SCHEMA PUBLIC TO cloudsqliamuser;

CREATE TABLE innsending
(
    id                      UUID PRIMARY KEY,
    opprettet               TIMESTAMP NOT NULL,
    personident             VARCHAR(11) NOT NULL,
    data                    BYTEA
);

CREATE TABLE fil
(
    id                      UUID PRIMARY KEY,
    innsending_id           UUID REFERENCES innsending (id) ON DELETE CASCADE,
    tittel                  TEXT,
    data                    BYTEA -- ligger i redis
);
