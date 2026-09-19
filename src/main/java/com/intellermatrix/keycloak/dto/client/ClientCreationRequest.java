package com.intellermatrix.keycloak.dto.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.intellermatrix.keycloak.enums.ClientProtocols;
import lombok.Builder;

import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientCreationRequest(String clientId,
                                    String name,
                                    Boolean enabled,
                                    ClientProtocols protocol,
                                    Boolean publicClient,
                                    String secret,
                                    List<String> redirectUris,
                                    Boolean standardFlowEnabled,
                                    Boolean directAccessGrantsEnabled,
                                    Boolean serviceAccountsEnabled
) {
}
