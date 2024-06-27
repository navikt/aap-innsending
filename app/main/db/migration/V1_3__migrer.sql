INSERT INTO soknad_ettersending (innsending_soknad_ref, innsending_ettersending_ref)
SELECT temp.innsending_soknad_ref, temp.innsending_ettersending_ref
FROM soknad_ettersending_temp temp;

DROP TABLE soknad_ettersending_temp;
