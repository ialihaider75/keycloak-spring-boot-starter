package com.intellermatrix.keycloak.auth;

import com.intellermatrix.keycloak.dto.user.UserCreationRequest;
import com.intellermatrix.keycloak.exception.KeycloakErrorReason;
import com.intellermatrix.keycloak.exception.KeycloakIntegrationException;
import com.intellermatrix.keycloak.service.KeyCloakService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServerService {

    private static final String AUTH_SERVER_NAME = "Keycloak";
    private static final String CREDENTIAL_TYPE_PASSWORD = "password";

    private final KeyCloakService keyCloakService;
    private final Validator validator;

    public Optional<UserDetailsResponse> getUserDetailsByUsername(String username) {
        log.info("Fetching user details for username: {} from {}", username, AUTH_SERVER_NAME);
        try {
            var userWithRoles = keyCloakService.getUserWithRolesByUsername(username);
            if (userWithRoles.roles().isEmpty()) {
                log.warn("User: {} exists in {} but has no client role assigned, treating lookup as unresolved",
                        username, AUTH_SERVER_NAME);
                return Optional.empty();
            }
            log.info("Fetched user details for username: {} from {}", username, AUTH_SERVER_NAME);
            var localUserDetailsResponse = UserDetailsResponse.of(userWithRoles.userDetails(), userWithRoles.roles().getFirst());
            return Optional.of(localUserDetailsResponse);
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

    public void createUserInAuthServer(UserRegistrationRequest request) {

        validateUserRegistrationRequest(request);

        log.info("Creating user in {} with username: {}", AUTH_SERVER_NAME, request.username());
        var userCreationRequest = UserCreationRequest.builder()
                .username(request.username())
                .credentials(List.of(UserCreationRequest.CredentialRepresentation.builder()
                        .value(request.password())
                        .type(CREDENTIAL_TYPE_PASSWORD)
                        .temporary(false)
                        .build()))
                .enabled(true)
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .emailVerified(false)
                .build();

        var optionalRegisteredUserId = keyCloakService.createUser(userCreationRequest);

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

        keyCloakService.assignClientRoleToUser(registeredUserId, request.role());

        log.info("Role: {} assigned to user: {} in {}", request.role(), request.username(), AUTH_SERVER_NAME);
    }

    public KeyCloakAuthUserResponse authenticate(KeyCloakAuthUserRequest keyCloakAuthUserRequest) {

        validateKeyCloakAuthUserRequest(keyCloakAuthUserRequest);
        log.info("Authenticating user: {} via {}", keyCloakAuthUserRequest.username(), AUTH_SERVER_NAME);
        var tokenResponse = keyCloakService.getAccessTokenResponseForUsernameAndPasswordCombination(keyCloakAuthUserRequest.username(),
                keyCloakAuthUserRequest.password());
        return tokenResponse.map(accessTokenResponse -> {
                    log.info("Authentication successful for user: {} via {}", keyCloakAuthUserRequest.username(), AUTH_SERVER_NAME);
                    return KeyCloakAuthUserResponse.builder()
                            .accessToken(accessTokenResponse.accessToken())
                            .build();

                })
                .orElseThrow(() -> {
                    log.error("Authentication failed for user: {} via {}", keyCloakAuthUserRequest.username(), AUTH_SERVER_NAME);
                    return new KeycloakIntegrationException(KeycloakErrorReason.INVALID_CREDENTIALS,
                            "Invalid username or password",
                            HttpStatus.UNAUTHORIZED);
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
