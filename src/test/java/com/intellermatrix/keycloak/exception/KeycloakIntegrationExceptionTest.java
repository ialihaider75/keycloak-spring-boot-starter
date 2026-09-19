package com.intellermatrix.keycloak.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakIntegrationExceptionTest {

    @Test
    void shouldExposeReasonMessageAndHttpStatus() {
        var exception = new KeycloakIntegrationException(
                KeycloakErrorReason.USER_NOT_FOUND, "User not found with username: john", HttpStatus.NOT_FOUND);

        assertThat(exception.getReason()).isEqualTo(KeycloakErrorReason.USER_NOT_FOUND);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("User not found with username: john");
    }

    @Test
    void shouldBeARuntimeException() {
        var exception = new KeycloakIntegrationException(
                KeycloakErrorReason.COMMUNICATION_ERROR, "Keycloak unreachable", HttpStatus.BAD_GATEWAY);

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
