package com.intellermatrix.keycloak.auth;

import java.util.Optional;

public interface KeycloakGateway {

    Optional<String> createUser(UserRegistrationRequest request);

    void assignClientRoleToUser(String userId, String roleName);

    Optional<UserDetailsResponse> findUserDetailsByUsername(String username);

    Optional<String> getAccessToken(String username, String password);
}
