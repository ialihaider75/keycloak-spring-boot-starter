package com.intellermatrix.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccessTokenResponse(@JsonProperty("access_token")
                                  String accessToken,
                                  @JsonProperty("expires_in")
                                  int expiresIn,
                                  @JsonProperty("refresh_expires_in")
                                  int refreshExpiresIn,
                                  @JsonProperty("refresh_token")
                                  String refreshToken,
                                  @JsonProperty("token_type")
                                  String tokenType,
                                  @JsonProperty("not-before-policy")
                                  int notBeforePolicy,
                                  @JsonProperty("session_state")
                                  String sessionState,
                                  String scope
) {
}
