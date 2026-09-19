package com.intellermatrix.keycloak.config;

import com.intellermatrix.keycloak.auth.AuthServerService;
import com.intellermatrix.keycloak.client.KeyCloakClientContext;
import com.intellermatrix.keycloak.initializer.KeyCloakInitializer;
import com.intellermatrix.keycloak.service.KeyCloakService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakAutoConfigurationTest {

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
            "external-services.keycloak.realm.roles[0].description=Customer role",
            "external-services.keycloak.auto-provision=false"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KeycloakAutoConfiguration.class, ValidationAutoConfiguration.class));

    @Test
    void shouldNotRegisterAnyBeans_whenConnectivityBaseUrlIsNotConfigured() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(KeyCloakConfig.class));
    }

    @Test
    void shouldRegisterAllBeansAndBindConfig_whenPropertiesArePresent() {
        contextRunner
                .withPropertyValues(VALID_PROPERTIES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KeyCloakConfig.class);
                    assertThat(context).hasSingleBean(KeyCloakClientContext.class);
                    assertThat(context).hasSingleBean(KeyCloakService.class);
                    assertThat(context).hasSingleBean(AuthServerService.class);
                    assertThat(context).doesNotHaveBean(KeyCloakInitializer.class);

                    var config = context.getBean(KeyCloakConfig.class);
                    assertThat(config.realm().id()).isEqualTo("demo-realm");
                });
    }
}
