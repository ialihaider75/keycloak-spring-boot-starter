package com.intellermatrix.keycloak.internal.dto.realm;

import lombok.Builder;

@Builder
public record CreateRealmRequest(
        String realm,
        String displayName,
        Boolean enabled
) {}

