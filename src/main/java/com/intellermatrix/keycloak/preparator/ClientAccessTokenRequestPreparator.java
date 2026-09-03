package com.intellermatrix.keycloak.preparator;

import com.intellermatrix.keycloak.enums.AccessTokenType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
public class ClientAccessTokenRequestPreparator implements AccessTokenRequestPreparator {

    private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";

    @Override
    public MultiValueMap<String, String> prepareAccessTokenRequest(String username,
                                                                   String password,
                                                                   String clientId,
                                                                   String clientSecret) {
        final MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add("grant_type", GRANT_TYPE_CLIENT_CREDENTIALS);
        requestMap.add("client_id", clientId);
        requestMap.add("client_secret", clientSecret);
        return requestMap;
    }

    @Override
    public AccessTokenType getType() {
        return AccessTokenType.CLIENT;
    }
}
