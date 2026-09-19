package com.intellermatrix.keycloak.dto.realm;

public record GetRealmResponse(String id,
                               String realm,
                               String displayName) {
}
