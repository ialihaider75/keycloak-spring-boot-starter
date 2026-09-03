package com.intellermatrix.keycloak.preparator;

import com.intellermatrix.keycloak.enums.AccessTokenType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AccessTokenRequestPreparatorFactory {

    private final List<AccessTokenRequestPreparator> preparators;

    public AccessTokenRequestPreparator getPreparator(AccessTokenType type) {
        Map<AccessTokenType, AccessTokenRequestPreparator> preparatorMap = preparators.stream()
                .collect(Collectors.toMap(AccessTokenRequestPreparator::getType, Function.identity()));

        AccessTokenRequestPreparator preparator = preparatorMap.get(type);
        if (preparator == null) {
            throw new IllegalArgumentException("No preparator found for type: " + type);
        }
        return preparator;
    }
}
