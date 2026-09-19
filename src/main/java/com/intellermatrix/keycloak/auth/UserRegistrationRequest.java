package com.intellermatrix.keycloak.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record UserRegistrationRequest(@NotBlank String username,
                                      @NotBlank String password,
                                      @NotBlank String email,
                                      @NotBlank String firstName,
                                      @NotBlank String lastName,
                                      @NotBlank String role) {
}
