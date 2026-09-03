package com.intellermatrix.keycloak.config;

import com.intellermatrix.keycloak.exception.KeycloakErrorReason;
import com.intellermatrix.keycloak.exception.KeycloakIntegrationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class KeyCloakExchangeClientConfigsTest {

    private final KeyCloakConfig config = KeyCloakConfig.builder()
            .connectivity(KeyCloakConfig.Connectivity.builder()
                    .baseUrl("http://localhost:8080")
                    .managementUrl("http://localhost:9000")
                    .timeoutInMs(3000)
                    .build())
            .build();

    private final KeyCloakExchangeClientConfigs exchangeClientConfigs = new KeyCloakExchangeClientConfigs(config);

    @Test
    void shouldThrowKeycloakIntegrationException_whenServerRespondsWith4xx() {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = exchangeClientConfigs.applyErrorHandlers(builder).build();

        mockServer.expect(requestTo("/health/live"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThatThrownBy(() -> restClient.get().uri("/health/live").retrieve().toBodilessEntity())
                .isInstanceOf(KeycloakIntegrationException.class)
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.INVALID_REQUEST);
    }

    @Test
    void shouldThrowKeycloakIntegrationException_whenServerRespondsWith5xx() {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        var restClient = exchangeClientConfigs.applyErrorHandlers(builder).build();

        mockServer.expect(requestTo("/health/live"))
                .andRespond(withServerError().contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThatThrownBy(() -> restClient.get().uri("/health/live").retrieve().toBodilessEntity())
                .isInstanceOf(KeycloakIntegrationException.class)
                .extracting(exception -> ((KeycloakIntegrationException) exception).getReason())
                .isEqualTo(KeycloakErrorReason.INTERNAL_SERVER_ERROR);
    }
}
