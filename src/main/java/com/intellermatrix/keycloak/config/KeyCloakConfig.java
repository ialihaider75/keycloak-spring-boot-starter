package com.intellermatrix.keycloak.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "external-services.keycloak")
@Validated
@Builder
public record KeyCloakConfig(@NotNull @Valid Connectivity connectivity,
                             @NotNull @Valid Admin admin,
                             @NotNull @Valid Realm realm) {

    @Builder
    public record Connectivity(@NotBlank String baseUrl,
                               @NotBlank String managementUrl,
                               @Positive int timeoutInMs) {
    }

    @Builder
    public record Admin(@NotBlank String username,
                        @NotBlank String password,
                        @NotBlank String realm,
                        @NotBlank String clientId) {
    }

    @Builder
    public record Realm(@NotBlank String id,
                        @NotBlank String displayName,
                        @NotNull @Valid Client client,
                        @NotNull @Valid Management management,
                        @NotEmpty @Valid List<RoleDefinition> roles) {

        @Builder
        public record Client(@NotBlank String id,
                             @NotBlank String name,
                             @NotBlank String secret,
                             @NotEmpty List<String> redirectUris) {
        }

        @Builder
        public record Management(@NotBlank String clientId) {
        }
    }
}
