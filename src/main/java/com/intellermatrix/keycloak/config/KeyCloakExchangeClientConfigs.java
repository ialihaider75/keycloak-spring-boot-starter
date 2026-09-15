package com.intellermatrix.keycloak.config;

import com.intellermatrix.keycloak.exception.KeycloakErrorReason;
import com.intellermatrix.keycloak.exception.KeycloakIntegrationException;
import com.intellermatrix.keycloak.exchange.KeyCloakExchangeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class KeyCloakExchangeClientConfigs {

    private static final String CLIENT_NAME = "Key Cloak Client";
    private static final String EXCEPTION_LOG_TEMPLATE = "Error occurred while calling %s.";
    private final KeyCloakConfig config;

    @Bean(name = "keyCloakExchangeClient")
    KeyCloakExchangeClient keyCloakExchangeClient(@Qualifier("keyCloakRestClient") RestClient restClient) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(KeyCloakExchangeClient.class);
    }

    @Bean(name = "keyCloakManagementExchangeClient")
    KeyCloakExchangeClient keyCloakManagementExchangeClient(@Qualifier("keyCloakManagementRestClient") RestClient restClient) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(KeyCloakExchangeClient.class);
    }

    @Bean(name = "keyCloakRestClient")
    RestClient restClient() {
        return applyErrorHandlers(RestClient.builder())
                .baseUrl(config.connectivity().baseUrl())
                .requestFactory(getSimpleClientHttpRequestFactory())
                .build();
    }

    @Bean(name = "keyCloakManagementRestClient")
    RestClient keyCloakManagementRestClient() {
        return applyErrorHandlers(RestClient.builder())
                .baseUrl(config.connectivity().managementUrl())
                .requestFactory(getSimpleClientHttpRequestFactory())
                .build();
    }

    RestClient.Builder applyErrorHandlers(RestClient.Builder builder) {
        return builder
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, ((request, response) -> {
                    logError(request, response);
                    throw new KeycloakIntegrationException(KeycloakErrorReason.INVALID_REQUEST,
                            String.format(EXCEPTION_LOG_TEMPLATE, CLIENT_NAME), resolveClientErrorStatus(response));
                }))
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, ((request, response) -> {
                    logError(request, response);
                    throw new KeycloakIntegrationException(KeycloakErrorReason.INTERNAL_SERVER_ERROR,
                            String.format(EXCEPTION_LOG_TEMPLATE, CLIENT_NAME), HttpStatus.INTERNAL_SERVER_ERROR);
                }));
    }

    private ClientHttpRequestFactory getSimpleClientHttpRequestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.connectivity().timeoutInMs());
        factory.setReadTimeout(config.connectivity().timeoutInMs());
        return factory;
    }

    /**
     * Keeps the real client-error status on the thrown exception so callers can react to specific
     * statuses (e.g. a 409 on user creation meaning the username/email is already taken) instead of
     * seeing every 4xx flattened into {@code 400 BAD_REQUEST}.
     */
    private HttpStatus resolveClientErrorStatus(ClientHttpResponse response) throws IOException {
        var resolvedStatus = HttpStatus.resolve(response.getStatusCode().value());
        return Objects.isNull(resolvedStatus) ? HttpStatus.BAD_REQUEST : resolvedStatus;
    }

    private void logError(HttpRequest request, ClientHttpResponse response) throws IOException {
        log.error("Error for {}: Request URI: {}, Response Body {}, Method: {}, Response Status Code: {}",
                CLIENT_NAME,
                request.getURI(),
                parseHttpResponseBody(response).orElse(null),
                request.getMethod().name(),
                response.getStatusCode());
    }

    private Optional<String> parseHttpResponseBody(ClientHttpResponse response) {
        try (var inputStream = response.getBody()) {
            var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            return Optional.of(reader.lines().collect(Collectors.joining(System.lineSeparator())));
        } catch (IOException e) {
            log.error("Error reading response body", e);
            return Optional.empty();
        }
    }
}
