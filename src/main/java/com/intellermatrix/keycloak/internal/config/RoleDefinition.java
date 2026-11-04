package com.intellermatrix.keycloak.internal.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record RoleDefinition(@NotBlank String name,
                             @NotBlank String description) {
}
