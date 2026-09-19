package com.intellermatrix.keycloak.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KeyCloakClientContext {

    // This class is temporary will move the storage of clientUUID into redis cache for future use.

    private volatile String clientUuid;
    private volatile String serviceAccountId;
    private volatile String managementClientUuid;

    public String getClientUuid() {
        if (clientUuid == null || clientUuid.isEmpty()) {
            log.warn("KeyCloak client UUID is not set in the context.");
            throw new IllegalStateException("KeyCloak client UUID is not set in the context.");
        }
        return clientUuid;
    }

    public void setClientUuid(String clientUuid) {
        log.info("Setting KeyCloak client UUID: {}", clientUuid);
        this.clientUuid = clientUuid;
    }

    public String getServiceAccountId() {
        if (serviceAccountId == null || serviceAccountId.isEmpty()) {
            log.warn("KeyCloak service account ID is not set in the context.");
            throw new IllegalStateException("KeyCloak service account ID is not set in the context.");
        }
        return serviceAccountId;
    }

    public void setServiceAccountId(String serviceAccountId) {
        log.info("Setting KeyCloak service account ID: {}", serviceAccountId);
        this.serviceAccountId = serviceAccountId;
    }

    public String getManagementClientUuid() {
        if (managementClientUuid == null || managementClientUuid.isEmpty()) {
            log.warn("KeyCloak management client UUID is not set in the context.");
            throw new IllegalStateException("KeyCloak management client UUID is not set in the context.");
        }
        return managementClientUuid;
    }


    public void setManagementClientUuid(String managementClientUuid) {
        log.info("Setting KeyCloak management client UUID: {}", managementClientUuid);
        this.managementClientUuid = managementClientUuid;
    }
}
