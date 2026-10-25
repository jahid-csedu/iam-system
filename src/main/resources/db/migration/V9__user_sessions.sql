-- Login sessions and their refresh tokens. Every login starts a session; access tokens name it in their sid claim, so
-- revoking the session (logout, password change or reset) ends its access and refresh tokens at once. A session ends at
-- expires_at: the access-token lifetime for clients without refresh tokens, otherwise the expiry of its latest refresh token.
-- Deleting the user or the client deletes its sessions.

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

-- Refresh tokens are random values; only their SHA-256 hash (hex) is stored. Each one is used once (used_at): a refresh
-- replaces it with a new one, and presenting a used one again revokes its session.
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
