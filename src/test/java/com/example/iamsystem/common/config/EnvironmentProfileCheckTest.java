package com.example.iamsystem.common.config;

import com.example.iamsystem.platform.config.PlatformProperties;
import com.example.iamsystem.security.config.CorsProperties;
import com.example.iamsystem.security.config.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the check against the real configuration files of src/main/resources (the test classpath has its own
 * application.properties), with environment variables simulated by a property source in front of them.
 */
class EnvironmentProfileCheckTest {

    private static final Map<String, Object> ALL_VARIABLES = Map.of(
            "SPRING_DATASOURCE_URL", "jdbc:mysql://db:3306/iam_system_db",
            "SPRING_DATASOURCE_USERNAME", "iam",
            "SPRING_DATASOURCE_PASSWORD", "db-password",
            "IAM_JWT_ISSUER", "https://iam.example.com",
            "IAM_JWT_KEYS_0_KID", "key-1",
            "SPRING_MAIL_USERNAME", "iam@example.com",
            "SPRING_MAIL_PASSWORD", "mail-password",
            "IAM_MAIL_FROM", "IAM <no-reply@example.com>",
            "IAM_CORS_ALLOWEDORIGINS", "https://console.example.com");

    private static final String[] PROFILES = {"local", "dev", "stage", "prod"};

    private final EnvironmentProfileCheck check = new EnvironmentProfileCheck();

    @Test
    @DisplayName("Without an environment profile, or with two, the startup stops")
    void noOrSeveralEnvironmentProfiles_stopTheStartup() throws IOException {
        IllegalStateException none = assertThrows(IllegalStateException.class, () -> run(environment(Map.of())));
        assertTrue(none.getMessage().contains("set SPRING_PROFILES_ACTIVE"), none.getMessage());

        assertThrows(IllegalStateException.class, () -> run(environment(ALL_VARIABLES, "dev", "prod")));
    }

    @Test
    @DisplayName("Every profile, local included, names every missing or empty setting with its environment variable")
    void everyProfile_namesEveryMissingSetting() throws IOException {
        for (String profile : PROFILES) {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> run(environment(Map.of("SPRING_MAIL_PASSWORD", " "), profile)));

            assertEquals("The '" + profile + "' profile needs these settings: spring.datasource.url (SPRING_DATASOURCE_URL), "
                    + "spring.datasource.username (SPRING_DATASOURCE_USERNAME), spring.datasource.password "
                    + "(SPRING_DATASOURCE_PASSWORD), iam.jwt.issuer (IAM_JWT_ISSUER), iam.jwt.keys[0].kid (IAM_JWT_KEYS_0_KID), "
                    + "spring.mail.username (SPRING_MAIL_USERNAME), spring.mail.password (SPRING_MAIL_PASSWORD), "
                    + "iam.mail.from (IAM_MAIL_FROM), iam.cors.allowed-origins (IAM_CORS_ALLOWEDORIGINS)", refused.getMessage());
        }
    }

    @Test
    @DisplayName("Every profile starts when every setting has a value")
    void everyProfile_withEverySetting_starts() {
        for (String profile : PROFILES) {
            assertDoesNotThrow(() -> run(environment(ALL_VARIABLES, profile)));
        }
    }

    @Test
    @DisplayName("Every profile reads the signing key, the platform root and the origins, and allows no key or root to be missing")
    void everyProfile_bindsKeyRootAndOrigins() throws IOException {
        Map<String, Object> variables = new HashMap<>(ALL_VARIABLES);
        variables.put("IAM_JWT_KEYS_0_PRIVATEKEYLOCATION", "file:/run/secrets/jwt-signing-key.pem");
        variables.put("IAM_PLATFORM_ROOT_USERNAME", "platform-root");
        for (String profile : PROFILES) {
            Binder binder = Binder.get(environment(variables, profile));

            JwtProperties jwt = binder.bindOrCreate("iam.jwt", JwtProperties.class);
            assertEquals("key-1", jwt.keys().getFirst().kid(), profile);
            assertEquals("file:/run/secrets/jwt-signing-key.pem", jwt.keys().getFirst().privateKeyLocation(), profile);
            assertFalse(jwt.allowTemporaryKey(), profile);
            PlatformProperties platform = binder.bindOrCreate("iam.platform", PlatformProperties.class);
            assertEquals("platform-root", platform.root().username(), profile);
            assertFalse(platform.allowMissingRoot(), profile);
            assertEquals(List.of("https://console.example.com"),
                    binder.bindOrCreate("iam.cors", CorsProperties.class).allowedOrigins(), profile);
        }
    }

    @Test
    @DisplayName("The test suite's profile needs nothing")
    void testProfile_needsNothing() {
        StandardEnvironment test = new StandardEnvironment();
        test.setActiveProfiles("test");

        assertDoesNotThrow(() -> run(test));
    }

    @Test
    @DisplayName("A setting's environment variable follows Spring Boot's mapping")
    void environmentVariable_followsSpringBootsMapping() {
        assertEquals("IAM_CORS_ALLOWEDORIGINS", EnvironmentProfileCheck.environmentVariable("iam.cors.allowed-origins"));
        assertEquals("IAM_JWT_KEYS_0_PRIVATEKEY", EnvironmentProfileCheck.environmentVariable("iam.jwt.keys[0].private-key"));
    }

    private void run(StandardEnvironment environment) {
        check.postProcessEnvironment(environment, new SpringApplication());
    }

    /**
     * The shipped base file and, for each active profile, its file, with the given environment variables in front. The
     * machine's own variables and system properties are left out (Gradle sets spring.profiles.active=test).
     */
    private static StandardEnvironment environment(Map<String, Object> variables, String... profiles) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.setActiveProfiles(profiles);
        environment.getPropertySources().addFirst(new MapPropertySource("variables", variables));
        for (String profile : profiles) {
            environment.getPropertySources().addLast(file("application-" + profile + ".properties"));
        }
        environment.getPropertySources().addLast(file("application.properties"));
        return environment;
    }

    private static ResourcePropertySource file(String name) throws IOException {
        return new ResourcePropertySource(new FileSystemResource("src/main/resources/" + name));
    }
}
