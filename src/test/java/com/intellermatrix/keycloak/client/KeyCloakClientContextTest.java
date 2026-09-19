package com.intellermatrix.keycloak.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyCloakClientContextTest {

    @Test
    void shouldThrowIllegalStateException_whenClientUuidNotSet() {
        var context = new KeyCloakClientContext();

        assertThatThrownBy(context::getClientUuid)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("KeyCloak client UUID is not set in the context.");
    }

    @Test
    void shouldReturnClientUuid_whenSet() {
        var context = new KeyCloakClientContext();
        context.setClientUuid("client-uuid-123");

        assertThat(context.getClientUuid()).isEqualTo("client-uuid-123");
    }

    @Test
    void shouldThrowIllegalStateException_whenServiceAccountIdNotSet() {
        var context = new KeyCloakClientContext();

        assertThatThrownBy(context::getServiceAccountId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("KeyCloak service account ID is not set in the context.");
    }

    @Test
    void shouldReturnServiceAccountId_whenSet() {
        var context = new KeyCloakClientContext();
        context.setServiceAccountId("service-account-456");

        assertThat(context.getServiceAccountId()).isEqualTo("service-account-456");
    }

    @Test
    void shouldThrowIllegalStateException_whenManagementClientUuidNotSet() {
        var context = new KeyCloakClientContext();

        assertThatThrownBy(context::getManagementClientUuid)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("KeyCloak management client UUID is not set in the context.");
    }

    @Test
    void shouldReturnManagementClientUuid_whenSet() {
        var context = new KeyCloakClientContext();
        context.setManagementClientUuid("mgmt-client-uuid-789");

        assertThat(context.getManagementClientUuid()).isEqualTo("mgmt-client-uuid-789");
    }
}
