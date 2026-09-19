package com.intellermatrix.keycloak.dto.role;

import lombok.Builder;

@Builder
public record RoleAssignmentRequest(String id, String name) {
}
