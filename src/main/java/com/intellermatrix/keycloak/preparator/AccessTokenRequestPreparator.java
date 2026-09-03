package com.intellermatrix.keycloak.preparator;

import com.intellermatrix.keycloak.enums.AccessTokenType;
import org.springframework.util.MultiValueMap;

public interface AccessTokenRequestPreparator {

    MultiValueMap<String, String> prepareAccessTokenRequest(String username,
                                                            String password,
                                                            String clientId,
                                                            String clientSecret);

    AccessTokenType getType();
}
