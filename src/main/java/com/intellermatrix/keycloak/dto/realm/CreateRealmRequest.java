package com.intellermatrix.keycloak.dto.realm;

import lombok.Builder;

@Builder
public record CreateRealmRequest(
        String realm,
        String displayName,
        Boolean enabled
) {}

