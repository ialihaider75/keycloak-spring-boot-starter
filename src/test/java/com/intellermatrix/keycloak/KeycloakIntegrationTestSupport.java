package com.intellermatrix.keycloak;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy;

@SpringBootTest(classes = TestKeycloakApplication.class)
public abstract class KeycloakIntegrationTestSupport {

    protected static final GenericContainer<?> keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:25.0.6")
            .withExposedPorts(8080, 9000)
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("KC_HEALTH_ENABLED", "true")
            .withEnv("KC_METRICS_ENABLED", "true")
            .withCommand("start-dev", "--health-enabled=true", "--metrics-enabled=true")
            .withStartupCheckStrategy(new IsRunningStartupCheckStrategy());

    static {
        keycloak.start();
    }

    @DynamicPropertySource
    static void registerKeycloakProperties(DynamicPropertyRegistry registry) {
        registry.add("external-services.keycloak.connectivity.base-url",
                () -> String.format("http://%s:%d", keycloak.getHost(), keycloak.getMappedPort(8080)));
        registry.add("external-services.keycloak.connectivity.management-url",
                () -> String.format("http://%s:%d", keycloak.getHost(), keycloak.getMappedPort(9000)));
    }
}
