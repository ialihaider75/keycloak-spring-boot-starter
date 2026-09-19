package com.intellermatrix.keycloak.service;

import com.intellermatrix.keycloak.client.KeyCloakClientContext;
import com.intellermatrix.keycloak.config.KeyCloakConfig;
import com.intellermatrix.keycloak.constant.HttpHeaderConstants;
import com.intellermatrix.keycloak.dto.AccessTokenResponse;
import com.intellermatrix.keycloak.dto.KeyCloakPingResponse;
import com.intellermatrix.keycloak.dto.role.RoleAssignmentRequest;
import com.intellermatrix.keycloak.dto.role.RoleDetailsResponse;
import com.intellermatrix.keycloak.dto.user.UserCreationRequest;
import com.intellermatrix.keycloak.dto.user.UserDetailsResponse;
import com.intellermatrix.keycloak.enums.AccessTokenType;
import com.intellermatrix.keycloak.exception.KeycloakErrorReason;
import com.intellermatrix.keycloak.exception.KeycloakIntegrationException;
import com.intellermatrix.keycloak.exchange.KeyCloakExchangeClient;
import com.intellermatrix.keycloak.preparator.AccessTokenRequestPreparatorFactory;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeyCloakService {

    private final KeyCloakExchangeClient keyCloakManagementExchangeClient;
    private final KeyCloakExchangeClient keyCloakExchangeClient;
    private final KeyCloakConfig keyCloakConfig;
    private final AccessTokenRequestPreparatorFactory preparatorFactory;
    private final KeyCloakClientContext keyCloakClientContext;
    private final Validator validator;

    public KeyCloakPingResponse pingKeyCloak() {
        log.info("Pinging KeyCloak server...");
        var keyCloakPingResponse = keyCloakManagementExchangeClient.pingKeyCloak();
        log.info("Received response from KeyCloak server: {}", keyCloakPingResponse);
        return keyCloakPingResponse;
    }

    public String getAdminAccessToken() {
        log.info("Requesting admin access token from KeyCloak...");
        var adminConfig = keyCloakConfig.admin();
        final MultiValueMap<String, String> requestMap = getAccessTokenRequestMap(
                adminConfig.username(),
                adminConfig.password(),
                adminConfig.clientId(),
                null,
                AccessTokenType.ADMIN
        );
        var accessTokenResponse = getAccessTokenForRealm(adminConfig.realm(), requestMap);
        log.info("Received admin access token from KeyCloak");
        return accessTokenResponse.accessToken();
    }

    public String getClientAccessToken() {
        log.info("Requesting client access token from KeyCloak for clientId: {}", keyCloakConfig.realm().client().id());
        var clientConfig = keyCloakConfig.realm().client();
        final MultiValueMap<String, String> requestMap = getAccessTokenRequestMap(
                null,
                null,
                clientConfig.id(),
                clientConfig.secret(),
                AccessTokenType.CLIENT
        );
        var accessTokenResponse = getAccessTokenForRealm(keyCloakConfig.realm().id(), requestMap);
        log.info("Received client access token from KeyCloak for clientId: {}", clientConfig.id());
        return accessTokenResponse.accessToken();
    }

    public Optional<AccessTokenResponse> getAccessTokenResponseForUsernameAndPasswordCombination(String username, String password) {
        validateUsernameAndPassword(username, password);
        log.info("Getting access token for user: {} from KeyCloak", username);
        var clientConfig = keyCloakConfig.realm().client();
        final MultiValueMap<String, String> requestMap = getAccessTokenRequestMap(
                username,
                password,
                clientConfig.id(),
                clientConfig.secret(),
                AccessTokenType.USER
        );
        try {
            var accessTokenResponse = getAccessTokenForRealm(keyCloakConfig.realm().id(), requestMap);
            log.info("Received access token for user: {} from KeyCloak", username);
            return Optional.of(accessTokenResponse);
        } catch (KeycloakIntegrationException e) {
            if (HttpStatus.UNAUTHORIZED.equals(e.getHttpStatus()) || HttpStatus.BAD_REQUEST.equals(e.getHttpStatus())) {
                log.warn("Authentication failed for user: {} with KeyCloak - invalid credentials", username);
                return Optional.empty();
            }
            log.error("Error while authenticating user: {} with KeyCloak", username, e);
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    "Error while authenticating user with KeyCloak",
                    HttpStatus.BAD_GATEWAY);
        } catch (Exception e) {
            log.error("Error while authenticating user: {} with KeyCloak", username, e);
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    "Error while authenticating user with KeyCloak",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    public Optional<String> createUser(UserCreationRequest userRequest) {

        validateUserCreationRequest(userRequest);

        var accessToken = getClientAccessToken();
        var bearerToken = String.format(HttpHeaderConstants.BEARER_TOKEN_FORMAT, accessToken);

        log.info("Creating user in KeyCloak realm: {} with username: {}", keyCloakConfig.realm().id(), userRequest.username());

        try {
            var response = keyCloakExchangeClient.createUser(
                    bearerToken,
                    keyCloakConfig.realm().id(),
                    userRequest
            );
            var locationHeader = response.getHeaders().getLocation();
            if (Objects.nonNull(locationHeader)) {
                log.info("User created successfully: {}", userRequest.username());
                var userId = StringUtils.substringAfterLast(locationHeader.getPath(), "/");
                log.info("New user ID for created user on keycloak : {}", userId);
                return Optional.of(userId);
            }

            log.warn("KeyCloak create-user response for username: {} had no Location header; " +
                    "looking up the user to confirm whether it was actually created", userRequest.username());
            return fallBackLookUpOfUser(userRequest, bearerToken);

        } catch (KeycloakIntegrationException e) {
            if (HttpStatus.CONFLICT.equals(e.getHttpStatus())) {
                log.error("User already exists in KeyCloak with username: {} or email: {}",
                        userRequest.username(), userRequest.email());
                throw new KeycloakIntegrationException(KeycloakErrorReason.USER_ALREADY_EXISTS,
                        String.format("User already exists in KeyCloak with username: %s", userRequest.username()),
                        HttpStatus.CONFLICT);
            }
            log.error("Error while creating user: {}", userRequest.username(), e);
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    "Error while creating user in KeyCloak",
                    HttpStatus.BAD_GATEWAY);
        } catch (Exception e) {
            log.error("Error while creating user: {}", userRequest.username(), e);
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    "Error while creating user in KeyCloak",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private Optional<String> fallBackLookUpOfUser(UserCreationRequest userRequest, String bearerToken) {
        try {
            var createdUser = getUserByUsername(userRequest.username(), bearerToken);
            log.info("Confirmed user was created despite missing Location header: {}", userRequest.username());
            return Optional.of(createdUser.id());
        } catch (KeycloakIntegrationException lookupException) {
            if (KeycloakErrorReason.USER_NOT_FOUND.equals(lookupException.getReason())) {
                log.error("Failed to create user: {}", userRequest.username());
                return Optional.empty();
            }
            throw lookupException;
        }
    }

    public RoleDetailsResponse getClientRoleDetailsByRoleName(String roleName) {
        var clientAccessToken = getClientAccessToken();
        var bearerToken = String.format(HttpHeaderConstants.BEARER_TOKEN_FORMAT, clientAccessToken);
        return getClientRoleDetailsByRoleName(roleName, bearerToken);
    }

    private RoleDetailsResponse getClientRoleDetailsByRoleName(String roleName, String bearerToken) {
        try {
            log.info("Fetching role details for role: {} in client: {}",
                    roleName,
                    keyCloakConfig.realm().client().id());
            var roleDetails = keyCloakExchangeClient.getClientRoleDetailsByRoleName(
                    bearerToken,
                    keyCloakConfig.realm().id(),
                    keyCloakClientContext.getClientUuid(),
                    roleName
            );

            log.info("Fetched role details for role: {}: {}", roleName, roleDetails);
            return roleDetails;
        } catch (KeycloakIntegrationException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getHttpStatus())) {
                log.warn("Role '{}' not found on client: {}", roleName, keyCloakConfig.realm().client().id());
                throw new KeycloakIntegrationException(KeycloakErrorReason.ROLE_NOT_FOUND,
                        "Role not found: " + roleName,
                        HttpStatus.NOT_FOUND);
            }
            log.error("Error while fetching role details for role: {}", roleName, e);
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    "Error while fetching role details from KeyCloak",
                    HttpStatus.BAD_GATEWAY);
        } catch (Exception e) {
            log.error("Error while fetching role details for role: {}", roleName, e);
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    "Error while fetching role details from KeyCloak",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    public void assignClientRoleToUser(String userId, String roleName) {
        var clientAccessToken = getClientAccessToken();
        var bearerToken = String.format(HttpHeaderConstants.BEARER_TOKEN_FORMAT, clientAccessToken);
        log.info("Assigning role: {} to user: {} in client: {}",
                roleName,
                userId,
                keyCloakConfig.realm().client().id());
        try {
            var roleDetails = getClientRoleDetailsByRoleName(roleName, bearerToken);

            var roleAssignmentRequest = RoleAssignmentRequest.builder()
                    .id(roleDetails.id())
                    .name(roleDetails.name())
                    .build();

            keyCloakExchangeClient.assignClientRoleToUser(
                    bearerToken,
                    keyCloakConfig.realm().id(),
                    userId,
                    keyCloakClientContext.getClientUuid(),
                    List.of(roleAssignmentRequest)
            );
            log.info("Assigned role: {} to user: {} successfully", roleName, userId);
        } catch (KeycloakIntegrationException e) {
            if (KeycloakErrorReason.ROLE_NOT_FOUND.equals(e.getReason())) {
                log.error("Cannot assign role: {} to user: {} - role does not exist", roleName, userId);
                throw e;
            }
            log.error("Error while assigning role: {} to user: {}", roleName, userId, e);
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    "Error while assigning role to user in KeyCloak",
                    HttpStatus.BAD_GATEWAY);
        } catch (Exception e) {
            log.error("Error while assigning role: {} to user: {}", roleName, userId, e);
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    "Error while assigning role to user in KeyCloak",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    public List<RoleDetailsResponse> getUserClientRoles(String userId) {
        var clientAccessToken = getClientAccessToken();
        var bearerToken = String.format(HttpHeaderConstants.BEARER_TOKEN_FORMAT, clientAccessToken);
        return getUserClientRoles(userId, bearerToken);
    }

    private List<RoleDetailsResponse> getUserClientRoles(String userId, String bearerToken) {
        log.info("Fetching assigned role details for user: {} in client: {}",
                userId,
                keyCloakConfig.realm().client().id());
        try {
            var assignedRoles = keyCloakExchangeClient.getClientRolesForUser(
                    bearerToken,
                    keyCloakConfig.realm().id(),
                    userId,
                    keyCloakClientContext.getClientUuid()
            );
            log.info("Fetched assigned roles for user: {}: {}", userId, assignedRoles);
            return assignedRoles;
        } catch (Exception e) {
            log.error("Error while fetching assigned roles for user: {}", userId, e);
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    "Error while fetching assigned roles from KeyCloak",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    public UserDetailsResponse getUserByUsername(String username) {
        var clientAccessToken = getClientAccessToken();
        var bearerToken = String.format(HttpHeaderConstants.BEARER_TOKEN_FORMAT, clientAccessToken);
        return getUserByUsername(username, bearerToken);
    }

    private UserDetailsResponse getUserByUsername(String username, String bearerToken) {
        log.info("Fetching user details for username: {} from KeyCloak realm: {}",
                username, keyCloakConfig.realm().id());

        List<UserDetailsResponse> users;
        try {
            users = keyCloakExchangeClient.getUsersByUsername(
                    bearerToken,
                    keyCloakConfig.realm().id(),
                    username,
                    true
            );
        } catch (Exception e) {
            log.error("Error while fetching user details for username: {}", username, e);
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    "Error while fetching user details from KeyCloak",
                    HttpStatus.BAD_GATEWAY);
        }

        if (users == null || users.isEmpty()) {
            log.error("User not found with username: {}", username);
            throw new KeycloakIntegrationException(KeycloakErrorReason.USER_NOT_FOUND,
                    "User not found with username: " + username,
                    HttpStatus.NOT_FOUND);
        }

        log.info("User details retrieved successfully for username: {}", username);
        return users.getFirst();
    }

    public UserWithRoles getUserWithRolesByUsername(String username) {
        var clientAccessToken = getClientAccessToken();
        var bearerToken = String.format(HttpHeaderConstants.BEARER_TOKEN_FORMAT, clientAccessToken);
        var userDetails = getUserByUsername(username, bearerToken);
        var roles = getUserClientRoles(userDetails.id(), bearerToken);
        return new UserWithRoles(userDetails, roles);
    }

    public UserDetailsResponse getUserById(String userId) {
        var clientAccessToken = getClientAccessToken();
        var bearerToken = String.format(HttpHeaderConstants.BEARER_TOKEN_FORMAT, clientAccessToken);

        log.info("Fetching user details for userId: {} from KeyCloak realm: {}",
                userId, keyCloakConfig.realm().id());

        try {
            UserDetailsResponse user = keyCloakExchangeClient.getUserById(
                    bearerToken,
                    keyCloakConfig.realm().id(),
                    userId
            );
            log.info("User details retrieved successfully for userId: {}", userId);
            return user;
        } catch (KeycloakIntegrationException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getHttpStatus())) {
                log.error("User not found with userId: {}", userId);
                throw new KeycloakIntegrationException(KeycloakErrorReason.USER_NOT_FOUND,
                        "User not found with userId: " + userId,
                        HttpStatus.NOT_FOUND);
            }
            log.error("Error while fetching user details for userId: {}", userId, e);
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    "Error while fetching user details from KeyCloak",
                    HttpStatus.BAD_GATEWAY);
        } catch (Exception e) {
            log.error("Error while fetching user details for userId: {}", userId, e);
            throw new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                    "Error while fetching user details from KeyCloak",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private AccessTokenResponse getAccessTokenForRealm(String realm, MultiValueMap<String, String> request) {
        log.info("Requesting access token for realm: {}", realm);
        var accessTokenResponse = keyCloakExchangeClient.getAccessTokenForRealm(realm, request);
        log.info("Received access token response for realm: {}", realm);
        return accessTokenResponse;
    }

    private MultiValueMap<String, String> getAccessTokenRequestMap(String username,
                                                                    String password,
                                                                    String clientId,
                                                                    String clientSecret,
                                                                    AccessTokenType type) {
        return preparatorFactory.getPreparator(type)
                .prepareAccessTokenRequest(username, password, clientId, clientSecret);
    }

    private void validateUserCreationRequest(UserCreationRequest userRequest) {
        var validationErrors = validator.validate(userRequest);
        if (!validationErrors.isEmpty()) {
            log.error("UserCreationRequest validation failed: {}", validationErrors);
            throw new KeycloakIntegrationException(KeycloakErrorReason.INVALID_REQUEST,
                    String.format("UserCreationRequest validation failed: %s", validationErrors),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validateUsernameAndPassword(String username, String password) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            log.error("Username or password cannot be blank");
            throw new KeycloakIntegrationException(KeycloakErrorReason.INVALID_REQUEST,
                    "Username or password cannot be blank",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
