package com.intellermatrix.keycloak.auth;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Result of {@link AuthServerService#getUserDetailsByUsername(String)}.
 *
 * @param id               the user's Keycloak user ID
 * @param username         the user's Keycloak username
 * @param email            the user's email address
 * @param firstName        the user's first name
 * @param lastName         the user's last name
 * @param enabled          whether the user's account is enabled in Keycloak
 * @param emailVerified    whether the user's email address is verified in Keycloak
 * @param createdTimestamp when the user was created in Keycloak, as epoch milliseconds
 * @param attributes       the user's custom Keycloak attributes
 * @param requiredActions  any Keycloak required actions still pending for this user
 * @param role             the user's first client-level role in the managed realm
 */
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
