package com.intellermatrix.keycloak.auth;

import com.intellermatrix.keycloak.dto.role.RoleDetailsResponse;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record UserDetailsResponse(String id,
                                  String username,
                                  String email,
                                  String firstName,
                                  String lastName,
                                  Boolean enabled,
                                  Boolean emailVerified,
                                  Long createdTimestamp,
                                  Map<String, List<String>> attributes,
                                  List<String> requiredActions,
                                  String role) {

    public static UserDetailsResponse of(com.intellermatrix.keycloak.dto.user.UserDetailsResponse userDetailsResponse,
                                         RoleDetailsResponse roleDetailsResponse) {
        return UserDetailsResponse.builder()
                .id(userDetailsResponse.id())
                .username(userDetailsResponse.username())
                .email(userDetailsResponse.email())
                .firstName(userDetailsResponse.firstName())
                .lastName(userDetailsResponse.lastName())
                .enabled(userDetailsResponse.enabled())
                .emailVerified(userDetailsResponse.emailVerified())
                .createdTimestamp(userDetailsResponse.createdTimestamp())
                .attributes(userDetailsResponse.attributes())
                .requiredActions(userDetailsResponse.requiredActions())
                .role(roleDetailsResponse.name())
                .build();
    }
}
