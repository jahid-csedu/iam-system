-- Token settings per client: access-token lifetime, whether refresh tokens are issued and their lifetime, and which user
-- claims go into access tokens. Every token also carries sub (user id), client_id, version, iat and exp.
-- Existing clients get the defaults new clients get: 5 minutes, refresh tokens on for 24 hours, the username claim.

ALTER TABLE clients
    ADD COLUMN access_token_ttl_seconds  INT     NOT NULL DEFAULT 300   AFTER type,
    ADD COLUMN refresh_token_enabled     BOOLEAN NOT NULL DEFAULT TRUE  AFTER access_token_ttl_seconds,
    ADD COLUMN refresh_token_ttl_seconds INT     NOT NULL DEFAULT 86400 AFTER refresh_token_enabled;

ALTER TABLE clients
    ALTER COLUMN access_token_ttl_seconds DROP DEFAULT,
    ALTER COLUMN refresh_token_enabled DROP DEFAULT,
    ALTER COLUMN refresh_token_ttl_seconds DROP DEFAULT;

-- client_ref_id is clients.id (clients.client_id is the public identifier).
CREATE TABLE client_token_claims (
    client_ref_id BIGINT      NOT NULL,
    claim         VARCHAR(30) NOT NULL,
    PRIMARY KEY (client_ref_id, claim),
    CONSTRAINT fk_client_token_claims_client FOREIGN KEY (client_ref_id) REFERENCES clients (id) ON DELETE CASCADE
);

INSERT INTO client_token_claims (client_ref_id, claim)
SELECT id, 'USERNAME' FROM clients;

UPDATE permissions SET description = 'Rename clients and change their token settings' WHERE name = 'clients:update';
