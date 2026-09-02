package com.intellermatrix.keycloak.enums;

import lombok.Getter;

@Getter
public enum ClientProtocols {

    OPENID_CONNECT("openid-connect");

    private final String protocol;

    ClientProtocols(String protocol) {
        this.protocol = protocol;
    }
}
