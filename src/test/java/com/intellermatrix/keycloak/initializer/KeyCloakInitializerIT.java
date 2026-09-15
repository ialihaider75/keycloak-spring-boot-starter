package com.intellermatrix.keycloak.initializer;

import com.intellermatrix.keycloak.KeycloakIntegrationTestSupport;
import com.intellermatrix.keycloak.client.KeyCloakClientContext;
import com.intellermatrix.keycloak.config.KeyCloakConfig;
import com.intellermatrix.keycloak.exchange.KeyCloakExchangeClient;
import com.intellermatrix.keycloak.service.KeyCloakService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class KeyCloakInitializerIT extends KeycloakIntegrationTestSupport {

    @Autowired
    private KeyCloakExchangeClient keyCloakExchangeClient;

    @Autowired
    private KeyCloakService keyCloakService;

    @Autowired
    private KeyCloakConfig keyCloakConfig;

    @Autowired
    private KeyCloakClientContext keyCloakClientContext;

    @Test
    void shouldHaveRealmInKeyCloak_whenApplicationHasStarted() {
        var bearerToken = "Bearer " + keyCloakService.getAdminAccessToken();

        var response = keyCloakExchangeClient.getRealmDetails(bearerToken, keyCloakConfig.realm().id());

        assertThat(response.realm()).isEqualTo(keyCloakConfig.realm().id());
        assertThat(response.displayName()).isEqualTo(keyCloakConfig.realm().displayName());
    }

    @Test
    void shouldHaveClientInKeyCloak_whenApplicationHasStarted() {
        var bearerToken = "Bearer " + keyCloakService.getAdminAccessToken();

        var response = keyCloakExchangeClient.getClientDetails(
                bearerToken, keyCloakConfig.realm().id(), keyCloakConfig.realm().client().id());

        assertThat(response).isNotEmpty();
        assertThat(response.getFirst().clientId()).isEqualTo(keyCloakConfig.realm().client().id());
    }

    @Test
    void shouldHaveClientUuidServiceAccountAndManagementUuidInContext_whenApplicationHasStarted() {
        assertThat(keyCloakClientContext.getClientUuid()).isNotNull();
        assertThat(keyCloakClientContext.getServiceAccountId()).isNotNull();
        assertThat(keyCloakClientContext.getManagementClientUuid()).isNotNull();
    }

    @Test
    void shouldHaveConfiguredRolesRegisteredOnClientLevel_whenApplicationHasStarted() {
        var bearerToken = "Bearer " + keyCloakService.getAdminAccessToken();

        for (var role : keyCloakConfig.realm().roles()) {
            var roleDetails = keyCloakExchangeClient.getClientRoleDetailsByRoleName(
                    bearerToken, keyCloakConfig.realm().id(), keyCloakClientContext.getClientUuid(), role.name());

            assertThat(roleDetails.name()).isEqualTo(role.name());
            assertThat(roleDetails.description()).isEqualTo(role.description());
        }
    }

    @Test
    void shouldHaveRealmManagementRolesAssignedToServiceAccount_whenApplicationHasStarted() {
        var bearerToken = "Bearer " + keyCloakService.getAdminAccessToken();

        var realmRoles = keyCloakExchangeClient.getRealmManagementRolesForServiceAccount(
                bearerToken,
                keyCloakConfig.realm().id(),
                keyCloakClientContext.getServiceAccountId(),
                keyCloakClientContext.getManagementClientUuid());

        assertThat(realmRoles).isNotEmpty();
        assertThat(realmRoles).extracting("name").contains("manage-users");
    }
}
