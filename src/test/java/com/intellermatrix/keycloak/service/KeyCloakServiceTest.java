package com.intellermatrix.keycloak.service;

import com.intellermatrix.keycloak.client.KeyCloakClientContext;
import com.intellermatrix.keycloak.config.KeyCloakConfig;
import com.intellermatrix.keycloak.config.RoleDefinition;
import com.intellermatrix.keycloak.dto.AccessTokenResponse;
import com.intellermatrix.keycloak.dto.KeyCloakPingResponse;
import com.intellermatrix.keycloak.dto.role.RoleAssignmentRequest;
import com.intellermatrix.keycloak.dto.role.RoleDetailsResponse;
import com.intellermatrix.keycloak.dto.user.UserCreationRequest;
import com.intellermatrix.keycloak.dto.user.UserDetailsResponse;
import com.intellermatrix.keycloak.exception.KeycloakErrorReason;
import com.intellermatrix.keycloak.exception.KeycloakIntegrationException;
import com.intellermatrix.keycloak.exchange.KeyCloakExchangeClient;
import com.intellermatrix.keycloak.preparator.AccessTokenRequestPreparatorFactory;
import com.intellermatrix.keycloak.preparator.AdminAccessTokenRequestPreparator;
import com.intellermatrix.keycloak.preparator.ClientAccessTokenRequestPreparator;
import com.intellermatrix.keycloak.preparator.UserAccessTokenRequestPreparator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeyCloakServiceTest {

    private final KeyCloakExchangeClient keyCloakManagementExchangeClient = mock(KeyCloakExchangeClient.class);
    private final KeyCloakExchangeClient keyCloakExchangeClient = mock(KeyCloakExchangeClient.class);
    private final KeyCloakClientContext keyCloakClientContext = mock(KeyCloakClientContext.class);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private KeyCloakConfig keyCloakConfig;
    private KeyCloakService keyCloakService;

    @BeforeEach
    void setUp() {
        keyCloakConfig = KeyCloakConfig.builder()
                .connectivity(KeyCloakConfig.Connectivity.builder()
                        .baseUrl("http://localhost:8080")
                        .managementUrl("http://localhost:9000")
                        .timeoutInMs(3000)
                        .build())
                .admin(KeyCloakConfig.Admin.builder()
                        .username("admin")
                        .password("admin-password")
                        .realm("master")
                        .clientId("admin-cli")
                        .build())
                .realm(KeyCloakConfig.Realm.builder()
                        .id("demo-realm")
                        .displayName("Demo Realm")
                        .client(KeyCloakConfig.Realm.Client.builder()
                                .id("demo-client")
                                .name("Demo Client")
                                .secret("client-secret")
                                .redirectUris(List.of("http://localhost:3000/*"))
                                .build())
                        .management(KeyCloakConfig.Realm.Management.builder()
                                .clientId("realm-management")
                                .build())
                        .roles(List.of(RoleDefinition.builder().name("CUSTOMER").description("Customer role").build()))
                        .build())
                .build();

        var preparatorFactory = new AccessTokenRequestPreparatorFactory(List.of(
                new AdminAccessTokenRequestPreparator(),
                new ClientAccessTokenRequestPreparator(),
                new UserAccessTokenRequestPreparator()
        ));

        keyCloakService = new KeyCloakService(
                keyCloakManagementExchangeClient,
                keyCloakExchangeClient,
                keyCloakConfig,
                preparatorFactory,
                keyCloakClientContext,
                validator
        );
    }

    @Test
    void shouldReturnPingResponse_whenPingKeyCloakSucceeds() {
        when(keyCloakManagementExchangeClient.pingKeyCloak()).thenReturn(new KeyCloakPingResponse("UP", List.of()));

        var result = keyCloakService.pingKeyCloak();

        assertThat(result.status()).isEqualTo("UP");
    }

    @Test
    void shouldReturnAccessToken_whenRequestingAdminAccessToken() {
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("master"), any()))
                .thenReturn(accessTokenResponse("admin-token"));

        var accessToken = keyCloakService.getAdminAccessToken();

        assertThat(accessToken).isEqualTo("admin-token");
    }

    @Test
    void shouldReturnAccessToken_whenRequestingClientAccessToken() {
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("demo-realm"), any()))
                .thenReturn(accessTokenResponse("client-token"));

        var accessToken = keyCloakService.getClientAccessToken();

        assertThat(accessToken).isEqualTo("client-token");
    }

    @Test
    void shouldThrowIllegalArgumentException_whenUsernameIsBlankForUserAccessToken() {
        assertThatThrownBy(() -> keyCloakService.getAccessTokenResponseForUsernameAndPasswordCombination("   ", "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username or password cannot be blank");
    }

    @Test
    void shouldReturnEmptyOptional_whenUserAccessTokenRequestFails() {
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("demo-realm"), any()))
                .thenThrow(new RuntimeException("invalid credentials"));

        var result = keyCloakService.getAccessTokenResponseForUsernameAndPasswordCombination("john", "wrong-password");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowRuntimeException_whenCreatingUserWithMissingUsername() {
        var request = UserCreationRequest.builder()
                .email("john@test.com")
                .emailVerified(true)
                .firstName("John")
                .lastName("Doe")
                .enabled(true)
                .credentials(List.of(UserCreationRequest.CredentialRepresentation.builder()
                        .type("password")
                        .value("Secret@123")
                        .temporary(false)
                        .build()))
                .build();

        assertThatThrownBy(() -> keyCloakService.createUser(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("UserCreationRequest validation failed");
    }

    @Test
    void shouldReturnCreatedUserId_whenCreateUserSucceeds() {
        var request = validUserCreationRequest();
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("demo-realm"), any()))
                .thenReturn(accessTokenResponse("client-token"));
        when(keyCloakExchangeClient.createUser(any(), eq("demo-realm"), eq(request)))
                .thenReturn(ResponseEntity.created(URI.create("/admin/realms/demo-realm/users/user-id-123")).build());

        var userId = keyCloakService.createUser(request);

        assertThat(userId).contains("user-id-123");
    }

    @Test
    void shouldReturnEmptyOptional_whenCreateUserResponseHasNoLocationHeader() {
        var request = validUserCreationRequest();
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("demo-realm"), any()))
                .thenReturn(accessTokenResponse("client-token"));
        when(keyCloakExchangeClient.createUser(any(), eq("demo-realm"), eq(request)))
                .thenReturn(ResponseEntity.ok().build());

        var userId = keyCloakService.createUser(request);

        assertThat(userId).isEmpty();
    }

    @Test
    void shouldThrowKeycloakIntegrationException_whenFetchingRoleDetailsFails() {
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("master"), any()))
                .thenReturn(accessTokenResponse("admin-token"));
        when(keyCloakClientContext.getClientUuid()).thenReturn("client-uuid");
        when(keyCloakExchangeClient.getClientRoleDetailsByRoleName(any(), eq("demo-realm"), eq("client-uuid"), eq("CUSTOMER")))
                .thenThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> keyCloakService.getClientRoleDetailsByRoleName("CUSTOMER"))
                .isInstanceOf(KeycloakIntegrationException.class)
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.COMMUNICATION_ERROR);
    }

    @Test
    void shouldThrowKeycloakIntegrationException_whenUserNotFoundByUsername() {
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("demo-realm"), any()))
                .thenReturn(accessTokenResponse("client-token"));
        when(keyCloakExchangeClient.getUsersByUsername(any(), eq("demo-realm"), eq("missing-user")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> keyCloakService.getUserByUsername("missing-user"))
                .isInstanceOf(KeycloakIntegrationException.class)
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.USER_NOT_FOUND);
    }

    @Test
    void shouldReturnUserDetails_whenUserFoundByUsername() {
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("demo-realm"), any()))
                .thenReturn(accessTokenResponse("client-token"));
        var userDetails = new UserDetailsResponse("user-id-123", "john", "john@test.com", "John", "Doe",
                true, true, 1234L, null, null);
        when(keyCloakExchangeClient.getUsersByUsername(any(), eq("demo-realm"), eq("john")))
                .thenReturn(List.of(userDetails));

        var result = keyCloakService.getUserByUsername("john");

        assertThat(result.id()).isEqualTo("user-id-123");
        assertThat(result.username()).isEqualTo("john");
    }

    @Test
    void shouldAssignClientRoleToUser_whenAssignmentSucceeds() {
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("demo-realm"), any()))
                .thenReturn(accessTokenResponse("client-token"));
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("master"), any()))
                .thenReturn(accessTokenResponse("admin-token"));
        when(keyCloakClientContext.getClientUuid()).thenReturn("client-uuid");
        var roleDetails = new RoleDetailsResponse("role-id-123", "CUSTOMER", "Customer role", false, "client-uuid");
        when(keyCloakExchangeClient.getClientRoleDetailsByRoleName(any(), eq("demo-realm"), eq("client-uuid"), eq("CUSTOMER")))
                .thenReturn(roleDetails);

        assertThatCode(() -> keyCloakService.assignClientRoleToUser("user-id-123", "CUSTOMER"))
                .doesNotThrowAnyException();

        var expectedRoleAssignment = RoleAssignmentRequest.builder().id("role-id-123").name("CUSTOMER").build();
        verify(keyCloakExchangeClient).assignClientRoleToUser(any(), eq("demo-realm"), eq("user-id-123"),
                eq("client-uuid"), eq(List.of(expectedRoleAssignment)));
    }

    @Test
    void shouldThrowKeycloakIntegrationException_whenAssigningRoleToUserFails() {
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("demo-realm"), any()))
                .thenReturn(accessTokenResponse("client-token"));
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("master"), any()))
                .thenReturn(accessTokenResponse("admin-token"));
        when(keyCloakClientContext.getClientUuid()).thenReturn("client-uuid");
        var roleDetails = new RoleDetailsResponse("role-id-123", "CUSTOMER", "Customer role", false, "client-uuid");
        when(keyCloakExchangeClient.getClientRoleDetailsByRoleName(any(), eq("demo-realm"), eq("client-uuid"), eq("CUSTOMER")))
                .thenReturn(roleDetails);
        doThrow(new RuntimeException("assignment failed"))
                .when(keyCloakExchangeClient)
                .assignClientRoleToUser(any(), eq("demo-realm"), eq("user-id-123"), eq("client-uuid"), any());

        assertThatThrownBy(() -> keyCloakService.assignClientRoleToUser("user-id-123", "CUSTOMER"))
                .isInstanceOf(KeycloakIntegrationException.class)
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.COMMUNICATION_ERROR);
    }

    @Test
    void shouldReturnUserClientRoles_whenFetchSucceeds() {
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("demo-realm"), any()))
                .thenReturn(accessTokenResponse("client-token"));
        when(keyCloakClientContext.getClientUuid()).thenReturn("client-uuid");
        var roleDetails = new RoleDetailsResponse("role-id-123", "CUSTOMER", "Customer role", false, "client-uuid");
        when(keyCloakExchangeClient.getClientRolesForUser(any(), eq("demo-realm"), eq("user-id-123"), eq("client-uuid")))
                .thenReturn(List.of(roleDetails));

        var result = keyCloakService.getUserClientRoles("user-id-123");

        assertThat(result).containsExactly(roleDetails);
    }

    @Test
    void shouldThrowKeycloakIntegrationException_whenFetchingUserClientRolesFails() {
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("demo-realm"), any()))
                .thenReturn(accessTokenResponse("client-token"));
        when(keyCloakClientContext.getClientUuid()).thenReturn("client-uuid");
        when(keyCloakExchangeClient.getClientRolesForUser(any(), eq("demo-realm"), eq("user-id-123"), eq("client-uuid")))
                .thenThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> keyCloakService.getUserClientRoles("user-id-123"))
                .isInstanceOf(KeycloakIntegrationException.class)
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.COMMUNICATION_ERROR);
    }

    @Test
    void shouldReturnUserDetails_whenUserFoundById() {
        when(keyCloakExchangeClient.getAccessTokenForRealm(eq("master"), any()))
                .thenReturn(accessTokenResponse("admin-token"));
        var userDetails = new UserDetailsResponse("user-id-123", "john", "john@test.com", "John", "Doe",
                true, true, 1234L, null, null);
        when(keyCloakExchangeClient.getUserById(any(), eq("demo-realm"), eq("user-id-123")))
                .thenReturn(userDetails);

        var result = keyCloakService.getUserById("user-id-123");

        assertThat(result.id()).isEqualTo("user-id-123");
        assertThat(result.username()).isEqualTo("john");
    }

    private UserCreationRequest validUserCreationRequest() {
        return UserCreationRequest.builder()
                .username("john")
                .email("john@test.com")
                .emailVerified(true)
                .firstName("John")
                .lastName("Doe")
                .enabled(true)
                .credentials(List.of(UserCreationRequest.CredentialRepresentation.builder()
                        .type("password")
                        .value("Secret@123")
                        .temporary(false)
                        .build()))
                .build();
    }

    private AccessTokenResponse accessTokenResponse(String accessToken) {
        return new AccessTokenResponse(accessToken, 300, 1800, "refresh-token", "Bearer", 0, "session-state", "openid");
    }
}
