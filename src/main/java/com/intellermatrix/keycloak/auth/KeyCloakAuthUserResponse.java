package com.intellermatrix.keycloak.auth;

import lombok.Builder;

/**
 * Result of a successful {@link AuthServerService#authenticate(KeyCloakAuthUserRequest)} call.
 *
 * @param accessToken the access token issued by Keycloak for the authenticated user
 */
@Builder
public record KeyCloakAuthUserResponse(String accessToken) {
}
