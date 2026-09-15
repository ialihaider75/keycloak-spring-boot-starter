package com.intellermatrix.keycloak.initializer;

import com.intellermatrix.keycloak.client.KeyCloakClientContext;
import com.intellermatrix.keycloak.config.KeyCloakConfig;
import com.intellermatrix.keycloak.dto.client.ClientCreationRequest;
import com.intellermatrix.keycloak.dto.realm.CreateRealmRequest;
import com.intellermatrix.keycloak.dto.role.RoleAssignmentRequest;
import com.intellermatrix.keycloak.dto.role.RoleCreationRequest;
import com.intellermatrix.keycloak.dto.role.RoleDetailsResponse;
import com.intellermatrix.keycloak.exchange.KeyCloakExchangeClient;
import com.intellermatrix.keycloak.service.KeyCloakService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "external-services.keycloak", name = "auto-provision", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class KeyCloakInitializer {

    /**
     * Realm-management roles the client's service account needs so that all per-request operations
     * can run on the client access token instead of master-realm admin credentials:
     * the {@code *-users} roles cover user creation/lookup and role-mapping, {@code view-clients}
     * covers reading the client's own role definitions.
     */
    private static final Set<String> REQUIRED_REALM_MANAGEMENT_ROLES =
            Set.of("manage-users", "view-users", "query-users", "view-clients");

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
        log.info("'{}' client retrieved with UUID {}",
                keyCloakConfig.realm().management().clientId(),
                realmManagementClientDetails.id());
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
            log.info("Newly created KeyCloak client '{}' in realm '{}' has UUID {}",
                    keyCloakConfig.realm().client().id(),
                    keyCloakConfig.realm().id(),
                    clientList.getFirst().id());
            keyCloakClientContext.setClientUuid(clientList.getFirst().id());

        } else {
            log.info("KeyCloak client '{}' already exists in realm '{}' with UUID {}, skipping creation of client ...",
                    keyCloakConfig.realm().client().id(),
                    keyCloakConfig.realm().id(),
                    clientDetails.getFirst().id());
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

        var assignedRoleNames = assignedRoles.stream()
                .map(RoleDetailsResponse::name)
                .collect(Collectors.toSet());

        var missingRoleNames = REQUIRED_REALM_MANAGEMENT_ROLES.stream()
                .filter(roleName -> !assignedRoleNames.contains(roleName))
                .collect(Collectors.toSet());

        if (missingRoleNames.isEmpty()) {
            log.info("All required realm management roles {} are already assigned to service account ID: {} of client : {}",
                    REQUIRED_REALM_MANAGEMENT_ROLES,
                    keyCloakClientContext.getServiceAccountId(),
                    keyCloakConfig.realm().client().id());
            return;
        }

        var roles = keyCloakExchangeClient.getClientRoles(bearerToken,
                keyCloakConfig.realm().id(),
                keyCloakClientContext.getManagementClientUuid());

        var rolesAssignmentRequest = roles
                .stream()
                .filter(role -> missingRoleNames.contains(role.name()))
                .map(role -> RoleAssignmentRequest
                        .builder()
                        .id(role.id())
                        .name(role.name())
                        .build())
                .toList();

        if (rolesAssignmentRequest.isEmpty()) {
            log.warn("Required realm management roles {} are not available on client '{}' in realm '{}', nothing to assign",
                    missingRoleNames,
                    keyCloakConfig.realm().management().clientId(),
                    keyCloakConfig.realm().id());
            return;
        }

        log.warn("Realm management roles {} are missing for service account ID: {} of client : {}, going to assign them",
                missingRoleNames,
                keyCloakClientContext.getServiceAccountId(),
                keyCloakConfig.realm().client().id());

        keyCloakExchangeClient.assignClientRolesToUser(
                bearerToken,
                keyCloakConfig.realm().id(),
                keyCloakClientContext.getServiceAccountId(),
                keyCloakClientContext.getManagementClientUuid(),
                rolesAssignmentRequest
        );
        log.info("Total {} Realm management Roles assigned successfully to service account ID: {} of client : {}",
                rolesAssignmentRequest.size(),
                keyCloakClientContext.getServiceAccountId(),
                keyCloakConfig.realm().client().id());
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
