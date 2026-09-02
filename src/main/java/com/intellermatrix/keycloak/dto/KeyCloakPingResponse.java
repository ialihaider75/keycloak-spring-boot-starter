package com.intellermatrix.keycloak.dto;

import java.util.List;

public record KeyCloakPingResponse(String status,
                                   List<Object> checks) {
}
