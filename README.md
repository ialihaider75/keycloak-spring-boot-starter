# Keycloak Spring Boot Starter

A reusable Spring Boot starter for integrating applications with Keycloak.

## Status

🚧 **Work in Progress**

This project is currently being extracted from an existing application into a reusable Spring Boot starter.

The API, configuration properties, and supported Keycloak features are subject to change until the first stable release.

## Goals

The starter is intended to:

- Integrate Keycloak into Spring Boot applications.
- Allow consumers to provide their own Keycloak configuration.
- Initialize the configured Keycloak realm, clients, roles, and required permissions at application startup.
- Fail application startup when required configuration is missing or Keycloak initialization fails.
- Provide a simple public API for authentication and user management.
- Keep Keycloak implementation details internal to the starter.

## Requirements

- Java 21
- Spring Boot 3.4.x
- A running Keycloak server

## Usage

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.intellermatrix</groupId>
    <artifactId>keycloak-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

The starter auto-configures itself (`KeycloakAutoConfiguration`) as soon as
`external-services.keycloak.connectivity.base-url` is present. No `@Import` or `@ComponentScan` is
needed in the consuming application.

### 2. Configure it

```yaml
external-services:
  keycloak:
    # Set to false to skip startup provisioning of realm/client/roles (default: true)
    auto-provision: true
    connectivity:
      base-url: http://localhost:8080          # required - Keycloak base URL
      management-url: http://localhost:9000    # required - Keycloak management (health) URL
      timeout-in-ms: 5000                      # required, > 0 - connect and read timeout
    admin:
      username: ${KEYCLOAK_ADMIN_USERNAME}     # required - master realm admin user
      password: ${KEYCLOAK_ADMIN_PASSWORD}     # required
      realm: master                            # required - realm holding the admin user
      client-id: admin-cli                     # required
    realm:
      id: my-app-realm                         # required - realm the starter manages
      display-name: My App Realm               # required
      management:
        client-id: realm-management            # required - Keycloak's built-in management client
      client:
        id: my-app-client                      # required
        name: My App Client                    # required
        secret: ${KEYCLOAK_CLIENT_SECRET}      # required - confidential client secret
        redirect-uris:                         # required, at least one entry
          - "http://localhost:8081/*"
      roles:                                   # required, at least one entry
        - name: CUSTOMER
          description: Customer role
        - name: PHARMACY
          description: Pharmacy role
```

All keys above except `auto-provision` are bound to `KeyCloakConfig`
(`@ConfigurationProperties("external-services.keycloak")`) and validated at startup — a missing or
blank value fails application startup.

**Master-realm admin credentials are required.** `external-services.keycloak.admin.*` must point at
a user in the `master` realm that can create realms and clients. Those credentials are used for
startup provisioning only (and, if you call them, `pingKeyCloak()` / `getAdminAccessToken()`);
all per-request signup, login, lookup and role operations run on the managed client's own
service-account token.

### 3. Startup behaviour

When `external-services.keycloak.auto-provision` is `true` (the default, including when the key is
absent), `KeyCloakInitializer` runs on `@PostConstruct` and, idempotently:

1. Pings Keycloak (startup fails fast if it is unreachable).
2. Creates the configured realm if it does not exist.
3. Creates the configured confidential client if it does not exist.
4. Grants the client's service account the realm-management roles it needs
   (`manage-users`, `view-users`, `query-users`, `view-clients`).
5. Creates every configured client-level role that does not exist.

Set `auto-provision: false` when the realm, client and roles are managed out of band (e.g. by
Terraform or an ops-provisioned realm import). The starter's runtime API still works; it simply does
not touch Keycloak at startup.

### 4. Public API

Inject `AuthServerService` — the supported entry point for consumers:

| Method | Purpose | Failure behaviour |
| --- | --- | --- |
| `void createUserInAuthServer(UserRegistrationRequest request)` | Signs a user up in the managed realm and assigns the requested client role. | `KeycloakIntegrationException` with `INVALID_REQUEST` (invalid request payload), `USER_ALREADY_EXISTS` (username/email taken), `COMMUNICATION_ERROR` (creation failed) |
| `KeyCloakAuthUserResponse authenticate(KeyCloakAuthUserRequest request)` | Password (direct access) grant login; returns the access token. | `KeycloakIntegrationException` with `INVALID_REQUEST` or `INVALID_CREDENTIALS` |
| `Optional<UserDetailsResponse> getUserDetailsByUsername(String username)` | Exact-match user lookup enriched with the user's first client role. | Empty `Optional` when the user does not exist or has no client role |

`KeyCloakService` is also a public bean for lower-level needs (`pingKeyCloak`, `createUser`,
`assignClientRoleToUser`, `getUserClientRoles`, `getUserByUsername`, `getUserById`, token
acquisition).

Every failure surfaces as `KeycloakIntegrationException`, which carries a
`KeycloakErrorReason` (`COMMUNICATION_ERROR`, `USER_NOT_FOUND`, `USER_ALREADY_EXISTS`,
`INVALID_CREDENTIALS`, `INVALID_REQUEST`, `INTERNAL_SERVER_ERROR`) and an `HttpStatus`, so consumers
can map it onto their own API responses without knowing anything about Keycloak.

## Development

Build the project using the Maven wrapper:

```bash
./mvnw clean package