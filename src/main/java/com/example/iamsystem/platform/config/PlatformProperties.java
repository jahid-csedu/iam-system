package com.example.iamsystem.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The first super user ({@code iam.platform.root.*}), created as the platform tenant's root while the platform has none.
 * Read only then: changing it later changes nothing. {@code root} is {@code null} when nothing is configured.
 */
@ConfigurationProperties("iam.platform")
public record PlatformProperties(RootUser root) {

    /**
     * @param fullName optional; the other three are required together
     */
    public record RootUser(String username, String password, String email, String fullName) {

        public boolean isComplete() {
            return isPresent(username) && isPresent(password) && isPresent(email);
        }

        private static boolean isPresent(String value) {
            return value != null && !value.isBlank();
        }
    }
}
