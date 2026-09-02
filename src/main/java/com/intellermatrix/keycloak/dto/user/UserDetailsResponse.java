package com.intellermatrix.keycloak.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record UserDetailsResponse(@JsonProperty("id")
                                  String id,
                                  @JsonProperty("username")
                                  String username,
                                  @JsonProperty("email")
                                  String email,
                                  @JsonProperty("firstName")
                                  String firstName,
                                  @JsonProperty("lastName")
                                  String lastName,
                                  @JsonProperty("enabled")
                                  Boolean enabled,
                                  @JsonProperty("emailVerified")
                                  Boolean emailVerified,
                                  @JsonProperty("createdTimestamp")
                                  Long createdTimestamp,
                                  @JsonProperty("attributes")
                                  Map<String, List<String>> attributes,
                                  @JsonProperty("requiredActions")
                                  List<String> requiredActions) {
}

