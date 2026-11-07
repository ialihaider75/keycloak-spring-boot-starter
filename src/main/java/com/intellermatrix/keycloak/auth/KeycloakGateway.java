package com.intellermatrix.keycloak.auth;

import java.util.Optional;

/**
 * The seam between this library's public API and its internal Keycloak implementation.
 *
 * <p>{@link AuthServerService} depends only on this interface, never on anything under {@code
 * com.intellermatrix.keycloak.internal} directly; {@code internal.service.KeyCloakService}
 * implements it. Consumers call {@link AuthServerService}, not this interface directly — it
 * exists so the public API's own source never needs to import an internal type.
 */
public interface KeycloakGateway {

    /**
     * Creates a user in the managed realm.
     *
     * @param request the new user's details
     * @return the newly created user's Keycloak user ID, or {@link Optional#empty()} if creation
     *         failed
     */
    Optional<String> createUser(UserRegistrationRequest request);

    /**
     * Grants a user the given client-level role in the managed realm.
     *
     * @param userId   the Keycloak user ID to grant the role to
     * @param roleName the client-level role name to grant
     */
    void assignClientRoleToUser(String userId, String roleName);

    /**
     * Looks up a user in the managed realm by exact username match.
     *
     * @param username the Keycloak username to look up
     * @return the user's details enriched with their first client role, or
     *         {@link Optional#empty()} if no such user exists
     */
    Optional<UserDetailsResponse> findUserDetailsByUsername(String username);

    /**
     * Obtains an access token for a user via the password (direct access) grant.
     *
     * @param username the Keycloak username to authenticate
     * @param password the user's password
     * @return the issued access token, or {@link Optional#empty()} if the credentials are invalid
     */
    Optional<String> getAccessToken(String username, String password);
}
