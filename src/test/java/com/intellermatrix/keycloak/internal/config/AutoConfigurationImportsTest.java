package com.intellermatrix.keycloak.internal.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AutoConfigurationImportsTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    void shouldListOnlyResolvableAutoConfigurationClasses() throws IOException {
        var classNames = readNonBlankLines();

        assertThat(classNames).isNotEmpty();
        for (var className : classNames) {
            assertThatCode(() -> Class.forName(className))
                    .as("auto-configuration class '%s' listed in %s must exist on the classpath", className, IMPORTS_RESOURCE)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void shouldListKeycloakAutoConfiguration() throws IOException {
        assertThat(readNonBlankLines()).containsExactly(KeycloakAutoConfiguration.class.getName());
    }

    private List<String> readNonBlankLines() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(IMPORTS_RESOURCE)) {
            assertThat(inputStream).as("resource %s must exist on the classpath", IMPORTS_RESOURCE).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList();
        }
    }
}
