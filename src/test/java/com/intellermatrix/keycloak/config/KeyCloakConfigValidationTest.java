package com.intellermatrix.keycloak.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class KeyCloakConfigValidationTest {

    private static final String[] VALID_PROPERTIES = {
            "external-services.keycloak.connectivity.base-url=http://localhost:8080",
            "external-services.keycloak.connectivity.management-url=http://localhost:9000",
            "external-services.keycloak.connectivity.timeout-in-ms=3000",
            "external-services.keycloak.admin.username=admin",
            "external-services.keycloak.admin.password=admin",
            "external-services.keycloak.admin.realm=master",
            "external-services.keycloak.admin.client-id=admin-cli",
            "external-services.keycloak.realm.id=demo-realm",
            "external-services.keycloak.realm.display-name=Demo Realm",
            "external-services.keycloak.realm.client.id=demo-client",
            "external-services.keycloak.realm.client.name=Demo Client",
            "external-services.keycloak.realm.client.secret=secret",
            "external-services.keycloak.realm.client.redirect-uris[0]=http://localhost:3000/*",
            "external-services.keycloak.realm.management.client-id=realm-management",
            "external-services.keycloak.realm.roles[0].name=CUSTOMER",
            "external-services.keycloak.realm.roles[0].description=Customer role"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldBindConfig_whenAllRequiredPropertiesArePresent() {
        contextRunner.withPropertyValues(VALID_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var config = context.getBean(KeyCloakConfig.class);
                    assertThat(config.realm().id()).isEqualTo("demo-realm");
                    assertThat(config.realm().roles()).hasSize(1);
                    assertThat(config.realm().roles().getFirst().name()).isEqualTo("CUSTOMER");
                });
    }

    @Test
    void shouldFailToStart_whenConnectivityBaseUrlIsMissing() {
        var properties = withoutPropertiesStartingWith("external-services.keycloak.connectivity.base-url");

        contextRunner.withPropertyValues(properties)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailToStart_whenRealmRolesListIsMissing() {
        var properties = withoutPropertiesStartingWith("external-services.keycloak.realm.roles");

        contextRunner.withPropertyValues(properties)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailToStart_whenAdminUsernameIsMissing() {
        var properties = withoutPropertiesStartingWith("external-services.keycloak.admin.username");

        contextRunner.withPropertyValues(properties)
                .run(context -> assertThat(context).hasFailed());
    }

    private String[] withoutPropertiesStartingWith(String prefix) {
        return Arrays.stream(VALID_PROPERTIES)
                .filter(property -> !property.startsWith(prefix))
                .toArray(String[]::new);
    }

    @Configuration
    @EnableConfigurationProperties(KeyCloakConfig.class)
    static class TestConfig {
    }
}
