package com.intellermatrix.keycloak.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record KeyCloakAuthUserRequest(@NotBlank String username,
                                      @NotBlank String password) {
}
