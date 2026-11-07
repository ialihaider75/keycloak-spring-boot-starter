package com.intellermatrix.keycloak.auth;

import com.intellermatrix.keycloak.exception.KeycloakErrorReason;
import com.intellermatrix.keycloak.exception.KeycloakIntegrationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Supported entry point for consumers of this starter.
 *
 * <p>This is the only class consumers should call for Keycloak-backed authentication and user
 * management; everything under {@code com.intellermatrix.keycloak.internal} is plumbing this
 * class uses to implement the methods below and is not meant to be called directly.
 *
 * <p>Every failure surfaces as {@link com.intellermatrix.keycloak.exception.KeycloakIntegrationException},
 * carrying a {@link com.intellermatrix.keycloak.exception.KeycloakErrorReason} and an
 * {@link org.springframework.http.HttpStatus} a consumer can map onto its own API responses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServerService {

    private static final String AUTH_SERVER_NAME = "Keycloak";

    private final KeycloakGateway keycloakGateway;
    private final Validator validator;

    /**
     * Looks up a user in the managed realm by exact username match, enriched with the user's
     * first client role.
     *
     * @param username the Keycloak username to look up
     * @return the user's details, or {@link Optional#empty()} if the user does not exist or has
     *         no client role assigned (a role-less user is treated as an unresolved lookup, not
     *         an error)
     * @throws com.intellermatrix.keycloak.exception.KeycloakIntegrationException if the lookup
     *         fails for a reason other than the user not existing (e.g. Keycloak unreachable)
     */
    public Optional<UserDetailsResponse> getUserDetailsByUsername(String username) {
        log.info("Fetching user details for username: {} from {}", username, AUTH_SERVER_NAME);
        try {
            var optionalUserDetails = keycloakGateway.findUserDetailsByUsername(username);
            if (optionalUserDetails.isEmpty()) {
                log.warn("No user details found for username: {} in {}", username, AUTH_SERVER_NAME);
                return Optional.empty();
            }
            var userDetails = optionalUserDetails.get();
            if (userDetails.role() == null || userDetails.role().isBlank()) {
                log.warn("User: {} exists in {} but has no client role assigned, treating lookup as unresolved",
                        username, AUTH_SERVER_NAME);
                return Optional.empty();
            }
            log.info("Fetched user details for username: {} from {}", username, AUTH_SERVER_NAME);
            return Optional.of(userDetails);
        } catch (KeycloakIntegrationException e) {
            if (e.getReason() != KeycloakErrorReason.USER_NOT_FOUND) {
                log.error("Error while fetching user details for username: {} from {}, reason: {}",
                        username, AUTH_SERVER_NAME, e.getReason());
                throw e;
            }
            log.warn("No user details found for username: {} in {}, reason: {}", username, AUTH_SERVER_NAME, e.getReason());
            return Optional.empty();
        }
    }

    /**
     * Signs a user up in the managed realm and assigns them the requested client role.
     *
     * @param request the new user's details and the single client role to assign; every field
     *                is required (see {@link UserRegistrationRequest})
     * @throws com.intellermatrix.keycloak.exception.KeycloakIntegrationException with
     *         {@code INVALID_REQUEST} if {@code request} fails validation, {@code
     *         USER_ALREADY_EXISTS} if the username or email is already taken, or {@code
     *         COMMUNICATION_ERROR} if user creation otherwise fails
     */
    public void createUserInAuthServer(UserRegistrationRequest request) {

        validateUserRegistrationRequest(request);

        log.info("Creating user in {} with username: {}", AUTH_SERVER_NAME, request.username());

        var optionalRegisteredUserId = keycloakGateway.createUser(request);

        if (optionalRegisteredUserId.isEmpty()) {
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    String.format("Failed to create user in %s", AUTH_SERVER_NAME),
                    HttpStatus.BAD_GATEWAY);
        }

        log.info("User created in {} with username: {}, having userId: {}, now going to assign role {}", AUTH_SERVER_NAME,
                request.username(),
                optionalRegisteredUserId,
                request.role());

        var registeredUserId = optionalRegisteredUserId.get();

        keycloakGateway.assignClientRoleToUser(registeredUserId, request.role());

        log.info("Role: {} assigned to user: {} in {}", request.role(), request.username(), AUTH_SERVER_NAME);
    }

    /**
     * Authenticates a user against the managed realm using the password (direct access) grant.
     *
     * @param keyCloakAuthUserRequest the username and password to authenticate with
     * @return the issued access token
     * @throws com.intellermatrix.keycloak.exception.KeycloakIntegrationException with
     *         {@code INVALID_REQUEST} if {@code keyCloakAuthUserRequest} fails validation, or
     *         {@code INVALID_CREDENTIALS} if the username or password is wrong
     */
    public KeyCloakAuthUserResponse authenticate(KeyCloakAuthUserRequest keyCloakAuthUserRequest) {

        validateKeyCloakAuthUserRequest(keyCloakAuthUserRequest);
        log.info("Authenticating user: {} via {}", keyCloakAuthUserRequest.username(), AUTH_SERVER_NAME);
        var accessToken = keycloakGateway.getAccessToken(keyCloakAuthUserRequest.username(),
                keyCloakAuthUserRequest.password());
        return accessToken.map(token -> {
                    log.info("Authentication successful for user: {} via {}", keyCloakAuthUserRequest.username(), AUTH_SERVER_NAME);
                    return KeyCloakAuthUserResponse.builder()
                            .accessToken(token)
                            .build();

                })
                .orElseThrow(() -> {
                    log.error("Authentication failed for user: {} via {}", keyCloakAuthUserRequest.username(), AUTH_SERVER_NAME);
                    return new KeycloakIntegrationException(KeycloakErrorReason.INVALID_CREDENTIALS,
                            "Invalid username or password", HttpStatus.UNAUTHORIZED);
                });
    }

    private void validateUserRegistrationRequest(UserRegistrationRequest request) {
        var validationErrors = validator.validate(request);
        if (!validationErrors.isEmpty()) {
            log.error("Validation errors during user creation for username: {} in {}: {}", request.username(),
                    AUTH_SERVER_NAME, validationErrors);
            throw new KeycloakIntegrationException(KeycloakErrorReason.INVALID_REQUEST,
                    String.format("UserRegistrationRequest validation failed: %s", validationErrors),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validateKeyCloakAuthUserRequest(KeyCloakAuthUserRequest keyCloakAuthUserRequest) {
        var validationErrors = validator.validate(keyCloakAuthUserRequest);
        if (!validationErrors.isEmpty()) {
            log.error("Validation errors during authentication for user: {} via {}: {}", keyCloakAuthUserRequest.username(),
                    AUTH_SERVER_NAME, validationErrors);
            throw new KeycloakIntegrationException(KeycloakErrorReason.INVALID_REQUEST,
                    String.format("KeyCloakAuthUserRequest validation failed: %s", validationErrors),
                    HttpStatus.BAD_REQUEST);
        }
    }
}
