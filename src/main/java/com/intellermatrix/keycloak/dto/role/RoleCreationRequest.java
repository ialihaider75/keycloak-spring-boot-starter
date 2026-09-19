package com.intellermatrix.keycloak.dto.role;

import lombok.Builder;

@Builder
public record RoleCreationRequest(String name,
                                  String description,
                                  Boolean composite,
                                  Boolean clientRole) {
}
