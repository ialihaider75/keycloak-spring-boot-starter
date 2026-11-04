package com.intellermatrix.keycloak.internal.dto.role;

import lombok.Builder;

@Builder
public record RoleAssignmentRequest(String id, String name) {
}
