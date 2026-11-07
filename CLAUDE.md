# CLAUDE.md

Instructions for Claude Code (or any AI coding agent) working in this repository.

## What this is

A Spring Boot starter that integrates applications with Keycloak, published to Maven Central
as `com.intellermatrix:keycloak-spring-boot-starter`. See `README.md` for usage, the public
API, and the full configuration reference.

## Public API boundary

Only `com.intellermatrix.keycloak.auth` and `com.intellermatrix.keycloak.exception` are the
supported public API. Everything else lives under `com.intellermatrix.keycloak.internal` and
is not covered by semantic versioning — see `README.md`'s "Internal packages" section for the
full convention. Any new package under `internal.*` needs a `package-info.java` carrying
`@ApiStatus.Internal`, matching every existing `internal.*` package.

## Test discipline

This project follows TDD throughout: tests written first, watched to fail, then the
implementation. **This applies to integration tests, not just unit tests.** A change to how
the library actually talks to Keycloak needs a real, Testcontainers-backed `*IT.java` test
(same pattern as `AuthServerServiceIT`, `KeyCloakServiceIT`, `KeyCloakInitializerIT`) — a
mocked unit test alone does not prove that kind of change.

## The skill-sync rule (load-bearing, not optional)

Any change to `external-services.keycloak.*` property names, structure, or defaults, or to
the public `com.intellermatrix.keycloak.auth`/`com.intellermatrix.keycloak.exception` API,
**must** update the `keycloak-quickstart` Claude Code skill in the same commit/PR. The skill
generates configuration for consumers based on exactly this shape — letting it drift out of
sync silently breaks every project it's used in.

## Release behavior

Pushes to `master` only auto-publish a new version when `src/main`, `src/test`, or `pom.xml`
changed. A docs-only, `CLAUDE.md`-only, or skill-only PR producing no new version on merge is
expected, not a bug.
