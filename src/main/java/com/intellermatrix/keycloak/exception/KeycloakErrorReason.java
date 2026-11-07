package com.intellermatrix.keycloak.exception;

/**
 * Why a {@link KeycloakIntegrationException} was thrown. Carried alongside an
 * {@link org.springframework.http.HttpStatus} so a consumer can branch on the cause without
 * knowing anything about Keycloak's own error shapes.
 */
public enum KeycloakErrorReason {
    /** Keycloak could not be reached, or returned an unexpected error for a request. */
    COMMUNICATION_ERROR,
    /** The requested user does not exist in the managed realm. */
    USER_NOT_FOUND,
    /** The username or email in a registration request is already taken. */
    USER_ALREADY_EXISTS,
    /** The username or password provided for authentication is wrong. */
    INVALID_CREDENTIALS,
    /** The request failed bean validation before reaching Keycloak. */
    INVALID_REQUEST,
    /** An unexpected failure occurred that doesn't fit any other reason. */
    INTERNAL_SERVER_ERROR,
    /** The requested client-level role does not exist in the managed realm. */
    ROLE_NOT_FOUND
}
