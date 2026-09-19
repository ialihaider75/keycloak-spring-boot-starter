package com.intellermatrix.keycloak.service;

import com.intellermatrix.keycloak.dto.role.RoleDetailsResponse;
import com.intellermatrix.keycloak.dto.user.UserDetailsResponse;

import java.util.List;

public record UserWithRoles(UserDetailsResponse userDetails, List<RoleDetailsResponse> roles) {
}
