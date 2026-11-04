package com.intellermatrix.keycloak.auth;

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
}
