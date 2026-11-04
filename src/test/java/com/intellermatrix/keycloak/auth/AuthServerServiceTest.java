package com.intellermatrix.keycloak.auth;

import com.intellermatrix.keycloak.exception.KeycloakErrorReason;
import com.intellermatrix.keycloak.exception.KeycloakIntegrationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServerServiceTest {

    private final KeycloakGateway keycloakGateway = mock(KeycloakGateway.class);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final AuthServerService authServerService = new AuthServerService(keycloakGateway, validator);

    @Test
    void shouldThrowKeycloakIntegrationException_whenRegistrationRequestIsMissingUsername() {
        var request = UserRegistrationRequest.builder()
                .password("Secret@123")
                .email("john@test.com")
                .firstName("John")
                .lastName("Doe")
                .role("CUSTOMER")
                .build();

        assertThatThrownBy(() -> authServerService.createUserInAuthServer(request))
                .isInstanceOf(KeycloakIntegrationException.class)
                .hasMessageContaining("UserRegistrationRequest validation failed")
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.INVALID_REQUEST);
    }

    @Test
    void shouldThrowKeycloakIntegrationException_whenRegistrationRequestHasBlankRole() {
        var request = UserRegistrationRequest.builder()
                .username("john")
                .password("Secret@123")
                .email("john@test.com")
                .firstName("John")
                .lastName("Doe")
                .role("")
                .build();

        assertThatThrownBy(() -> authServerService.createUserInAuthServer(request))
                .isInstanceOf(KeycloakIntegrationException.class)
                .hasMessageContaining("UserRegistrationRequest validation failed")
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.INVALID_REQUEST);
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

        when(keycloakGateway.createUser(any(UserRegistrationRequest.class))).thenReturn(Optional.of("user-id-123"));

        authServerService.createUserInAuthServer(request);

        verify(keycloakGateway).assignClientRoleToUser("user-id-123", "CUSTOMER");
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

        when(keycloakGateway.createUser(any(UserRegistrationRequest.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authServerService.createUserInAuthServer(request))
                .isInstanceOf(KeycloakIntegrationException.class)
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.COMMUNICATION_ERROR);
    }

    @Test
    void shouldReturnAccessToken_whenAuthenticationSucceeds() {
        var request = KeyCloakAuthUserRequest.builder().username("john").password("Secret@123").build();
        when(keycloakGateway.getAccessToken("john", "Secret@123")).thenReturn(Optional.of("access-token"));

        var response = authServerService.authenticate(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void shouldThrowKeycloakIntegrationException_whenAuthenticationFails() {
        var request = KeyCloakAuthUserRequest.builder().username("john").password("wrong-password").build();
        when(keycloakGateway.getAccessToken("john", "wrong-password")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authServerService.authenticate(request))
                .isInstanceOf(KeycloakIntegrationException.class)
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.INVALID_CREDENTIALS);
    }

    @Test
    void shouldReturnUserDetailsWithRole_whenUserFoundByUsername() {
        var userDetails = UserDetailsResponse.builder()
                .id("user-id-123")
                .username("john")
                .role("CUSTOMER")
                .build();
        when(keycloakGateway.findUserDetailsByUsername("john")).thenReturn(Optional.of(userDetails));

        var result = authServerService.getUserDetailsByUsername("john");

        assertThat(result).isPresent();
        assertThat(result.get().role()).isEqualTo("CUSTOMER");
    }

    @Test
    void shouldReturnEmptyOptional_whenUserHasNoClientRoleAssigned() {
        var userDetails = UserDetailsResponse.builder()
                .id("user-id-123")
                .username("john")
                .role(null)
                .build();
        when(keycloakGateway.findUserDetailsByUsername("john")).thenReturn(Optional.of(userDetails));

        var result = authServerService.getUserDetailsByUsername("john");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldPropagateUnexpectedRuntimeException_whenUserLookupFailsUnexpectedly() {
        when(keycloakGateway.findUserDetailsByUsername("john")).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> authServerService.getUserDetailsByUsername("john"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bug");
    }

    @Test
    void shouldPropagateException_whenClientRolesLookupFailsWithNonNotFoundReason() {
        var communicationError = new KeycloakIntegrationException(KeycloakErrorReason.COMMUNICATION_ERROR,
                "KeyCloak is unavailable", org.springframework.http.HttpStatus.BAD_GATEWAY);
        when(keycloakGateway.findUserDetailsByUsername("john")).thenThrow(communicationError);

        assertThatThrownBy(() -> authServerService.getUserDetailsByUsername("john"))
                .isSameAs(communicationError);
    }

    @Test
    void shouldPropagateUserAlreadyExists_whenKeyCloakReportsConflictOnCreate() {
        var request = UserRegistrationRequest.builder()
                .username("john")
                .password("Secret@123")
                .email("john@test.com")
                .firstName("John")
                .lastName("Doe")
                .role("CUSTOMER")
                .build();

        when(keycloakGateway.createUser(any(UserRegistrationRequest.class)))
                .thenThrow(new KeycloakIntegrationException(KeycloakErrorReason.USER_ALREADY_EXISTS,
                        "already exists", org.springframework.http.HttpStatus.CONFLICT));

        assertThatThrownBy(() -> authServerService.createUserInAuthServer(request))
                .isInstanceOf(KeycloakIntegrationException.class)
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.USER_ALREADY_EXISTS);
    }

    @Test
    void shouldReturnEmptyOptional_whenUserNotFoundByUsername() {
        when(keycloakGateway.findUserDetailsByUsername("missing")).thenThrow(
                new KeycloakIntegrationException(KeycloakErrorReason.USER_NOT_FOUND, "not found", org.springframework.http.HttpStatus.NOT_FOUND));

        var result = authServerService.getUserDetailsByUsername("missing");

        assertThat(result).isEmpty();
    }
}
