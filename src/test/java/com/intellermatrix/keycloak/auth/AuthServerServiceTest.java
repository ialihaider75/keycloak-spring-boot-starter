package com.intellermatrix.keycloak.auth;

import com.intellermatrix.keycloak.dto.AccessTokenResponse;
import com.intellermatrix.keycloak.dto.role.RoleDetailsResponse;
import com.intellermatrix.keycloak.dto.user.UserCreationRequest;
import com.intellermatrix.keycloak.exception.KeycloakErrorReason;
import com.intellermatrix.keycloak.exception.KeycloakIntegrationException;
import com.intellermatrix.keycloak.service.KeyCloakService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServerServiceTest {

    private final KeyCloakService keyCloakService = mock(KeyCloakService.class);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final AuthServerService authServerService = new AuthServerService(keyCloakService, validator);

    @Test
    void shouldThrowRuntimeException_whenRegistrationRequestIsMissingUsername() {
        var request = UserRegistrationRequest.builder()
                .password("Secret@123")
                .email("john@test.com")
                .firstName("John")
                .lastName("Doe")
                .role("CUSTOMER")
                .build();

        assertThatThrownBy(() -> authServerService.createUserInAuthServer(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("UserRegistrationRequest validation failed");
    }

    @Test
    void shouldCreateUserAndAssignRole_whenRegistrationRequestIsValid() {
        var request = UserRegistrationRequest.builder()
                .username("john")
                .password("Secret@123")
                .email("john@test.com")
                .firstName("John")
                .lastName("Doe")
                .role("CUSTOMER")
                .build();

        when(keyCloakService.createUser(any(UserCreationRequest.class))).thenReturn(Optional.of("user-id-123"));

        authServerService.createUserInAuthServer(request);

        verify(keyCloakService).assignClientRoleToUser("user-id-123", "CUSTOMER");
    }

    @Test
    void shouldThrowKeycloakIntegrationException_whenUserCreationFailsInKeyCloak() {
        var request = UserRegistrationRequest.builder()
                .username("john")
                .password("Secret@123")
                .email("john@test.com")
                .firstName("John")
                .lastName("Doe")
                .role("CUSTOMER")
                .build();

        when(keyCloakService.createUser(any(UserCreationRequest.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authServerService.createUserInAuthServer(request))
                .isInstanceOf(KeycloakIntegrationException.class)
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.COMMUNICATION_ERROR);
    }

    @Test
    void shouldReturnAccessToken_whenAuthenticationSucceeds() {
        var request = KeyCloakAuthUserRequest.builder().username("john").password("Secret@123").build();
        var tokenResponse = new AccessTokenResponse("access-token", 300, 1800, "refresh-token", "Bearer", 0, "session", "openid");
        when(keyCloakService.getAccessTokenResponseForUsernameAndPasswordCombination("john", "Secret@123"))
                .thenReturn(Optional.of(tokenResponse));

        var response = authServerService.authenticate(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void shouldThrowKeycloakIntegrationException_whenAuthenticationFails() {
        var request = KeyCloakAuthUserRequest.builder().username("john").password("wrong-password").build();
        when(keyCloakService.getAccessTokenResponseForUsernameAndPasswordCombination("john", "wrong-password"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authServerService.authenticate(request))
                .isInstanceOf(KeycloakIntegrationException.class)
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.INVALID_CREDENTIALS);
    }

    @Test
    void shouldReturnUserDetailsWithRole_whenUserFoundByUsername() {
        var rawUserDetails = new com.intellermatrix.keycloak.dto.user.UserDetailsResponse(
                "user-id-123", "john", "john@test.com", "John", "Doe", true, true, 1234L, null, null);
        when(keyCloakService.getUserByUsername("john")).thenReturn(rawUserDetails);
        when(keyCloakService.getUserClientRoles("user-id-123"))
                .thenReturn(List.of(new RoleDetailsResponse("role-id", "CUSTOMER", "Customer role", false, "client-uuid")));

        var result = authServerService.getUserDetailsByUsername("john");

        assertThat(result).isPresent();
        assertThat(result.get().role()).isEqualTo("CUSTOMER");
    }

    @Test
    void shouldReturnEmptyOptional_whenUserNotFoundByUsername() {
        when(keyCloakService.getUserByUsername("missing")).thenThrow(
                new KeycloakIntegrationException(KeycloakErrorReason.USER_NOT_FOUND, "not found", org.springframework.http.HttpStatus.NOT_FOUND));

        var result = authServerService.getUserDetailsByUsername("missing");

        assertThat(result).isEmpty();
    }
}
