package com.intellermatrix.keycloak.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * A new user's details for {@link AuthServerService#createUserInAuthServer(UserRegistrationRequest)}.
 * All fields are required.
 *
 * @param username  the username to register in the managed realm
 * @param password  the user's initial password
 * @param email     the user's email address
 * @param firstName the user's first name
 * @param lastName  the user's last name
 * @param role      the single client-level role to assign to the new user; the role must
 *                  already be configured in {@code external-services.keycloak.realm.roles}
 */
@Builder
public record UserRegistrationRequest(@NotBlank String username,
                                      @NotBlank String password,
                                      @NotBlank String email,
                                      @NotBlank String firstName,
                                      @NotBlank String lastName,
                                      @NotBlank String role) {
}
