package com.example.iamsystem.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.example.iamsystem.common.util.SecretMask.MASK;

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

        /**
         * Never shows the password.
         */
        @Override
        public String toString() {
            return "RootUser[username=" + username + ", password=" + MASK + ", email=" + email + ", fullName=" + fullName + "]";
        }

        private static boolean isPresent(String value) {
            return value != null && !value.isBlank();
        }
    }
}
