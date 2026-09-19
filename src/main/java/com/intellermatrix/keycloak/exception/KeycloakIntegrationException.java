package com.intellermatrix.keycloak.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class KeycloakIntegrationException extends RuntimeException {

    private final KeycloakErrorReason reason;
    private final HttpStatus httpStatus;

    public KeycloakIntegrationException(KeycloakErrorReason reason, String message, HttpStatus httpStatus) {
        super(message);
        this.reason = reason;
        this.httpStatus = httpStatus;
    }
}
