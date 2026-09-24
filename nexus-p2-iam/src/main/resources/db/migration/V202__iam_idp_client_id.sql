-- The OAuth client identifier is public metadata needed for token-endpoint checks.
-- It is not a client secret and is not sensitive credential material.
ALTER TABLE iam.iam_idp_configuration
    ADD COLUMN client_id VARCHAR(256),
    ADD COLUMN deleted_at TIMESTAMPTZ;
