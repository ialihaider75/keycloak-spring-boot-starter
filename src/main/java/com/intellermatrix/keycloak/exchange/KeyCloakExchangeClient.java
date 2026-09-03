package com.intellermatrix.keycloak.exchange;

import com.intellermatrix.keycloak.dto.AccessTokenResponse;
import com.intellermatrix.keycloak.dto.KeyCloakPingResponse;
import com.intellermatrix.keycloak.dto.client.ClientCreationRequest;
import com.intellermatrix.keycloak.dto.client.ClientDetailsResponse;
import com.intellermatrix.keycloak.dto.realm.CreateRealmRequest;
import com.intellermatrix.keycloak.dto.realm.GetRealmResponse;
import com.intellermatrix.keycloak.dto.role.RoleAssignmentRequest;
import com.intellermatrix.keycloak.dto.role.RoleCreationRequest;
import com.intellermatrix.keycloak.dto.role.RoleDetailsResponse;
import com.intellermatrix.keycloak.dto.serviceaccount.ServiceAccountUserResponse;
import com.intellermatrix.keycloak.dto.user.UserCreationRequest;
import com.intellermatrix.keycloak.dto.user.UserDetailsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

@HttpExchange
public interface KeyCloakExchangeClient {

    @GetExchange(value = "/health/live")
    KeyCloakPingResponse pingKeyCloak();

    @PostExchange(value = "/realms/{realm}/protocol/openid-connect/token",
            accept = "application/json",
            contentType = "application/x-www-form-urlencoded")
    AccessTokenResponse getAccessTokenForRealm(@PathVariable String realm,
                                               @RequestBody MultiValueMap<String, String> request);

    @GetExchange(value = "/admin/realms/{realmId}", accept = "application/json")
    GetRealmResponse getRealmDetails(@RequestHeader("Authorization") String bearerToken,
                                     @PathVariable String realmId);

    @PostExchange(value = "/admin/realms", accept = "application/json", contentType = "application/json")
    void createRealm(@RequestHeader("Authorization") String bearerToken,
                     @RequestBody CreateRealmRequest realmRequest);

    @GetExchange(value = "/admin/realms/{realmId}/clients/?clientId=clientId", accept = "application/json")
    List<ClientDetailsResponse> getClientDetails(@RequestHeader("Authorization") String bearerToken,
                                                 @PathVariable("realmId") String realmId,
                                                 @RequestParam("clientId") String clientId);

    @PostExchange(value = "/admin/realms/{realmId}/clients", accept = "application/json", contentType = "application/json")
    void createClient(@RequestHeader("Authorization") String bearerToken,
                      @PathVariable String realmId,
                      @RequestBody ClientCreationRequest clientRequest);

    @GetExchange(value = "/admin/realms/{realm}/clients/{clientUuid}/roles/{roleName}", accept = "application/json")
    RoleDetailsResponse getClientRoleDetailsByRoleName(@RequestHeader("Authorization") String bearerToken,
                                                       @PathVariable String realm,
                                                       @PathVariable String clientUuid,
                                                       @PathVariable String roleName);

    @PostExchange(value = "/admin/realms/{realm}/clients/{clientUuid}/roles", accept = "application/json", contentType = "application/json")
    void createClientRole(@RequestHeader("Authorization") String bearerToken,
                          @PathVariable String realm,
                          @PathVariable String clientUuid,
                          @RequestBody RoleCreationRequest roleCreationRequest);

    @GetExchange(value = "/admin/realms/{realm}/clients/{clientUuid}/service-account-user", accept = "application/json")
    ServiceAccountUserResponse getServiceAccountUserForClient(@RequestHeader("Authorization") String bearerToken,
                                                              @PathVariable String realm,
                                                              @PathVariable String clientUuid);

    @GetExchange(value = "/admin/realms/{realm}/users/{userId}/role-mappings/clients/{realmManagementClientId}",
            accept = "application/json")
    List<RoleDetailsResponse> getRealmManagementRolesForServiceAccount(@RequestHeader("Authorization") String bearerToken,
                                                                       @PathVariable String realm,
                                                                       @PathVariable String userId,
                                                                       @PathVariable String realmManagementClientId);

    @GetExchange(value = "/admin/realms/{realm}/clients/{clientUuid}/roles", accept = "application/json")
    List<RoleDetailsResponse> getClientRoles(@RequestHeader("Authorization") String bearerToken,
                                              @PathVariable String realm,
                                              @PathVariable String clientUuid);

    @PostExchange(value = "/admin/realms/{realm}/users/{userId}/role-mappings/clients/{realmManagementClientId}",
            accept = "application/json", contentType = "application/json")
    void assignClientRolesToUser(@RequestHeader("Authorization") String bearerToken,
                                  @PathVariable String realm,
                                  @PathVariable String userId,
                                  @PathVariable String realmManagementClientId,
                                  @RequestBody List<RoleAssignmentRequest> roles);

    @PostExchange(value = "/admin/realms/{realm}/users", accept = "application/json", contentType = "application/json")
    ResponseEntity<Void> createUser(@RequestHeader("Authorization") String bearerToken,
                              @PathVariable String realm,
                              @RequestBody UserCreationRequest userRequest);

    @PostExchange(value = "/admin/realms/{realm}/users/{userId}/role-mappings/clients/{clientUuid}",
            accept = "application/json", contentType = "application/json")
    void assignClientRoleToUser(@RequestHeader("Authorization") String bearerToken,
                                @PathVariable String realm,
                                @PathVariable String userId,
                                @PathVariable String clientUuid,
                                @RequestBody List<RoleAssignmentRequest> roles);

    @GetExchange(value = "/admin/realms/{realm}/users/{userId}/role-mappings/clients/{clientUuid}",
            accept = "application/json")
    List<RoleDetailsResponse> getClientRolesForUser(@RequestHeader("Authorization") String bearerToken,
                                                     @PathVariable String realm,
                                                     @PathVariable String userId,
                                                     @PathVariable String clientUuid);

    @GetExchange(value = "/admin/realms/{realm}/users", accept = "application/json")
    List<UserDetailsResponse> getUsersByUsername(@RequestHeader("Authorization") String bearerToken,
                                                 @PathVariable String realm,
                                                 @RequestParam("username") String username);

    @GetExchange(value = "/admin/realms/{realm}/users/{userId}", accept = "application/json")
    UserDetailsResponse getUserById(@RequestHeader("Authorization") String bearerToken,
                                     @PathVariable String realm,
                                     @PathVariable String userId);
}
