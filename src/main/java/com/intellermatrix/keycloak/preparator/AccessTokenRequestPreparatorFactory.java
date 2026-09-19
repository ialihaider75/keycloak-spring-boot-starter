package com.intellermatrix.keycloak.preparator;

import com.intellermatrix.keycloak.enums.AccessTokenType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AccessTokenRequestPreparatorFactory {

    private final Map<AccessTokenType, AccessTokenRequestPreparator> preparatorsByType;

    public AccessTokenRequestPreparatorFactory(List<AccessTokenRequestPreparator> preparators) {
        this.preparatorsByType = preparators.stream()
                .collect(Collectors.toUnmodifiableMap(AccessTokenRequestPreparator::getType, Function.identity()));
    }

    public AccessTokenRequestPreparator getPreparator(AccessTokenType type) {
        var preparator = preparatorsByType.get(type);
        if (preparator == null) {
            throw new IllegalArgumentException("No preparator found for type: " + type);
        }
        return preparator;
    }
}
