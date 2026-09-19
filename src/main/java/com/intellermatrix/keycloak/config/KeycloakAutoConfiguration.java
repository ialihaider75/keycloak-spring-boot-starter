package com.intellermatrix.keycloak.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ConditionalOnProperty(prefix = "external-services.keycloak.connectivity", name = "base-url")
@EnableConfigurationProperties(KeyCloakConfig.class)
@ComponentScan(basePackages = "com.intellermatrix.keycloak")
public class KeycloakAutoConfiguration {
}
