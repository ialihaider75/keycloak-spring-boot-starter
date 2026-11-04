package com.intellermatrix.keycloak.internal.service;

import com.intellermatrix.keycloak.internal.dto.role.RoleDetailsResponse;
import com.intellermatrix.keycloak.internal.dto.user.UserDetailsResponse;

import java.util.List;

public record UserWithRoles(UserDetailsResponse userDetails, List<RoleDetailsResponse> roles) {
}
