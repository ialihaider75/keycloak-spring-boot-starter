---
name: setup
description: Use when a user wants to add Keycloak authentication to a Spring Boot project, or integrate the com.intellermatrix:keycloak-spring-boot-starter library, or asks for help setting up Keycloak with a Spring Boot app.
allowed-tools: Read Write Edit Bash AskUserQuestion
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

## Step 1: Add the dependency

Read the project's `pom.xml`. Add a `<dependency>` for
`com.intellermatrix:keycloak-spring-boot-starter` at the version resolved above, inside the
existing `<dependencies>` block. Don't touch any other dependency.

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

Use whatever the resulting port is as the redirect URI's port if the user's answer in Step 2
didn't already match it.

## Step 4: Write the application configuration

Detect whether the project already has `application.properties` or
`application.yaml`/`.yml` under `src/main/resources/`. If one exists, use that same format.
If neither exists, create `application.yaml` (YAML, matching this library's own README
examples).

**Merge the new keys in — never overwrite or remove anything already in the file.** A
project with existing keys (a datasource, `spring.application.name`, etc.) must keep every
one of them, byte-for-byte, after this step.

Add these keys (YAML shown; translate to dotted `.properties` form if that's the project's
existing format):

```yaml
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

`admin.*`, `connectivity.timeout-in-ms`, and `realm.management.client-id` are always exactly
these values — never ask about them. `realm.management.client-id` is always
`realm-management`: it's Keycloak's own fixed built-in client name, not something a consumer
chooses, and it's a required field — omitting it breaks the whole configuration at Spring
startup.

## Step 5: Write the Docker Compose file

Check whether `docker-compose.yml` already exists at the project root.

**If it does not exist**, create `docker-compose.yml` with this exact shape:

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

**If `docker-compose.yml` already exists, do not touch it.** Write the exact same content
above to a new file, `docker-compose.keycloak.yml`, instead. Tell the user they can run both
together with:

```bash
docker compose -f docker-compose.yml -f docker-compose.keycloak.yml up -d
```

## Step 6: Summarize

Tell the user, concisely:
- Which files were created or changed.
- The exact command to bring Keycloak up (`docker compose up -d`, or the two-file command
  from Step 5 if a sibling file was used).
- That writing a controller over `AuthServerService` (the library's public entry point) is
  the next step, and it's on them — this skill stops once the library is wired and
  configured; it does not generate business logic.
