-- DORA-2.8: finalize email encryption.
--
-- After V7 + V7.1 have copied every plaintext email into the encrypted
-- email_enc column and populated email_search_hash, this migration
-- removes the old plaintext column and makes email_search_hash the
-- unique lookup key.

ALTER TABLE app_user DROP COLUMN email;
ALTER TABLE app_user RENAME COLUMN email_enc TO email;
ALTER TABLE app_user ADD CONSTRAINT uq_app_user_email_search_hash UNIQUE (email_search_hash);
