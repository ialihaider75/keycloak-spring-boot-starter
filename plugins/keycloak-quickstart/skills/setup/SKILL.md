---
name: setup
description: Use when a user wants to add Keycloak authentication to a Spring Boot project, or integrate the com.intellermatrix:keycloak-spring-boot-starter library, or asks for help setting up Keycloak with a Spring Boot app.
allowed-tools: Read Write Edit AskUserQuestion
---

Integrate `com.intellermatrix:keycloak-spring-boot-starter` into the current Spring Boot
project: add the dependency, collect the project-specific configuration, write the
configuration files, and set up a local Keycloak+Postgres dev environment. **Maven only** —
if the project is a Gradle project (no `pom.xml`, a `build.gradle`/`build.gradle.kts`
present instead), stop and tell the user Gradle isn't supported yet.

The current published version of the library:

!`curl -s https://repo1.maven.org/maven2/com/intellermatrix/keycloak-spring-boot-starter/maven-metadata.xml`

Read the `<release>` value from that XML — that is the exact version to use. Never hardcode
a version; this library auto-publishes a new one on every real code change, so a fixed
version in this skill would go stale.

## Step 1: Check compatibility, then add the dependency

Read the project's `pom.xml`. This library requires **Java 21** and **Spring Boot 3.4.x**. Check
the `<java.version>` (or `<maven.compiler.release>`) property and the `spring-boot-starter-parent`
version:
- Java below 21, or a Spring Boot version outside the `3.4.x` line: stop here and tell the user
  plainly which requirement isn't met, instead of continuing — a dependency added to an
  incompatible project will not compile or will fail at startup, and finishing the rest of this
  skill would make that look like success.
- Otherwise, add a `<dependency>` for `com.intellermatrix:keycloak-spring-boot-starter` at the
  version resolved above, inside the existing `<dependencies>` block. Don't touch any other
  dependency.

## Step 2: Ask for the project-specific values, one at a time

Ask these five things, one question at a time, waiting for each answer before asking the
next. Do not ask about anything else — everything else in Step 4 gets a fixed default.

1. Realm `id` and `display-name` (e.g. `demo-realm` / `Demo Realm`).
2. Client `id` and `name` (e.g. `demo-client` / `Demo Client`).
3. Client `secret` — any string is fine for local dev.
4. Redirect URI(s) — at least one (e.g. `http://localhost:8081/*`), matching the port this
   project will actually run on (see Step 3).
5. Role(s) to provision — one or more, each a `name` and a short `description` (e.g.
   `ADMIN` / `Administrator role`).

## Step 3: Resolve the port collision

Keycloak's local dev container always uses the standard ports `8080` (HTTP) and `9000`
(management) — never move those.

Check the project's existing configuration (`application.properties` or
`application.yaml`/`.yml`, whichever exists) for a `server.port` value:
- If it's **unset**, or already set to `8080`: set it to `8081` in the generated
  configuration, and tell the user explicitly that you moved it and why (it would otherwise
  collide with Keycloak's own port).
- If it's already set to **anything else**: leave it exactly as it is. Do not touch it.

If the resulting port doesn't match the redirect URI's port from Step 2, don't silently change
what the user gave you — tell them explicitly about the mismatch and ask them to confirm the
corrected redirect URI before writing it. A redirect URI sometimes points at something other than
this Spring app (a separate frontend, a deployed callback), so the app's own port is not always
the right answer.

## Step 4: Write the application configuration

Detect whether the project already has `application.properties` or
`application.yaml`/`.yml` under `src/main/resources/`. If one exists, use that same format.
If neither exists, create `application.yaml` (YAML, matching this library's own README
examples). (Spring Initializr's default project has an empty `application.properties` — that
is the common case, not the exception.)

**Merge the new keys in — never overwrite or remove anything already in the file.** A
project with existing keys (a datasource, `spring.application.name`, etc.) must keep every
one of them, byte-for-byte, after this step.

**If Step 3 determined the app's port needs to move to `8081`, write that too** — it's easy to
treat the block below as the whole file content and forget the port isn't in it.

YAML:

```yaml
server:
  port: 8081 # only if Step 3 determined a move was needed

external-services:
  keycloak:
    auto-provision: true
    connectivity:
      base-url: http://localhost:8080
      management-url: http://localhost:9000
      timeout-in-ms: 5000
    admin:
      username: admin
      password: admin
      realm: master
      client-id: admin-cli
    realm:
      id: <from Step 2>
      display-name: <from Step 2>
      management:
        client-id: realm-management
      client:
        id: <from Step 2>
        name: <from Step 2>
        secret: <from Step 2>
        redirect-uris:
          - <from Step 2>
      roles:
        - name: <from Step 2>
          description: <from Step 2>
        # one entry per role collected in Step 2
```

The same tree in `.properties` form — note the indexed syntax for `redirect-uris` and `roles`,
both of which are lists; a plain comma-separated scalar does not bind the same way and fails
Spring's validation at startup:

```properties
server.port=8081

external-services.keycloak.auto-provision=true
external-services.keycloak.connectivity.base-url=http://localhost:8080
external-services.keycloak.connectivity.management-url=http://localhost:9000
external-services.keycloak.connectivity.timeout-in-ms=5000
external-services.keycloak.admin.username=admin
external-services.keycloak.admin.password=admin
external-services.keycloak.admin.realm=master
external-services.keycloak.admin.client-id=admin-cli
external-services.keycloak.realm.id=<from Step 2>
external-services.keycloak.realm.display-name=<from Step 2>
external-services.keycloak.realm.management.client-id=realm-management
external-services.keycloak.realm.client.id=<from Step 2>
external-services.keycloak.realm.client.name=<from Step 2>
external-services.keycloak.realm.client.secret=<from Step 2>
external-services.keycloak.realm.client.redirect-uris[0]=<from Step 2>
external-services.keycloak.realm.roles[0].name=<from Step 2>
external-services.keycloak.realm.roles[0].description=<from Step 2>
# roles[1], roles[2], ... — one indexed pair per role collected in Step 2
```

`admin.*`, `connectivity.timeout-in-ms`, and `realm.management.client-id` are always exactly
these values — never ask about them. `realm.management.client-id` is always
`realm-management`: it's Keycloak's own fixed built-in client name, not something a consumer
chooses, and it's a required field — omitting it breaks the whole configuration at Spring
startup.

## Step 5: Write the Docker Compose file

Check whether a Compose file already exists at the project root — any of `compose.yaml`,
`compose.yml`, `docker-compose.yaml`, or `docker-compose.yml` (Compose checks all of these;
missing one means the skill might create a file that Compose silently ignores because it
prefers another one that's already there).

**If none exist**, create `docker-compose.yml` with this exact shape:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: keycloak
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U keycloak"]
      interval: 5s
      timeout: 5s
      retries: 10

  keycloak:
    image: quay.io/keycloak/keycloak:25.0.6
    command: start-dev
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: keycloak
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_HEALTH_ENABLED: "true"
      KC_METRICS_ENABLED: "true"
    ports:
      - "8080:8080"
      - "9000:9000"
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres_data:
```

**If one already exists, do not touch it.** Write a new file, `docker-compose.keycloak.yml`,
instead, with services and a volume renamed so they can never collide with whatever the
existing file already defines (a plain `postgres`/`keycloak` name is common enough in a
project's own compose file that reusing it here would merge into, and corrupt, the user's own
service when both files are loaded together):

```yaml
services:
  keycloak-postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: keycloak
      POSTGRES_USER: keycloak
      POSTGRES_PASSWORD: keycloak
    volumes:
      - keycloak_postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U keycloak"]
      interval: 5s
      timeout: 5s
      retries: 10

  keycloak:
    image: quay.io/keycloak/keycloak:25.0.6
    command: start-dev
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://keycloak-postgres:5432/keycloak
      KC_DB_USERNAME: keycloak
      KC_DB_PASSWORD: keycloak
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_HEALTH_ENABLED: "true"
      KC_METRICS_ENABLED: "true"
    ports:
      - "8080:8080"
      - "9000:9000"
    depends_on:
      keycloak-postgres:
        condition: service_healthy

volumes:
  keycloak_postgres_data:
```

Tell the user to bring this up as its own isolated project — `docker compose -f
docker-compose.yml -f docker-compose.keycloak.yml up -d` *merges* the two files instead of
running them side by side, which is exactly what the renamed services above are there to make
harmless either way, but `-p` is still the correct way to run two unrelated stacks together:

```bash
docker compose -f docker-compose.keycloak.yml -p keycloak up -d
```

## Step 6: Summarize

Tell the user, concisely:
- Which files were created or changed.
- The exact command to bring Keycloak up (`docker compose up -d`, or the `-p keycloak` command
  from Step 5 if a sibling file was used).
- That writing a controller over `AuthServerService` (the library's public entry point) is
  the next step, and it's on them — this skill stops once the library is wired and
  configured; it does not generate business logic.
