package com.intellermatrix.keycloak.internal.dto.serviceaccount;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ServiceAccountUserResponse(@JsonProperty("id")
                                         String id,
                                         @JsonProperty("username")
                                         String serviceAccountUsername,
                                         @JsonProperty("enabled")
                                         boolean enabled) {
}
