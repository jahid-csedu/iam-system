-- Tenants are STANDARD (created by signup) or the single PLATFORM tenant of the super users. The platform tenant itself is
-- created by the application at startup (PlatformBootstrap), together with its console client and predefined roles.

ALTER TABLE tenants ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'STANDARD' AFTER name;
ALTER TABLE tenants ALTER COLUMN type DROP DEFAULT;

-- At most one platform tenant: the expression is NULL for standard tenants, and NULLs never collide.
CREATE UNIQUE INDEX uk_tenants_platform ON tenants ((CASE WHEN type = 'PLATFORM' THEN 1 END));
