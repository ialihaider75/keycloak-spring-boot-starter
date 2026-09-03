package com.intellermatrix.keycloak.auth;

import lombok.Builder;

@Builder
public record KeyCloakAuthUserResponse(String accessToken) {
}
