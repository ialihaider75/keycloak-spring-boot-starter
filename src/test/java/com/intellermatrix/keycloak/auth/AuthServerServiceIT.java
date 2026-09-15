package com.intellermatrix.keycloak.auth;

import com.intellermatrix.keycloak.KeycloakIntegrationTestSupport;
import com.intellermatrix.keycloak.exception.KeycloakIntegrationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServerServiceIT extends KeycloakIntegrationTestSupport {

    @Autowired
    private AuthServerService authServerService;

    @Test
    void shouldRegisterUserAndAllowLogin_forFullSignupAndLoginFlow() {
        var username = "it-auth-user-" + System.currentTimeMillis();
        var password = "Secret@1234";

        var registrationRequest = UserRegistrationRequest.builder()
                .username(username)
                .password(password)
                .email(username + "@test.com")
                .firstName("Integration")
                .lastName("Test")
                .role("CUSTOMER")
                .build();

        authServerService.createUserInAuthServer(registrationRequest);

        var authResponse = authServerService.authenticate(KeyCloakAuthUserRequest.builder()
                .username(username)
                .password(password)
                .build());

        assertThat(authResponse.accessToken()).isNotBlank();

        var userDetails = authServerService.getUserDetailsByUsername(username);
        assertThat(userDetails).isPresent();
        assertThat(userDetails.get().role()).isEqualTo("CUSTOMER");
    }

    @Test
    void shouldThrowKeycloakIntegrationException_whenAuthenticatingWithWrongPassword() {
        var username = "it-auth-user-" + System.currentTimeMillis();
        var registrationRequest = UserRegistrationRequest.builder()
                .username(username)
                .password("Correct@1234")
                .email(username + "@test.com")
                .firstName("Integration")
                .lastName("Test")
                .role("PHARMACY")
                .build();

        authServerService.createUserInAuthServer(registrationRequest);

        assertThatThrownBy(() -> authServerService.authenticate(KeyCloakAuthUserRequest.builder()
                .username(username)
                .password("Wrong@1234")
                .build()))
                .isInstanceOf(KeycloakIntegrationException.class);
    }
}
