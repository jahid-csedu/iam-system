-- Tenant isolation: every role belongs to one tenant, and every tenant-owned table has an index starting with tenant_id.
-- Assumes no tenant-less roles exist (no production data at this point).

-- Roles: required tenant and name, names unique per tenant. The unique key also serves tenant_id lookups and the FK.
ALTER TABLE roles MODIFY tenant_id BIGINT NOT NULL;
ALTER TABLE roles MODIFY name VARCHAR(255) NOT NULL;
ALTER TABLE roles ADD CONSTRAINT uk_roles_tenant_name UNIQUE (tenant_id, name);
ALTER TABLE roles DROP INDEX fk_roles_tenant;

-- Users: the tenant_id index is named as an index, not after the FK that created it.
ALTER TABLE users RENAME INDEX fk_users_tenant TO idx_users_tenant;

-- created_by is audit information only: deleting a user keeps the users they created.
ALTER TABLE users DROP FOREIGN KEY fk_users_created_by;
ALTER TABLE users ADD CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL;

-- Deleting a role removes it from its users.
ALTER TABLE user_roles DROP FOREIGN KEY fk_user_roles_role;
ALTER TABLE user_roles ADD CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE;

-- Applications: same index naming as users.
ALTER TABLE applications RENAME INDEX fk_applications_tenant TO idx_applications_tenant;
