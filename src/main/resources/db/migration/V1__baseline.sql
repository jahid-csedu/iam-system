-- Baseline schema matching the JPA entities as of the Flyway introduction.
-- Never edit this file; add a new V<n>__<description>.sql migration instead.

CREATE TABLE organizations (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(255) NOT NULL,
    status     VARCHAR(255),
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_organizations_name UNIQUE (name)
);

CREATE TABLE services (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    service_name VARCHAR(255) NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_services_service_name UNIQUE (service_name)
);

CREATE TABLE applications (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    application_name VARCHAR(255) NOT NULL,
    organization_id  BIGINT       NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_applications_organization FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    username              VARCHAR(255),
    password              VARCHAR(255),
    full_name             VARCHAR(255),
    email                 VARCHAR(255),
    is_root_user          BOOLEAN      NOT NULL,
    is_super_user         BOOLEAN      NOT NULL,
    organization_id       BIGINT,
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
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE roles (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255),
    description     VARCHAR(255),
    is_global       BOOLEAN      NOT NULL,
    organization_id BIGINT,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_roles_organization FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE TABLE permissions (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    service_name   VARCHAR(255),
    action         VARCHAR(50),
    description    VARCHAR(255),
    service_id     BIGINT,
    application_id BIGINT,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_permissions_service FOREIGN KEY (service_id) REFERENCES services (id),
    CONSTRAINT fk_permissions_application FOREIGN KEY (application_id) REFERENCES applications (id)
);

CREATE TABLE password_reset_otps (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    otp         VARCHAR(255) NOT NULL,
    user_id     BIGINT       NOT NULL,
    expiry_date DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_otps_otp UNIQUE (otp),
    CONSTRAINT uk_password_reset_otps_user UNIQUE (user_id),
    CONSTRAINT fk_password_reset_otps_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
);
