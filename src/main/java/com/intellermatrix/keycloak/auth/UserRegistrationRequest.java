package com.intellermatrix.keycloak.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record UserRegistrationRequest(@NotBlank String username,
                                      @NotBlank String password,
                                      @NotBlank String email,
                                      @NotBlank String firstName,
                                      @NotBlank String lastName,
                                      @NotNull String role) {
}
