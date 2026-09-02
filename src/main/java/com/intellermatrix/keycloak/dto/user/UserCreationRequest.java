package com.intellermatrix.keycloak.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserCreationRequest(@NotBlank
                                  String username,
                                  @NotNull
                                  Boolean enabled,
                                  @NotBlank
                                  String email,
                                  @NotNull
                                  Boolean emailVerified,
                                  @NotBlank
                                  String firstName,
                                  @NotBlank
                                  String lastName,
                                  @NotEmpty
                                  @Valid
                                  List<CredentialRepresentation> credentials) {

    @Builder
    public record CredentialRepresentation( @NotBlank
                                            String type,
                                            @NotBlank
                                            String value,
                                            @NotNull
                                            Boolean temporary) {
    }
}

