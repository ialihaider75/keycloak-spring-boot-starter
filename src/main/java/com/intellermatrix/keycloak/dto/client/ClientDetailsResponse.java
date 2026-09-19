package com.intellermatrix.keycloak.dto.client;

import java.util.List;

public record ClientDetailsResponse(
        String id,                    // UUID (important!)
        String clientId,
        String name,
        Boolean enabled,
        Boolean publicClient,
        String secret,
        String protocol,
        List<String> redirectUris,
        Boolean serviceAccountsEnabled,
        Boolean directAccessGrantsEnabled
) {}

