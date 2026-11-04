package com.intellermatrix.keycloak.internal.dto.role;


public record RoleDetailsResponse(String id,
                                  String name,
                                  String description,
                                  Boolean composite,
                                  String containerId) {
}
