package com.intellermatrix.keycloak.service;

import com.intellermatrix.keycloak.KeycloakIntegrationTestSupport;
import com.intellermatrix.keycloak.dto.user.UserCreationRequest;
import com.intellermatrix.keycloak.exception.KeycloakErrorReason;
import com.intellermatrix.keycloak.exception.KeycloakIntegrationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void shouldReturnExactlyMatchingUser_whenAnotherUsernameSharesTheSamePrefix() {
        var username = "it-exact-" + System.currentTimeMillis();
        var similarUsername = username + "-extra";

        var userId = keyCloakService.createUser(userCreationRequest(username, "Secret@1234")).orElseThrow();
        var similarUserId = keyCloakService.createUser(userCreationRequest(similarUsername, "Secret@1234")).orElseThrow();

        var foundUser = keyCloakService.getUserByUsername(username);

        assertThat(foundUser.username()).isEqualTo(username);
        assertThat(foundUser.id()).isEqualTo(userId).isNotEqualTo(similarUserId);
    }

    @Test
    void shouldReturnUserById_usingClientAccessTokenOnly() {
        var username = "it-by-id-" + System.currentTimeMillis();
        var userId = keyCloakService.createUser(userCreationRequest(username, "Secret@1234")).orElseThrow();

        var user = keyCloakService.getUserById(userId);

        assertThat(user.username()).isEqualTo(username);
    }

    @Test
    void shouldReturnClientRoleDetails_usingClientAccessTokenOnly() {
        var roleDetails = keyCloakService.getClientRoleDetailsByRoleName("CUSTOMER");

        assertThat(roleDetails.name()).isEqualTo("CUSTOMER");
    }

    @Test
    void shouldThrowUserAlreadyExists_whenCreatingTheSameUserTwice() {
        var username = "it-duplicate-" + System.currentTimeMillis();
        var request = userCreationRequest(username, "Secret@1234");

        assertThat(keyCloakService.createUser(request)).isPresent();

        assertThatThrownBy(() -> keyCloakService.createUser(request))
                .isInstanceOf(KeycloakIntegrationException.class)
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.USER_ALREADY_EXISTS);
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

    private UserCreationRequest userCreationRequest(String username, String password) {
        return UserCreationRequest.builder()
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
    }
}
