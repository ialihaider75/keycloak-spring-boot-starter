package com.intellermatrix.keycloak.config;

import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "external-services.keycloak")
@Builder
public record KeyCloakConfig(Connectivity connectivity, Admin admin, Realm realm) {

    @Builder
    public record Connectivity(String baseUrl, String managementUrl ,int timeoutInMs) {
    }

    @Builder
    public record Admin(String username, String password, String realm, String clientId) {
    }

    @Builder
    public record Realm(String id, String displayName, Client client, Management management) {
        @Builder
        public record Client(String id, String name, String secret, List<String> redirectUris) {

        }

        @Builder
        public record Management(String clientId) {

        }
    }

}
