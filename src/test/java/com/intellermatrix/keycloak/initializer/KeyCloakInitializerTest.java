package com.intellermatrix.keycloak.initializer;

import com.intellermatrix.keycloak.client.KeyCloakClientContext;
import com.intellermatrix.keycloak.config.KeyCloakConfig;
import com.intellermatrix.keycloak.config.RoleDefinition;
import com.intellermatrix.keycloak.dto.KeyCloakPingResponse;
import com.intellermatrix.keycloak.dto.client.ClientDetailsResponse;
import com.intellermatrix.keycloak.dto.realm.GetRealmResponse;
import com.intellermatrix.keycloak.dto.role.RoleAssignmentRequest;
import com.intellermatrix.keycloak.dto.role.RoleDetailsResponse;
import com.intellermatrix.keycloak.dto.serviceaccount.ServiceAccountUserResponse;
import com.intellermatrix.keycloak.exception.KeycloakErrorReason;
import com.intellermatrix.keycloak.exception.KeycloakIntegrationException;
import com.intellermatrix.keycloak.exchange.KeyCloakExchangeClient;
import com.intellermatrix.keycloak.service.KeyCloakService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KeyCloakInitializerTest {

    private final KeyCloakService keyCloakService = mock(KeyCloakService.class);
    private final KeyCloakExchangeClient keyCloakExchangeClient = mock(KeyCloakExchangeClient.class);
    private final KeyCloakClientContext keyCloakClientContext = mock(KeyCloakClientContext.class);

    private KeyCloakConfig keyCloakConfig;
    private KeyCloakInitializer keyCloakInitializer;

    @BeforeEach
    void setUp() {
        keyCloakConfig = KeyCloakConfig.builder()
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

        keyCloakInitializer = new KeyCloakInitializer(keyCloakService, keyCloakConfig, keyCloakExchangeClient, keyCloakClientContext);
    }

    @Test
    void shouldAbortInitialization_whenPingKeyCloakFails() {
        when(keyCloakService.pingKeyCloak()).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> keyCloakInitializer.init())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("connection refused");

        verifyNoInteractions(keyCloakExchangeClient);
        verify(keyCloakService, never()).getAdminAccessToken();
    }

    @Test
    void shouldPingBeforeProvisioning_whenKeyCloakIsReachable() {
        when(keyCloakService.pingKeyCloak()).thenReturn(new KeyCloakPingResponse("UP", List.of()));
        when(keyCloakService.getAdminAccessToken()).thenReturn("admin-token");
        stubSuccessfulProvisioning();

        keyCloakInitializer.init();

        var inOrder = inOrder(keyCloakService, keyCloakExchangeClient);
        inOrder.verify(keyCloakService).pingKeyCloak();
        inOrder.verify(keyCloakExchangeClient).getRealmDetails(anyString(), eq("demo-realm"));
    }

    @Test
    void shouldFetchFreshAdminAccessTokenForEachProvisioningStep() {
        when(keyCloakService.pingKeyCloak()).thenReturn(new KeyCloakPingResponse("UP", List.of()));
        when(keyCloakService.getAdminAccessToken()).thenReturn("admin-token");
        stubSuccessfulProvisioning();

        keyCloakInitializer.init();

        verify(keyCloakService, times(6)).getAdminAccessToken();
    }

    @Test
    void shouldCreateRole_whenConfiguredRoleDoesNotExistOnClientLevel() {
        when(keyCloakService.pingKeyCloak()).thenReturn(new KeyCloakPingResponse("UP", List.of()));
        when(keyCloakService.getAdminAccessToken()).thenReturn("admin-token");
        stubSuccessfulProvisioning();
        when(keyCloakExchangeClient.getClientRoleDetailsByRoleName(anyString(), eq("demo-realm"), eq("client-uuid"), eq("CUSTOMER")))
                .thenThrow(new KeycloakIntegrationException(KeycloakErrorReason.INVALID_REQUEST, "role not found", HttpStatus.NOT_FOUND));

        keyCloakInitializer.init();

        verify(keyCloakExchangeClient).createClientRole(anyString(), eq("demo-realm"), eq("client-uuid"),
                argThat(request -> request.name().equals("CUSTOMER") && request.description().equals("Customer role")));
    }

    @Test
    void shouldPropagateException_whenRealmExistenceCheckFailsWithNonNotFoundError() {
        when(keyCloakService.pingKeyCloak()).thenReturn(new KeyCloakPingResponse("UP", List.of()));
        when(keyCloakService.getAdminAccessToken()).thenReturn("admin-token");
        var serverError = new KeycloakIntegrationException(KeycloakErrorReason.INTERNAL_SERVER_ERROR,
                "KeyCloak is unavailable", HttpStatus.INTERNAL_SERVER_ERROR);
        when(keyCloakExchangeClient.getRealmDetails(anyString(), eq("demo-realm"))).thenThrow(serverError);

        assertThatThrownBy(() -> keyCloakInitializer.init())
                .isSameAs(serverError);

        verify(keyCloakExchangeClient, never()).createRealm(anyString(), any());
    }

    @Test
    void shouldPropagateException_whenRoleExistenceCheckFailsWithNonNotFoundError() {
        when(keyCloakService.pingKeyCloak()).thenReturn(new KeyCloakPingResponse("UP", List.of()));
        when(keyCloakService.getAdminAccessToken()).thenReturn("admin-token");
        stubSuccessfulProvisioning();
        var serverError = new KeycloakIntegrationException(KeycloakErrorReason.INTERNAL_SERVER_ERROR,
                "KeyCloak is unavailable", HttpStatus.INTERNAL_SERVER_ERROR);
        when(keyCloakExchangeClient.getClientRoleDetailsByRoleName(anyString(), eq("demo-realm"), eq("client-uuid"), eq("CUSTOMER")))
                .thenThrow(serverError);

        assertThatThrownBy(() -> keyCloakInitializer.init())
                .isSameAs(serverError);

        verify(keyCloakExchangeClient, never()).createClientRole(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldThrowIllegalStateException_whenNewlyCreatedClientCannotBeRetrieved() {
        when(keyCloakService.pingKeyCloak()).thenReturn(new KeyCloakPingResponse("UP", List.of()));
        when(keyCloakService.getAdminAccessToken()).thenReturn("admin-token");
        stubSuccessfulProvisioning();
        when(keyCloakExchangeClient.getClientDetails(anyString(), eq("demo-realm"), eq("demo-client")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> keyCloakInitializer.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("KeyCloak client 'demo-client' not found in realm 'demo-realm' immediately after creation");

        verify(keyCloakClientContext, never()).setClientUuid(anyString());
    }

    @Test
    void shouldAssignOnlyMissingRealmManagementRoles_whenServiceAccountIsPartiallyEntitled() {
        when(keyCloakService.pingKeyCloak()).thenReturn(new KeyCloakPingResponse("UP", List.of()));
        when(keyCloakService.getAdminAccessToken()).thenReturn("admin-token");
        stubSuccessfulProvisioning();
        when(keyCloakExchangeClient.getRealmManagementRolesForServiceAccount(anyString(), eq("demo-realm"), anyString(), anyString()))
                .thenReturn(List.of(new RoleDetailsResponse("role-id-1", "manage-users", "desc", false, "mgmt-uuid")));
        when(keyCloakExchangeClient.getClientRoles(anyString(), eq("demo-realm"), eq("mgmt-uuid")))
                .thenReturn(List.of(
                        new RoleDetailsResponse("role-id-1", "manage-users", "desc", false, "mgmt-uuid"),
                        new RoleDetailsResponse("role-id-2", "view-users", "desc", false, "mgmt-uuid"),
                        new RoleDetailsResponse("role-id-3", "query-users", "desc", false, "mgmt-uuid"),
                        new RoleDetailsResponse("role-id-4", "view-clients", "desc", false, "mgmt-uuid"),
                        new RoleDetailsResponse("role-id-5", "manage-realm", "desc", false, "mgmt-uuid")));

        keyCloakInitializer.init();

        verify(keyCloakExchangeClient).assignClientRolesToUser(anyString(), eq("demo-realm"), eq("service-account-id"),
                eq("mgmt-uuid"), argThat(requests -> requests.stream().map(RoleAssignmentRequest::name).toList()
                        .containsAll(List.of("view-users", "query-users", "view-clients"))
                        && requests.size() == 3));
    }

    @Test
    void shouldNotAssignRealmManagementRoles_whenServiceAccountAlreadyHasAllRequiredRoles() {
        when(keyCloakService.pingKeyCloak()).thenReturn(new KeyCloakPingResponse("UP", List.of()));
        when(keyCloakService.getAdminAccessToken()).thenReturn("admin-token");
        stubSuccessfulProvisioning();

        keyCloakInitializer.init();

        verify(keyCloakExchangeClient, never()).assignClientRolesToUser(anyString(), anyString(), anyString(),
                anyString(), anyList());
    }

    private void stubSuccessfulProvisioning() {
        when(keyCloakExchangeClient.getRealmDetails(anyString(), eq("demo-realm")))
                .thenReturn(new GetRealmResponse("id", "demo-realm", "Demo Realm"));
        when(keyCloakExchangeClient.getClientDetails(anyString(), eq("demo-realm"), eq("realm-management")))
                .thenReturn(List.of(new ClientDetailsResponse("mgmt-uuid", "realm-management", "realm-management",
                        true, false, null, "openid-connect", List.of(), false, false)));
        when(keyCloakExchangeClient.getClientDetails(anyString(), eq("demo-realm"), eq("demo-client")))
                .thenReturn(List.of(new ClientDetailsResponse("client-uuid", "demo-client", "Demo Client",
                        true, false, "client-secret", "openid-connect", List.of(), true, true)));
        when(keyCloakClientContext.getClientUuid()).thenReturn("client-uuid");
        when(keyCloakClientContext.getManagementClientUuid()).thenReturn("mgmt-uuid");
        when(keyCloakClientContext.getServiceAccountId()).thenReturn("service-account-id");
        when(keyCloakExchangeClient.getServiceAccountUserForClient(anyString(), eq("demo-realm"), eq("client-uuid")))
                .thenReturn(new ServiceAccountUserResponse("service-account-id", "service-account-demo-client", true));
        when(keyCloakExchangeClient.getRealmManagementRolesForServiceAccount(anyString(), eq("demo-realm"), anyString(), anyString()))
                .thenReturn(List.of(
                        new RoleDetailsResponse("role-id-1", "manage-users", "desc", false, "mgmt-uuid"),
                        new RoleDetailsResponse("role-id-2", "view-users", "desc", false, "mgmt-uuid"),
                        new RoleDetailsResponse("role-id-3", "query-users", "desc", false, "mgmt-uuid"),
                        new RoleDetailsResponse("role-id-4", "view-clients", "desc", false, "mgmt-uuid")));
        when(keyCloakExchangeClient.getClientRoleDetailsByRoleName(anyString(), eq("demo-realm"), eq("client-uuid"), eq("CUSTOMER")))
                .thenReturn(new RoleDetailsResponse("role-id", "CUSTOMER", "Customer role", false, "client-uuid"));
    }
}
