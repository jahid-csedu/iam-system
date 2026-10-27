-- Baseline schema at the end of the implementation plan (2026-10-27): migrations V1 to V22 merged into one file, taken
-- from the schema they produced. Never edit this file; add a new V<n>__<description>.sql migration instead.
-- Indexes that only back a foreign key are created by MySQL under the foreign key's name.

-- Tenants. Exactly one PLATFORM tenant (created at startup); every other tenant is STANDARD and created by signup.
CREATE TABLE tenants (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(255) NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tenants_name UNIQUE (name)
);
CREATE UNIQUE INDEX uk_tenants_platform ON tenants ((CASE WHEN type = 'PLATFORM' THEN 1 END));

-- Users. Usernames and emails are unique per tenant; one root per tenant. A root's username and email are unique across
-- all tenants, which the application checks (MySQL can't express it as one constraint).
CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    username              VARCHAR(255) NOT NULL,
    password              VARCHAR(255),
    full_name             VARCHAR(255),
    email                 VARCHAR(255),
    email_verified        BOOLEAN      NOT NULL,
    user_type             VARCHAR(20)  NOT NULL,
    is_root_user          BOOLEAN      NOT NULL,
    tenant_id             BIGINT       NOT NULL,
    active                BOOLEAN,
    password_expired      BOOLEAN,
    password_expiry_date  DATETIME(6),
    user_locked           BOOLEAN,
    failed_login_attempts INT,
    account_locked_until  DATETIME(6),
    created_by            BIGINT,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    version               INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_tenant_username UNIQUE (tenant_id, username),
    CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE UNIQUE INDEX uk_users_root_per_tenant ON users ((CASE WHEN is_root_user THEN tenant_id END));

-- The fixed permission catalog (reference data below). TENANT keys go into tenants' roles; PLATFORM keys only into the
-- platform tenant's roles and grants.
CREATE TABLE permissions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    scope       VARCHAR(20)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_permissions_name UNIQUE (name)
);

-- Tenant-owned roles; deleting a role removes it from its users and grants.
CREATE TABLE roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    tenant_id   BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Permissions given to a management user directly (next to their roles).
CREATE TABLE user_permissions (
    user_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, permission_id),
    CONSTRAINT fk_user_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id),
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- The predefined roles copied into every new tenant at signup (reference data below); later changes never reach
-- existing tenants.
CREATE TABLE role_templates (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_role_templates_name UNIQUE (name)
);

CREATE TABLE role_template_permissions (
    role_template_id BIGINT NOT NULL,
    permission_id    BIGINT NOT NULL,
    PRIMARY KEY (role_template_id, permission_id),
    CONSTRAINT fk_role_template_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id),
    CONSTRAINT fk_role_template_permissions_template FOREIGN KEY (role_template_id) REFERENCES role_templates (id)
);

-- Platform users' roles and permissions on one tenant, or on all standard tenants when tenant_id is NULL.
CREATE TABLE platform_role_grants (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    role_id    BIGINT      NOT NULL,
    tenant_id  BIGINT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_platform_role_grants_tenant (tenant_id),
    CONSTRAINT fk_platform_role_grants_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_platform_role_grants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_platform_role_grants_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uk_platform_role_grants ON platform_role_grants (user_id, role_id, (COALESCE(tenant_id, 0)));

CREATE TABLE platform_permission_grants (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    permission_id BIGINT      NOT NULL,
    tenant_id     BIGINT,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_platform_permission_grants_tenant (tenant_id),
    CONSTRAINT fk_platform_permission_grants_permission FOREIGN KEY (permission_id) REFERENCES permissions (id),
    CONSTRAINT fk_platform_permission_grants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_platform_permission_grants_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uk_platform_permission_grants ON platform_permission_grants (user_id, permission_id, (COALESCE(tenant_id, 0)));

-- Client applications with their token settings; one MANAGEMENT_CONSOLE per tenant.
CREATE TABLE clients (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id                 BIGINT       NOT NULL,
    client_id                 VARCHAR(24)  NOT NULL,
    name                      VARCHAR(255) NOT NULL,
    type                      VARCHAR(30)  NOT NULL,
    status                    VARCHAR(20)  NOT NULL,
    access_token_ttl_seconds  INT          NOT NULL,
    refresh_token_enabled     BOOLEAN      NOT NULL,
    refresh_token_ttl_seconds INT          NOT NULL,
    created_at                DATETIME(6)  NOT NULL,
    updated_at                DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_clients_client_id UNIQUE (client_id),
    CONSTRAINT uk_clients_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_clients_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE UNIQUE INDEX uk_clients_console_per_tenant ON clients ((CASE WHEN type = 'MANAGEMENT_CONSOLE' THEN tenant_id END));

-- The user claims a client's access tokens carry.
CREATE TABLE client_token_claims (
    client_ref_id BIGINT      NOT NULL,
    claim         VARCHAR(30) NOT NULL,
    PRIMARY KEY (client_ref_id, claim),
    CONSTRAINT fk_client_token_claims_client FOREIGN KEY (client_ref_id) REFERENCES clients (id) ON DELETE CASCADE
);

-- Login sessions and their single-use refresh tokens (SHA-256 hex of the token only).
CREATE TABLE user_sessions (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    client_ref_id BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    expires_at    DATETIME(6) NOT NULL,
    revoked_at    DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_user_sessions_tenant (tenant_id),
    CONSTRAINT fk_user_sessions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_sessions_client FOREIGN KEY (client_ref_id) REFERENCES clients (id) ON DELETE CASCADE
);

CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    session_id BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_session FOREIGN KEY (session_id) REFERENCES user_sessions (id) ON DELETE CASCADE
);

-- One-time codes: a user's pending password-reset OTP and email verification code (bcrypt hashes, at most one each),
-- and the requests counted by the hourly limit per purpose.
CREATE TABLE password_reset_otps (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    otp_hash        VARCHAR(255) NOT NULL,
    user_id         BIGINT       NOT NULL,
    expiry_date     DATETIME(6)  NOT NULL,
    failed_attempts INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_otps_user UNIQUE (user_id),
    CONSTRAINT fk_password_reset_otps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE email_verification_codes (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    code_hash       VARCHAR(255) NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    failed_attempts INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_codes_user UNIQUE (user_id),
    CONSTRAINT fk_email_verification_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE otp_requests (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    purpose      VARCHAR(30) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_otp_requests_user (user_id, purpose, requested_at),
    CONSTRAINT fk_otp_requests_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Reference data: the permission catalog (PermissionKey mirrors it; a test keeps them in sync).
INSERT INTO permissions (name, scope, description) VALUES
    ('users:read', 'TENANT', 'List and view the users of the tenant'),
    ('users:create', 'TENANT', 'Create users in the tenant'),
    ('users:update', 'TENANT', 'Change other users'' names and emails, roles and direct permissions'),
    ('users:delete', 'TENANT', 'Delete users'),
    ('users:reset-password', 'TENANT', 'Reset another user''s password (the user gets a one-time code by email)'),
    ('roles:read', 'TENANT', 'List and view the roles of the tenant'),
    ('roles:create', 'TENANT', 'Create roles'),
    ('roles:update', 'TENANT', 'Rename roles and change their permissions'),
    ('roles:delete', 'TENANT', 'Delete roles'),
    ('permissions:read', 'TENANT', 'List and view the permission catalog'),
    ('users:create-management', 'TENANT', 'Create management users (users:create covers regular users)'),
    ('clients:read', 'TENANT', 'List and view the client applications of the tenant'),
    ('clients:create', 'TENANT', 'Register client applications'),
    ('clients:update', 'TENANT', 'Rename clients and change their token settings'),
    ('clients:delete', 'TENANT', 'Delete client applications (never the management console)'),
    ('tenants:read', 'PLATFORM', 'List and view all tenants'),
    ('tenants:create', 'PLATFORM', 'Create tenants with their root user'),
    ('tenants:suspend', 'PLATFORM', 'Suspend a tenant: none of its users can log in or use their tokens'),
    ('tenants:activate', 'PLATFORM', 'Reactivate a suspended tenant'),
    ('clients:suspend', 'TENANT', 'Suspend an application client: nobody can log in or use tokens through it'),
    ('clients:activate', 'TENANT', 'Reactivate a suspended application client'),
    ('users:deactivate', 'TENANT', 'Deactivate a user: they can''t log in, and their tokens stop working'),
    ('users:activate', 'TENANT', 'Reactivate a deactivated user'),
    ('users:unlock', 'TENANT', 'End a user''s login lockout at once');

-- Reference data: the predefined roles. ADMIN holds every TENANT key.
INSERT INTO role_templates (name, description) VALUES
    ('ADMIN', 'Manages the whole tenant: users, roles and password resets'),
    ('USER_MANAGER', 'Creates, updates and deletes users'),
    ('VIEWER', 'Views users, roles and the permission catalog');

INSERT INTO role_template_permissions (role_template_id, permission_id)
SELECT t.id, p.id
FROM role_templates t
JOIN permissions p ON p.scope = 'TENANT'
WHERE t.name = 'ADMIN';

INSERT INTO role_template_permissions (role_template_id, permission_id)
SELECT t.id, p.id
FROM role_templates t
JOIN permissions p ON p.name IN ('users:read', 'users:create', 'users:update', 'users:delete', 'users:deactivate',
                                 'users:activate', 'users:unlock')
WHERE t.name = 'USER_MANAGER';

INSERT INTO role_template_permissions (role_template_id, permission_id)
SELECT t.id, p.id
FROM role_templates t
JOIN permissions p ON p.name IN ('users:read', 'roles:read', 'permissions:read', 'clients:read')
WHERE t.name = 'VIEWER';
