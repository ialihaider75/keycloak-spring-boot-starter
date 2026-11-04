package com.intellermatrix.keycloak.internal.dto.role;

import lombok.Builder;

@Builder
public record RoleCreationRequest(String name,
                                  String description,
                                  Boolean composite,
                                  Boolean clientRole) {
}
