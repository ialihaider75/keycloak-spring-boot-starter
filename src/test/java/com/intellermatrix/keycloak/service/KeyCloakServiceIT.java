package com.intellermatrix.keycloak.service;

import com.intellermatrix.keycloak.KeycloakIntegrationTestSupport;
import com.intellermatrix.keycloak.dto.user.UserCreationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KeyCloakServiceIT extends KeycloakIntegrationTestSupport {

    @Autowired
    private KeyCloakService keyCloakService;

    @Test
    void shouldGetUpResponse_whenPingingKeyCloak() {
        var response = keyCloakService.pingKeyCloak();

        assertThat(response.status()).isEqualTo("UP");
    }

    @Test
    void shouldReturnAdminAndClientAccessTokens() {
        assertThat(keyCloakService.getAdminAccessToken()).isNotBlank();
        assertThat(keyCloakService.getClientAccessToken()).isNotBlank();
    }

    @Test
    void shouldCreateUserAssignRoleAndAuthenticate_forFullSignupAndLoginFlow() {
        var username = "it-user-" + System.currentTimeMillis();
        var password = "Secret@1234";

        var userCreationRequest = UserCreationRequest.builder()
                .username(username)
                .email(username + "@test.com")
                .emailVerified(true)
                .firstName("Integration")
                .lastName("Test")
                .enabled(true)
                .credentials(List.of(UserCreationRequest.CredentialRepresentation.builder()
                        .type("password")
                        .value(password)
                        .temporary(false)
                        .build()))
                .build();

        var optionalUserId = keyCloakService.createUser(userCreationRequest);
        assertThat(optionalUserId).isPresent();
        assertThat(UUID.fromString(optionalUserId.get())).isNotNull();

        keyCloakService.assignClientRoleToUser(optionalUserId.get(), "CUSTOMER");

        var assignedRoles = keyCloakService.getUserClientRoles(optionalUserId.get());
        assertThat(assignedRoles).extracting("name").contains("CUSTOMER");

        var userByUsername = keyCloakService.getUserByUsername(username);
        assertThat(userByUsername.id()).isEqualTo(optionalUserId.get());

        var accessTokenResponse = keyCloakService.getAccessTokenResponseForUsernameAndPasswordCombination(username, password);
        assertThat(accessTokenResponse).isPresent();
        assertThat(accessTokenResponse.get().accessToken()).isNotBlank();
    }

    @Test
    void shouldReturnEmptyOptional_whenAuthenticatingWithWrongPassword() {
        var username = "it-user-" + System.currentTimeMillis();
        var userCreationRequest = UserCreationRequest.builder()
                .username(username)
                .email(username + "@test.com")
                .emailVerified(true)
                .firstName("Integration")
                .lastName("Test")
                .enabled(true)
                .credentials(List.of(UserCreationRequest.CredentialRepresentation.builder()
                        .type("password")
                        .value("Correct@1234")
                        .temporary(false)
                        .build()))
                .build();

        var optionalUserId = keyCloakService.createUser(userCreationRequest);
        assertThat(optionalUserId).isPresent();

        var result = keyCloakService.getAccessTokenResponseForUsernameAndPasswordCombination(username, "Wrong@1234");

        assertThat(result).isEmpty();
    }
}
