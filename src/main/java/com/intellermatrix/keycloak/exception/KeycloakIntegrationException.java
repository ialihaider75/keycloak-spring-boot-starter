package com.intellermatrix.keycloak.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Thrown for every failure this library's public API ({@link com.intellermatrix.keycloak.auth.AuthServerService})
 * can produce. Carries a {@link KeycloakErrorReason} and an {@link HttpStatus} so a consumer can
 * map it onto its own API response without knowing anything about Keycloak.
 */
@Getter
public class KeycloakIntegrationException extends RuntimeException {

    /** Why this exception was thrown. */
    private final KeycloakErrorReason reason;
    /** The HTTP status a consumer's own API response should use for this failure. */
    private final HttpStatus httpStatus;

    /**
     * @param reason     why this exception is being thrown
     * @param message    a human-readable description of the failure
     * @param httpStatus the HTTP status a consumer's own API response should use for this failure
     */
    public KeycloakIntegrationException(KeycloakErrorReason reason, String message, HttpStatus httpStatus) {
        super(message);
        this.reason = reason;
        this.httpStatus = httpStatus;
    }
}
