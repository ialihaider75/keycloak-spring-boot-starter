package com.intellermatrix.keycloak.internal.dto.realm;

public record GetRealmResponse(String id,
                               String realm,
                               String displayName) {
}
