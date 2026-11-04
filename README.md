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

### 5. Internal packages

This library has exactly two supported, stable packages. Everything else is an implementation
detail that ships in the same jar but is not part of the API contract.

| Package | Status | Use it in your application? |
| --- | --- | --- |
| `com.intellermatrix.keycloak.auth` | **Public API** | **Yes.** This is the intended integration surface: `AuthServerService`, `KeycloakGateway`, and the request/response records documented in [section 4](#4-public-api). |
| `com.intellermatrix.keycloak.exception` | **Public API** | **Yes.** Catch `KeycloakIntegrationException` and branch on `KeycloakErrorReason`. |
| `com.intellermatrix.keycloak.internal.*`<br/>(`internal.service`, `internal.client`, `internal.config`, `internal.constant`, `internal.enums`, `internal.exchange`, `internal.initializer`, `internal.preparator`, `internal.dto.*`) | **Internal — implementation detail** | **No.** Not covered by semantic versioning. May be renamed, restructured, or removed in any release — including a patch release — without notice. |

**If you are only consuming this starter as a dependency**, the rule is simple: only ever import
from `com.intellermatrix.keycloak.auth` and `com.intellermatrix.keycloak.exception`. Everything
under `com.intellermatrix.keycloak.internal` — `KeyCloakService`, `KeyCloakExchangeClient`, every
`internal.dto.*` record, every Keycloak admin-REST shape — is plumbing this library uses to
implement the public API above. It is intentionally not meant to be imported, extended, mocked,
or otherwise depended on from outside this library, even though the Java compiler will let you do
it. If something you need isn't exposed through `auth`, that's a gap in the public API worth
raising as an issue, not a reason to reach into `internal.*`.

This boundary is a *documented* convention, not a compiler-enforced one — plain Java packages
cannot express "public within this library, hidden from consumers" without either collapsing
everything into a single flat package or adopting the Java Platform Module System, both of which
were rejected here in favor of keeping the library's existing, readable sub-package organization.

As a second, IDE-visible layer of the same convention, every package under `internal.*` is
annotated `@ApiStatus.Internal` (via a `package-info.java` in each one). IntelliJ recognizes this
annotation and flags a visible warning when code outside this library references a class in an
`@ApiStatus.Internal` package — at the point of use, not only for readers who happen to check this
README first. This is not a compiler error: `javac`, Maven, and non-IntelliJ tooling do not enforce
it, so nothing stops it from compiling if the warning is ignored.

## Development

### Building and testing

Build the project using the Maven wrapper:

```bash
./mvnw clean package
```

Run the unit test suite only (fast, no external services required):

```bash
./mvnw test
```

Run the full test suite, including integration tests against a real Keycloak instance started via
Testcontainers (`maven-failsafe-plugin`, every `**/*IT.java` class — requires a running Docker
daemon):

```bash
./mvnw verify
```

### Contributing: where new code belongs

Before adding or moving a class, decide which side of the public/internal boundary (see
"[Internal packages](#5-internal-packages)" above) it belongs on. This decision is the single
most important thing to get right in a PR against this repo — getting it wrong either breaks a
consumer's code on the next release, or leaks Keycloak implementation details into the one place
this library promises stability.

- **It's Keycloak integration plumbing** — an admin-REST DTO, a new `KeyCloakExchangeClient`
  method, a config/property class, a helper only ever used inside the starter: put it under the
  matching `com.intellermatrix.keycloak.internal.*` sub-package (e.g. a new DTO goes in
  `internal.dto.<area>`, mirroring the sub-package it models; a new low-level service method goes
  on `internal.service.KeyCloakService`). If the change introduces a brand-new sub-package under
  `internal`, add a `package-info.java` to it — copy the template from any existing `internal.*`
  package (the `@ApiStatus.Internal` annotation plus the Javadoc pointing back to this README
  section). Skipping this step is easy to miss and easy to catch: `./mvnw -q compile` still
  succeeds either way, so review it by eye.
- **It's new consumer-facing behaviour** — a new method on `AuthServerService`, a new request or
  response record consumers are meant to construct or read, a new `KeycloakErrorReason`: put it in
  `com.intellermatrix.keycloak.auth` or `com.intellermatrix.keycloak.exception`. Treat this as a
  deliberate public-API change. Once this library reaches a stable release, additions here are
  easy (backwards compatible); removals or signature changes are not.
- **`auth` never imports from `internal.*`**, with one narrow, explicit exception: it may depend
  on `KeycloakGateway` (declared inside `auth` itself) as the single seam between the two sides.
  `KeycloakGateway`'s own signature only uses types already declared in `auth`, so implementing it
  (`internal.service.KeyCloakService` does) never forces an `internal.*` import back into `auth`.
  If a class in `auth` needs data that currently only exists as an `internal.dto.*` shape, do the
  conversion on the `internal.*` side (inside `KeyCloakService` or the gateway implementation) and
  hand `auth` its own, already-public-shaped record — do not import the internal DTO into `auth`
  to save a few lines of mapping code. You can check the boundary still holds at any time with:

  ```bash
  grep -rn "^import com.intellermatrix.keycloak.internal" src/main/java/com/intellermatrix/keycloak/auth/
  ```

  A clean result (**no output at all**) is the only passing result — not even a single import
  line is expected here.
- **Tests mirror the production package they test.** A class in `internal.service` is tested from
  `src/test/java/com/intellermatrix/keycloak/internal/service`, exactly the same sub-package path
  as the class under test — the existing test tree already follows this, and `KeyCloakServiceTest`
  constructing the `internal.preparator` implementations directly (rather than mocking them) is
  the reason those three preparator classes stay `public` instead of package-private: a test that
  needs to reach a class by name from another package is a real reason to keep that class public,
  the same as any other caller would be.