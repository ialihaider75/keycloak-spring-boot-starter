package com.intellermatrix.keycloak.preparator;

import com.intellermatrix.keycloak.enums.AccessTokenType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenRequestPreparatorFactoryTest {

    private final AccessTokenRequestPreparatorFactory factory = new AccessTokenRequestPreparatorFactory(List.of(
            new AdminAccessTokenRequestPreparator(),
            new ClientAccessTokenRequestPreparator(),
            new UserAccessTokenRequestPreparator()
    ));

    @Test
    void shouldBuildPasswordGrantRequest_forAdminType() {
        var request = factory.getPreparator(AccessTokenType.ADMIN)
                .prepareAccessTokenRequest("admin", "admin-pw", "admin-cli", null);

        assertThat(request.getFirst("grant_type")).isEqualTo("password");
        assertThat(request.getFirst("client_id")).isEqualTo("admin-cli");
        assertThat(request.getFirst("username")).isEqualTo("admin");
        assertThat(request.getFirst("password")).isEqualTo("admin-pw");
    }

    @Test
    void shouldBuildClientCredentialsGrantRequest_forClientType() {
        var request = factory.getPreparator(AccessTokenType.CLIENT)
                .prepareAccessTokenRequest(null, null, "demo-client", "client-secret");

        assertThat(request.getFirst("grant_type")).isEqualTo("client_credentials");
        assertThat(request.getFirst("client_id")).isEqualTo("demo-client");
        assertThat(request.getFirst("client_secret")).isEqualTo("client-secret");
    }

    @Test
    void shouldBuildPasswordGrantRequestWithClientSecret_forUserType() {
        var request = factory.getPreparator(AccessTokenType.USER)
                .prepareAccessTokenRequest("john", "john-pw", "demo-client", "client-secret");

        assertThat(request.getFirst("grant_type")).isEqualTo("password");
        assertThat(request.getFirst("client_id")).isEqualTo("demo-client");
        assertThat(request.getFirst("client_secret")).isEqualTo("client-secret");
        assertThat(request.getFirst("username")).isEqualTo("john");
        assertThat(request.getFirst("password")).isEqualTo("john-pw");
    }

    @Test
    void shouldThrowIllegalArgumentException_whenNoPreparatorRegisteredForType() {
        var emptyFactory = new AccessTokenRequestPreparatorFactory(List.of());

        assertThatThrownBy(() -> emptyFactory.getPreparator(AccessTokenType.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No preparator found for type: ADMIN");
    }
}
