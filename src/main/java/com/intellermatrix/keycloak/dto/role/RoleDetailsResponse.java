package com.intellermatrix.keycloak.dto.role;


public record RoleDetailsResponse(String id,
                                  String name,
                                  String description,
                                  Boolean composite,
                                  String containerId) {
}
