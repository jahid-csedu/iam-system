-- Grants: what a platform user may do inside other tenants (with X-Tenant-Id). A grant gives a platform user either a
-- role of the platform tenant or a single permission, on one tenant or, with tenant_id NULL, on every standard tenant
-- (including tenants created later). Inside the platform tenant itself platform users use their normal roles instead.
-- Who may receive a grant (management users of the platform tenant) and which roles (platform tenant roles) is checked
-- when a grant is given.

CREATE TABLE platform_role_grants (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    role_id    BIGINT      NOT NULL,
    tenant_id  BIGINT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_platform_role_grants_tenant (tenant_id),
    CONSTRAINT fk_platform_role_grants_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_platform_role_grants_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_platform_role_grants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);

CREATE TABLE platform_permission_grants (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    permission_id BIGINT      NOT NULL,
    tenant_id     BIGINT,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_platform_permission_grants_tenant (tenant_id),
    CONSTRAINT fk_platform_permission_grants_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_platform_permission_grants_permission FOREIGN KEY (permission_id) REFERENCES permissions (id),
    CONSTRAINT fk_platform_permission_grants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);

-- One grant per user, role or permission, and tenant (0 stands for "all tenants", since NULLs never collide).
CREATE UNIQUE INDEX uk_platform_role_grants ON platform_role_grants (user_id, role_id, (COALESCE(tenant_id, 0)));
CREATE UNIQUE INDEX uk_platform_permission_grants ON platform_permission_grants (user_id, permission_id, (COALESCE(tenant_id, 0)));
