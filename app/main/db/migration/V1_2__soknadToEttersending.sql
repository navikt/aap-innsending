CREATE TABLE soknad_ettersending (
  id SERIAL PRIMARY KEY,
  innsending_soknad_ref UUID NOT NULL,
  innsending_ettersending_ref UUID NOT NULL,
  UNIQUE (innsending_soknad_ref, innsending_ettersending_ref)
);

ALTER TABLE logg ADD COLUMN innsending_id UUID NOT NULL;