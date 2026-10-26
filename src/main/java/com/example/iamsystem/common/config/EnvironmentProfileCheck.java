package com.example.iamsystem.common.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stops the startup unless exactly one environment profile is active ({@code local}, {@code dev}, {@code stage},
 * {@code prod}, or {@code test} for the test suite), so nothing runs on an unintended configuration. Except in
 * {@code test}, every setting named in {@code iam.required-settings} must have a value, local included, so a local run
 * exercises the same configuration as a deployment; the error lists all the missing ones with their environment
 * variables. Runs right after the configuration files are loaded, before any bean is created.
 */
public class EnvironmentProfileCheck implements EnvironmentPostProcessor, Ordered {

    static final String REQUIRED_SETTINGS = "iam.required-settings";
    private static final Set<String> ENVIRONMENT_PROFILES = Set.of("local", "dev", "stage", "prod", "test");
    private static final String TEST_PROFILE = "test";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String profile = environmentProfile(environment);
        if (!TEST_PROFILE.equals(profile)) {
            checkRequiredSettings(environment, profile);
        }
    }

    /**
     * After the configuration files, which may activate profiles themselves.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static String environmentProfile(ConfigurableEnvironment environment) {
        List<String> active = Arrays.stream(environment.getActiveProfiles()).filter(ENVIRONMENT_PROFILES::contains).toList();
        if (active.size() != 1) {
            throw new IllegalStateException("Exactly one environment profile must be active (local, dev, stage or prod; "
                    + "set SPRING_PROFILES_ACTIVE), but found " + active);
        }
        return active.getFirst();
    }

    private static void checkRequiredSettings(ConfigurableEnvironment environment, String profile) {
        String[] required = environment.getProperty(REQUIRED_SETTINGS, String[].class, new String[0]);
        List<String> missing = Arrays.stream(required)
                .filter(setting -> isBlank(environment.getProperty(setting)))
                .map(setting -> setting + " (" + environmentVariable(setting) + ")")
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("The '" + profile + "' profile needs these settings: " + String.join(", ", missing));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * The environment variable Spring Boot maps onto the setting: {@code iam.jwt.keys[0].private-key} is
     * {@code IAM_JWT_KEYS_0_PRIVATEKEY}.
     */
    static String environmentVariable(String setting) {
        return setting.replace("-", "").replace("[", ".").replace("]", "").replace('.', '_').toUpperCase(Locale.ROOT);
    }
}
