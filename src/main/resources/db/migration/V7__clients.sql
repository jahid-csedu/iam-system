-- Clients: every login goes through a registered client of a tenant, identified by its public client_id.
-- Each tenant has exactly one MANAGEMENT_CONSOLE client (management users only) and any number of APPLICATION clients.

-- The unused applications table (never written by any code) is replaced by clients.
DROP TABLE applications;

CREATE TABLE clients (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id  BIGINT       NOT NULL,
    client_id  VARCHAR(24)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_clients_client_id UNIQUE (client_id),
    CONSTRAINT uk_clients_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_clients_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

-- At most one management console per tenant: the expression is NULL for other clients, and NULLs never collide.
CREATE UNIQUE INDEX uk_clients_console_per_tenant ON clients ((CASE WHEN type = 'MANAGEMENT_CONSOLE' THEN tenant_id END));

-- Existing tenants get their console client (24 URL-safe characters from 18 random bytes).
INSERT INTO clients (tenant_id, client_id, name, type, created_at, updated_at)
SELECT id,
       REPLACE(REPLACE(TO_BASE64(RANDOM_BYTES(18)), '+', '-'), '/', '_'),
       'Management Console', 'MANAGEMENT_CONSOLE', NOW(6), NOW(6)
FROM tenants;

-- Usernames and emails are unique per tenant now that login knows the tenant through the client.
-- (A root user's username and email stay unique across all users of all tenants: checked by the application.)
ALTER TABLE users MODIFY username VARCHAR(255) NOT NULL;
ALTER TABLE users DROP INDEX uk_users_email;
ALTER TABLE users ADD CONSTRAINT uk_users_tenant_username UNIQUE (tenant_id, username);
ALTER TABLE users ADD CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email);
ALTER TABLE users DROP INDEX idx_users_tenant;

INSERT INTO permissions (name, scope, description) VALUES
    ('clients:read',   'TENANT', 'List and view the client applications of the tenant'),
    ('clients:create', 'TENANT', 'Register client applications'),
    ('clients:update', 'TENANT', 'Rename client applications'),
    ('clients:delete', 'TENANT', 'Delete client applications (never the management console)');

INSERT INTO role_template_permissions (role_template_id, permission_id)
SELECT t.id, p.id
FROM role_templates t
JOIN permissions p ON p.name LIKE 'clients:%'
WHERE t.name = 'ADMIN';

INSERT INTO role_template_permissions (role_template_id, permission_id)
SELECT t.id, p.id
FROM role_templates t
JOIN permissions p ON p.name = 'clients:read'
WHERE t.name = 'VIEWER';
