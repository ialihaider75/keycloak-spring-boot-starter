package com.intellermatrix.keycloak.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Credentials for {@link AuthServerService#authenticate(KeyCloakAuthUserRequest)}.
 *
 * @param username the Keycloak username to authenticate
 * @param password the user's password
 */
@Builder
public record KeyCloakAuthUserRequest(@NotBlank String username,
                                      @NotBlank String password) {
}
