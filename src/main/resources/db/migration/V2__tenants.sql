-- Organizations become tenants: every user belongs to exactly one tenant, and each tenant has exactly one root user.
-- Assumes no tenant-less users exist (no production data at this point).

RENAME TABLE organizations TO tenants;
ALTER TABLE tenants RENAME INDEX uk_organizations_name TO uk_tenants_name;
ALTER TABLE tenants MODIFY status VARCHAR(20) NOT NULL;

-- users.organization_id -> users.tenant_id (required)
ALTER TABLE users DROP FOREIGN KEY fk_users_organization;
ALTER TABLE users RENAME COLUMN organization_id TO tenant_id;
ALTER TABLE users MODIFY tenant_id BIGINT NOT NULL;
ALTER TABLE users RENAME INDEX fk_users_organization TO fk_users_tenant;
ALTER TABLE users ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);

-- At most one root user per tenant: the expression is NULL for non-root users, and NULLs never collide.
CREATE UNIQUE INDEX uk_users_root_per_tenant ON users ((CASE WHEN is_root_user THEN tenant_id END));

-- roles.organization_id -> roles.tenant_id (still optional until tenant isolation); global roles are gone
ALTER TABLE roles DROP FOREIGN KEY fk_roles_organization;
ALTER TABLE roles RENAME COLUMN organization_id TO tenant_id;
ALTER TABLE roles RENAME INDEX fk_roles_organization TO fk_roles_tenant;
ALTER TABLE roles ADD CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE roles DROP COLUMN is_global;

-- applications.organization_id -> applications.tenant_id
ALTER TABLE applications DROP FOREIGN KEY fk_applications_organization;
ALTER TABLE applications RENAME COLUMN organization_id TO tenant_id;
ALTER TABLE applications RENAME INDEX fk_applications_organization TO fk_applications_tenant;
ALTER TABLE applications ADD CONSTRAINT fk_applications_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
