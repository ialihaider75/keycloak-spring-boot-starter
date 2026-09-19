package com.intellermatrix.keycloak.exception;

public enum KeycloakErrorReason {
    COMMUNICATION_ERROR,
    USER_NOT_FOUND,
    USER_ALREADY_EXISTS,
    INVALID_CREDENTIALS,
    INVALID_REQUEST,
    INTERNAL_SERVER_ERROR,
    ROLE_NOT_FOUND
}
