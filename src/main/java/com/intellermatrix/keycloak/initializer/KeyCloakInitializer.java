package com.intellermatrix.keycloak.initializer;

import com.intellermatrix.keycloak.client.KeyCloakClientContext;
import com.intellermatrix.keycloak.config.KeyCloakConfig;
import com.intellermatrix.keycloak.dto.client.ClientCreationRequest;
import com.intellermatrix.keycloak.dto.realm.CreateRealmRequest;
import com.intellermatrix.keycloak.dto.role.RoleAssignmentRequest;
import com.intellermatrix.keycloak.dto.role.RoleCreationRequest;
import com.intellermatrix.keycloak.exchange.KeyCloakExchangeClient;
import com.intellermatrix.keycloak.service.KeyCloakService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KeyCloakInitializer {

    private final KeyCloakService keyCloakService;
    private final KeyCloakConfig keyCloakConfig;
    private final KeyCloakExchangeClient keyCloakExchangeClient;
    private final KeyCloakClientContext keyCloakClientContext;

    @PostConstruct
    public void init() {
        log.info("Pinging KeyCloak server before provisioning realm/client/roles...");
        keyCloakService.pingKeyCloak();
        log.info("KeyCloak server is reachable, proceeding with provisioning.");

        var bearerToken = keyCloakService.getAdminAccessToken()
                .transform(accessToken -> String.format("Bearer %s", accessToken));

        createRealmIfNotExists(bearerToken);
        retrieveRealmManagementClientDetails(bearerToken);
        createClientIfNotExists(bearerToken);
        retrieveServiceAccountIdForClient(bearerToken);
        shouldAssignRealmManagementRolesToServiceAccountIfNotExists(bearerToken);
        shouldCreateRolesOnClientLevelIfNotExists(bearerToken);
    }

    private void createRealmIfNotExists(String bearerToken) {
        log.info("Checking if KeyCloak realm '{}' exists...", keyCloakConfig.realm().id());

        try {
            var realmDetails = keyCloakExchangeClient.getRealmDetails(
                    bearerToken,
                    keyCloakConfig.realm().id()
            );

            log.info("KeyCloak realm '{}' already exists: {}, skipping creation of realm ...",
                    keyCloakConfig.realm().id(), realmDetails);
        } catch (Exception e) {
            log.warn("KeyCloak realm '{}' does not exist. Creating new realm...", keyCloakConfig.realm().id());

            var createRealmRequest = CreateRealmRequest.builder()
                    .realm(keyCloakConfig.realm().id())
                    .displayName(keyCloakConfig.realm().displayName())
                    .enabled(true)
                    .build();

            keyCloakExchangeClient.createRealm(
                    bearerToken,
                    createRealmRequest
            );

            log.info("KeyCloak realm '{}' created successfully.", keyCloakConfig.realm().id());
        }
    }

    private void retrieveRealmManagementClientDetails(String bearerToken) {
        log.info("Retrieving 'realm-management' client details for realm '{}'", keyCloakConfig.realm().id());
        var clientDetailsList = keyCloakExchangeClient.getClientDetails(
                bearerToken,
                keyCloakConfig.realm().id(),
                keyCloakConfig.realm().management().clientId()
        );

        if (clientDetailsList.isEmpty()) {
            throw new IllegalStateException(String.format("'%s' client not found in realm '%s'",
                    keyCloakConfig.realm().management().clientId(),
                    keyCloakConfig.realm().id()));
        }

        var realmManagementClientDetails = clientDetailsList.getFirst();
        log.info("'{}' client details retrieved: {}", keyCloakConfig.realm().management().clientId(), realmManagementClientDetails);
        keyCloakClientContext.setManagementClientUuid(realmManagementClientDetails.id());
    }

    private void createClientIfNotExists(String bearerToken) {
        log.info("Checking if KeyCloak client '{}' exists in realm '{}'...",
                keyCloakConfig.realm().client().id(),
                keyCloakConfig.realm().id());

        var clientDetails = keyCloakExchangeClient.getClientDetails(
                bearerToken,
                keyCloakConfig.realm().id(),
                keyCloakConfig.realm().client().id()
        );

        if (clientDetails.isEmpty()) {

            log.warn("KeyCloak client '{}' does not exist in realm '{}'. Creating new client...",
                    keyCloakConfig.realm().client().id(),
                    keyCloakConfig.realm().id());

            var clientCreationRequest = ClientCreationRequest.builder()
                    .clientId(keyCloakConfig.realm().client().id())
                    .name(keyCloakConfig.realm().client().name())
                    .secret(keyCloakConfig.realm().client().secret())
                    .enabled(true)
                    .redirectUris(keyCloakConfig.realm().client().redirectUris())
                    .publicClient(false)
                    .standardFlowEnabled(true)
                    .directAccessGrantsEnabled(true)
                    .serviceAccountsEnabled(true)
                    .build();

            keyCloakExchangeClient.createClient(
                    bearerToken,
                    keyCloakConfig.realm().id(),
                    clientCreationRequest
            );

            log.info("KeyCloak client '{}' created successfully in realm '{}'.",
                    keyCloakConfig.realm().client().id(),
                    keyCloakConfig.realm().id());

            var clientList = keyCloakExchangeClient.getClientDetails(
                    bearerToken,
                    keyCloakConfig.realm().id(),
                    keyCloakConfig.realm().client().id()
            );
            log.info("Newly created client details: {}", clientList.getFirst());
            keyCloakClientContext.setClientUuid(clientList.getFirst().id());

        } else {
            log.info("KeyCloak client '{}' already exists in realm '{}': {}, skipping creation of client ...",
                    keyCloakConfig.realm().client().id(),
                    keyCloakConfig.realm().id(),
                    clientDetails);
            keyCloakClientContext.setClientUuid(clientDetails.getFirst().id());
        }
    }

    private void retrieveServiceAccountIdForClient(String bearerToken) {
        log.info("Retrieving service account user for client UUID: {}", keyCloakClientContext.getClientUuid());
        var serviceAccountUser = keyCloakExchangeClient.getServiceAccountUserForClient(
                bearerToken,
                keyCloakConfig.realm().id(),
                keyCloakClientContext.getClientUuid()
        );
        log.info("Service account user retrieved: {}", serviceAccountUser);
        keyCloakClientContext.setServiceAccountId(serviceAccountUser.id());
    }

    private void shouldAssignRealmManagementRolesToServiceAccountIfNotExists(String bearerToken) {
        log.info("Retrieving realm management roles assigned to service account ID: {} of client : {}",
                keyCloakClientContext.getServiceAccountId(),
                keyCloakConfig.realm().client().id());

        var assignedRoles = keyCloakExchangeClient.getRealmManagementRolesForServiceAccount(
                bearerToken,
                keyCloakConfig.realm().id(),
                keyCloakClientContext.getServiceAccountId(),
                keyCloakClientContext.getManagementClientUuid()
        );
        if (!assignedRoles.isEmpty()) {
            log.info("Assigned roles retrieved: {}", assignedRoles);
        } else {
            var roles = keyCloakExchangeClient.getClientRoles(bearerToken,
                    keyCloakConfig.realm().id(),
                    keyCloakClientContext.getManagementClientUuid());

            var rolesAssignmentRequest = roles
                    .stream()
                    .filter(role -> role.name().contains("users"))
                    .map(role -> RoleAssignmentRequest
                            .builder()
                            .id(role.id())
                            .name(role.name())
                            .build())
                    .toList();

            log.warn("No Realm management roles assigned to service account ID: {} of client : {}, going to assign roles {}",
                    keyCloakClientContext.getServiceAccountId(),
                    keyCloakConfig.realm().client().id(),
                    rolesAssignmentRequest);

            keyCloakExchangeClient.assignClientRolesToUser(
                    bearerToken,
                    keyCloakConfig.realm().id(),
                    keyCloakClientContext.getServiceAccountId(),
                    keyCloakClientContext.getManagementClientUuid(),
                    rolesAssignmentRequest
            );
            log.info("Total {} Realm management Roles assigned successfully to service account ID: {} of client : {}",
                    rolesAssignmentRequest.size(),
                    keyCloakConfig.realm().client().id(),
                    keyCloakClientContext.getServiceAccountId());
        }
    }

    private void shouldCreateRolesOnClientLevelIfNotExists(String bearerToken) {
        var roles = keyCloakConfig.realm().roles();
        for (var role : roles) {
            try {
                var roleDetails = keyCloakExchangeClient.getClientRoleDetailsByRoleName(bearerToken,
                        keyCloakConfig.realm().id(),
                        keyCloakClientContext.getClientUuid(),
                        role.name()
                );

                log.info("Role '{}' already exists on client level: {}, skipping creation of role ...",
                        role.name(),
                        roleDetails);
            } catch (Exception e) {
                log.warn("Role '{}' does not exist on client level. Creating new role...", role.name());

                var roleCreationRequest = RoleCreationRequest.builder()
                        .name(role.name())
                        .description(role.description())
                        .composite(false)
                        .clientRole(true)
                        .build();

                keyCloakExchangeClient.createClientRole(
                        bearerToken,
                        keyCloakConfig.realm().id(),
                        keyCloakClientContext.getClientUuid(),
                        roleCreationRequest
                );

                log.info("Role '{}' created successfully on client level.", role.name());
            }
        }
    }
}
